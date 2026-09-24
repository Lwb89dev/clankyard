package dev.clankyard.ai.provider.http

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatMessage
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.ChatRole
import dev.clankyard.ai.provider.ContentPart
import dev.clankyard.ai.provider.ModelInfo
import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.RequestId
import java.io.IOException
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource

/**
 * Shared Chat Completions SSE adapter (KD-17) for first-party OpenAI, xAI,
 * and OpenAI-compatible hosts. Wire field is `max_tokens`. No Responses API.
 */
class OpenAICompletionsAdapter(
    client: OkHttpClient,
    private val root: HttpUrl,
    private val calls: InFlightCalls = InFlightCalls(),
    private val streamWallClock: Duration = ProviderHttp.STREAM_WALL_CLOCK,
) {
    private val listClient = ProviderHttp.listClient(client)
    private val streamClient = ProviderHttp.streamingClient(client)

    suspend fun listModels(credential: Credential): List<ModelInfo> {
        val apiKey = requireApiKey(credential)
        val request = Request.Builder()
            .url(root.modelsUrl())
            .header("Authorization", bearer(apiKey))
            .header("Accept", "application/json")
            .get()
            .build()
        listClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException(httpErrorEvent(response, apiKey).message)
            return parseModels(
                response.body?.readUtf8Limited(ResponseLimits.JSON_BODY_BYTES).orEmpty(),
            )
        }
    }

    fun chat(request: ChatRequest, credential: Credential): Flow<ChatEvent> {
        val apiKey = requireApiKey(credential)
        val httpRequest = completionsRequest(request, apiKey)
        return flow { collectChat(request.requestId, httpRequest, apiKey) }
    }

    suspend fun cancel(requestId: RequestId) {
        calls.cancel(requestId)
    }

    private suspend fun FlowCollector<ChatEvent>.collectChat(
        requestId: RequestId,
        httpRequest: Request,
        apiKey: String,
    ) {
        val client = if (httpRequest.header("Accept") == SSE_ACCEPT) streamClient else listClient
        val call = client.newCall(httpRequest)
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
        if (response.header("Content-Type").orEmpty().contains("text/event-stream")) {
            parseCompletionsSse(source)
            return
        }
        parseCompletionJson(source.readUtf8Limited(ResponseLimits.JSON_BODY_BYTES))
    }

    private suspend fun FlowCollector<ChatEvent>.parseCompletionsSse(source: BufferedSource) {
        val tools = LinkedHashMap<Int, ToolCallAcc>()
        var completed = false
        source.consumeSse { _, data ->
            if (completed) return@consumeSse
            if (data.trim() == "[DONE]") {
                flushTools(tools)
                emit(ChatEvent.Completed)
                completed = true
                return@consumeSse
            }
            handleChunk(data, tools)
        }
        if (!completed) {
            flushTools(tools)
            emit(ChatEvent.Completed)
        }
    }

    private suspend fun FlowCollector<ChatEvent>.parseCompletionJson(body: String) {
        val tools = LinkedHashMap<Int, ToolCallAcc>()
        handleChunk(body, tools, fromMessage = true)
        flushTools(tools)
        emit(ChatEvent.Completed)
    }

    private suspend fun FlowCollector<ChatEvent>.handleChunk(
        data: String,
        tools: MutableMap<Int, ToolCallAcc>,
        fromMessage: Boolean = false,
    ) {
        val obj = parseObject(data) ?: return
        emitUsage(obj["usage"] as? JsonObject)
        val choices = obj["choices"] as? JsonArray ?: return
        for (choice in choices) {
            val c = choice as? JsonObject ?: continue
            val delta = c["delta"] as? JsonObject
            val message = c["message"] as? JsonObject
            val payload = when {
                fromMessage -> message
                else -> delta ?: message
            } ?: continue
            emitText(payload)
            applyToolDeltas(payload["tool_calls"] as? JsonArray, tools)
        }
    }

    private suspend fun FlowCollector<ChatEvent>.emitText(payload: JsonObject) {
        val text = payload.string("content").orEmpty()
        if (text.isNotEmpty()) emit(ChatEvent.Delta(text))
    }

    private suspend fun FlowCollector<ChatEvent>.emitUsage(usage: JsonObject?) {
        if (usage == null) return
        emit(
            ChatEvent.Usage(
                inputTokens = usage.int("prompt_tokens"),
                outputTokens = usage.int("completion_tokens"),
            ),
        )
    }

    private suspend fun FlowCollector<ChatEvent>.flushTools(tools: MutableMap<Int, ToolCallAcc>) {
        if (tools.isEmpty()) return
        for (acc in tools.values) {
            if (acc.name.isEmpty()) continue
            emit(ChatEvent.ToolCall(acc.id, acc.name, acc.arguments.toString()))
        }
        tools.clear()
    }

    private fun completionsRequest(request: ChatRequest, apiKey: String): Request {
        val body = request.toCompletionsBody().toString()
        val builder = Request.Builder()
            .url(root.chatCompletionsUrl())
            .header("Authorization", bearer(apiKey))
            .header("Content-Type", JSON_MEDIA.toString())
            .post(body.toRequestBody(JSON_MEDIA))
        if (request.stream) builder.header("Accept", SSE_ACCEPT)
        return builder.build()
    }

    private fun parseModels(body: String): List<ModelInfo> {
        val rootObj = parseObject(body) ?: return emptyList()
        val data = rootObj["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            ModelInfo(
                id = id,
                displayName = obj.string("owned_by") ?: id,
                contextWindowTokens = obj.int("context_window"),
                supportsTools = true,
                supportsStreaming = true,
            )
        }
    }

    private companion object {
        const val SSE_ACCEPT = "text/event-stream"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

private fun bearer(apiKey: String) = "Bearer $apiKey"

private class ToolCallAcc(
    var id: String = "",
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
)

private fun applyToolDeltas(toolCalls: JsonArray?, tools: MutableMap<Int, ToolCallAcc>) {
    if (toolCalls == null) return
    for ((fallback, el) in toolCalls.withIndex()) {
        val obj = el as? JsonObject ?: continue
        val index = obj.int("index") ?: fallback
        val acc = tools.getOrPut(index) { ToolCallAcc() }
        obj.string("id")?.let { acc.id = it }
        val fn = obj["function"] as? JsonObject
        fn?.string("name")?.let { acc.name += it }
        fn?.string("arguments")?.let { acc.arguments.append(it) }
    }
}

private fun ChatRequest.toCompletionsBody(): JsonObject = buildJsonObject {
    put("model", model)
    put("stream", stream)
    putJsonArray("messages") { for (msg in messages) add(msg.toCompletionsJson()) }
    maxTokens?.let { put("max_tokens", it) }
    temperature?.let { put("temperature", it.toDouble()) }
    if (tools.isEmpty()) return@buildJsonObject
    putJsonArray("tools") { for (spec in tools) add(spec.toCompletionsTool()) }
}

private fun ChatMessage.toCompletionsJson(): JsonObject = buildJsonObject {
    put("role", role.wireName())
    if (role == ChatRole.Tool) {
        val result = parts.filterIsInstance<ContentPart.ToolResult>().first()
        put("tool_call_id", result.toolCallId)
        put("content", result.content)
        return@buildJsonObject
    }
    val uses = parts.filterIsInstance<ContentPart.ToolUse>()
    if (uses.isNotEmpty()) {
        putJsonArray("tool_calls") { for (use in uses) add(use.toToolCallJson()) }
        val text = textParts()
        if (text.isNotEmpty()) put("content", text)
        return@buildJsonObject
    }
    put("content", textParts())
}

private fun ChatMessage.textParts(): String =
    parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }

private fun ContentPart.ToolUse.toToolCallJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("type", "function")
    putJsonObject("function") {
        put("name", name)
        put("arguments", argumentsJson)
    }
}

private fun ToolSpec.toCompletionsTool(): JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", name)
        put("description", description)
        put("parameters", parseJsonSchema(parametersJsonSchema))
    }
}

private fun ChatRole.wireName(): String = when (this) {
    ChatRole.System -> "system"
    ChatRole.User -> "user"
    ChatRole.Assistant -> "assistant"
    ChatRole.Tool -> "tool"
}
