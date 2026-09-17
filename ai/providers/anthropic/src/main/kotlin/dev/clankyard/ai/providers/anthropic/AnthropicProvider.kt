package dev.clankyard.ai.providers.anthropic

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatMessage
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.ChatRole
import dev.clankyard.ai.provider.ContentPart
import dev.clankyard.ai.provider.LlmProvider
import dev.clankyard.ai.provider.ModelInfo
import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.ai.provider.http.InFlightCalls
import dev.clankyard.ai.provider.http.ProviderHttp
import dev.clankyard.ai.provider.http.await
import dev.clankyard.ai.provider.http.collectHttpCall
import dev.clankyard.ai.provider.http.consumeSse
import dev.clankyard.ai.provider.http.httpErrorEvent
import dev.clankyard.ai.provider.http.int
import dev.clankyard.ai.provider.http.messagesUrl
import dev.clankyard.ai.provider.http.modelsUrl
import dev.clankyard.ai.provider.http.normalizeCompletionsRoot
import dev.clankyard.ai.provider.http.parseJsonSchema
import dev.clankyard.ai.provider.http.parseObject
import dev.clankyard.ai.provider.http.requireApiKey
import dev.clankyard.ai.provider.http.string
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import java.io.IOException
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource

/**
 * Anthropic Messages API (`POST /v1/messages` SSE).
 * Headers: `x-api-key` + `anthropic-version: 2023-06-01`.
 * **No OAuth**, no custom URL schemes, no WebView login.
 * Does not retain [Credential]. Not the Completions adapter.
 */
class AnthropicProvider internal constructor(
    client: OkHttpClient,
    private val root: HttpUrl,
    private val streamWallClock: Duration = ProviderHttp.STREAM_WALL_CLOCK,
) : LlmProvider {
    constructor(
        client: OkHttpClient,
        baseUrl: String = DEFAULT_BASE_URL,
    ) : this(client, normalizeCompletionsRoot(baseUrl))

    override val id: ProviderId = ProviderId("anthropic")
    override val displayName: String = "Anthropic"
    override val authenticationKind: AuthenticationKind = AuthenticationKind.ApiKey

    private val listClient = ProviderHttp.listClient(client)
    private val streamClient = ProviderHttp.streamingClient(client)
    private val calls = InFlightCalls()

    override suspend fun listModels(credential: Credential): List<ModelInfo> {
        val apiKey = requireApiKey(credential)
        val request = authedGet(root.modelsUrl(), apiKey)
        listClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException(httpErrorEvent(response, apiKey).message)
            return parseModels(response.body?.string().orEmpty())
        }
    }

    override fun chat(request: ChatRequest, credential: Credential): Flow<ChatEvent> {
        val apiKey = requireApiKey(credential)
        val httpRequest = messagesRequest(request, apiKey)
        return flow { collectChat(request.requestId, httpRequest, apiKey) }
    }

    override suspend fun cancel(requestId: RequestId) {
        calls.cancel(requestId)
    }

    private suspend fun FlowCollector<ChatEvent>.collectChat(
        requestId: RequestId,
        httpRequest: Request,
        apiKey: String,
    ) {
        val call = streamClient.newCall(httpRequest)
        calls.register(requestId, call)
        try {
            collectHttpCall(call, streamWallClock) {
                call.await().use { response -> emitFromResponse(response, apiKey) }
            }
        } finally {
            calls.remove(requestId)
        }
    }

    private suspend fun FlowCollector<ChatEvent>.emitFromResponse(
        response: Response,
        apiKey: String,
    ) {
        if (!response.isSuccessful) {
            emit(httpErrorEvent(response, apiKey))
            return
        }
        val source = response.body?.source()
        if (source == null) {
            emit(ChatEvent.Error("empty body", retryable = true))
            return
        }
        parseMessagesSse(source)
    }

    private suspend fun FlowCollector<ChatEvent>.parseMessagesSse(source: BufferedSource) {
        val state = MessageStreamState()
        source.consumeSse { event, data ->
            if (state.completed) return@consumeSse
            val obj = parseObject(data) ?: return@consumeSse
            handleSse(event, obj, state)
        }
        if (!state.completed) {
            emitUsage(state.inputTokens, state.outputTokens)
            emit(ChatEvent.Completed)
        }
    }

    private suspend fun FlowCollector<ChatEvent>.handleSse(
        event: String,
        obj: JsonObject,
        state: MessageStreamState,
    ) {
        val type = event.ifEmpty { obj.string("type").orEmpty() }
        when (type) {
            "message_start" ->
                state.inputTokens = usageInt(obj["message"] as? JsonObject, "input_tokens")
            "content_block_start" -> startBlock(obj, state.tools)
            "content_block_delta" -> deltaBlock(obj, state.tools)
            "content_block_stop" -> flushTool(obj, state.tools)
            "message_delta" ->
                state.outputTokens = usageInt(obj, "output_tokens") ?: state.outputTokens
            "message_stop" -> {
                emitUsage(state.inputTokens, state.outputTokens)
                emit(ChatEvent.Completed)
                state.completed = true
            }
        }
    }

    private suspend fun FlowCollector<ChatEvent>.startBlock(
        obj: JsonObject,
        tools: MutableMap<Int, ToolUseAcc>,
    ) {
        val index = obj.int("index") ?: return
        val block = obj["content_block"] as? JsonObject ?: return
        if (block.string("type") != "tool_use") return
        tools[index] = ToolUseAcc(
            id = block.string("id").orEmpty(),
            name = block.string("name").orEmpty(),
        )
    }

    private suspend fun FlowCollector<ChatEvent>.deltaBlock(
        obj: JsonObject,
        tools: MutableMap<Int, ToolUseAcc>,
    ) {
        val delta = obj["delta"] as? JsonObject ?: return
        when (delta.string("type")) {
            "text_delta" -> {
                val text = delta.string("text").orEmpty()
                if (text.isNotEmpty()) emit(ChatEvent.Delta(text))
            }
            "input_json_delta" -> {
                val index = obj.int("index") ?: return
                tools[index]?.arguments?.append(delta.string("partial_json").orEmpty())
            }
        }
    }

    private suspend fun FlowCollector<ChatEvent>.flushTool(
        obj: JsonObject,
        tools: MutableMap<Int, ToolUseAcc>,
    ) {
        val index = obj.int("index") ?: return
        val acc = tools.remove(index) ?: return
        if (acc.name.isEmpty()) return
        emit(ChatEvent.ToolCall(acc.id, acc.name, acc.arguments.toString().ifEmpty { "{}" }))
    }

    private suspend fun FlowCollector<ChatEvent>.emitUsage(input: Int?, output: Int?) {
        if (input == null && output == null) return
        emit(ChatEvent.Usage(input, output))
    }

    private fun messagesRequest(request: ChatRequest, apiKey: String): Request {
        val body = request.toAnthropicBody().toString()
        return Request.Builder()
            .url(root.messagesUrl())
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", JSON_MEDIA.toString())
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
    }

    private fun authedGet(url: HttpUrl, apiKey: String): Request = Request.Builder()
        .url(url)
        .header("x-api-key", apiKey)
        .header("anthropic-version", ANTHROPIC_VERSION)
        .header("Accept", "application/json")
        .get()
        .build()

    private fun parseModels(body: String): List<ModelInfo> {
        val rootObj = parseObject(body) ?: return emptyList()
        val data = rootObj["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            ModelInfo(
                id = id,
                displayName = obj.string("display_name") ?: id,
                contextWindowTokens = null,
                supportsTools = true,
                supportsStreaming = true,
            )
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

private class MessageStreamState {
    val tools = LinkedHashMap<Int, ToolUseAcc>()
    var inputTokens: Int? = null
    var outputTokens: Int? = null
    var completed = false
}

private class ToolUseAcc(
    val id: String,
    val name: String,
    val arguments: StringBuilder = StringBuilder(),
)

private fun usageInt(obj: JsonObject?, key: String): Int? {
    val usage = obj?.get("usage") as? JsonObject ?: return null
    return usage.int(key)
}

private fun ChatRequest.toAnthropicBody(): JsonObject = buildJsonObject {
    put("model", model)
    put("max_tokens", maxTokens ?: 4096)
    put("stream", stream)
    temperature?.let { put("temperature", it.toDouble()) }
    val systemText = messages.filter { it.role == ChatRole.System }.joinToString("\n") { it.text() }
    if (systemText.isNotEmpty()) put("system", systemText)
    putJsonArray("messages") {
        for (msg in messages) {
            if (msg.role == ChatRole.System) continue
            add(msg.toAnthropicMessage())
        }
    }
    if (tools.isEmpty()) return@buildJsonObject
    putJsonArray("tools") { for (spec in tools) add(spec.toAnthropicTool()) }
}

private fun ChatMessage.toAnthropicMessage(): JsonObject = buildJsonObject {
    put("role", if (role == ChatRole.Assistant) "assistant" else "user")
    put("content", contentBlocks())
}

private fun ChatMessage.contentBlocks(): JsonArray = buildJsonArray {
    when (role) {
        ChatRole.Tool -> for (part in parts.filterIsInstance<ContentPart.ToolResult>()) {
            add(
                buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", part.toolCallId)
                    put("content", part.content)
                    put("is_error", part.isError)
                },
            )
        }
        ChatRole.Assistant -> for (part in parts) add(part.toAssistantBlock())
        else -> add(
            buildJsonObject {
                put("type", "text")
                put("text", text())
            },
        )
    }
}

private fun ContentPart.toAssistantBlock(): JsonObject = when (this) {
    is ContentPart.Text -> buildJsonObject {
        put("type", "text")
        put("text", text)
    }
    is ContentPart.ToolUse -> buildJsonObject {
        put("type", "tool_use")
        put("id", id)
        put("name", name)
        put("input", parseJsonSchema(argumentsJson))
    }
    is ContentPart.ToolResult -> buildJsonObject {
        put("type", "text")
        put("text", content)
    }
}

private fun ChatMessage.text(): String =
    parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }

private fun ToolSpec.toAnthropicTool(): JsonObject = buildJsonObject {
    put("name", name)
    put("description", description)
    put("input_schema", parseJsonSchema(parametersJsonSchema))
}
