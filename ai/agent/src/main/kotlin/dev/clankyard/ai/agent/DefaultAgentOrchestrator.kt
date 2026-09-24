package dev.clankyard.ai.agent

import dev.clankyard.ai.context.AgentMode
import dev.clankyard.ai.context.ContextEngine
import dev.clankyard.ai.context.ContextPacket
import dev.clankyard.ai.patch.ProposedEdit
import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatMessage
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.ChatRole
import dev.clankyard.ai.provider.ContentPart
import dev.clankyard.ai.provider.LlmProvider
import dev.clankyard.ai.secret.SecretFilter
import dev.clankyard.ai.tools.ToolContext
import dev.clankyard.ai.tools.ToolRegistry
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.RequestId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class DefaultAgentOrchestrator(
    private val provider: LlmProvider,
    private val contextEngine: ContextEngine,
    private val tools: ToolRegistry,
    private val secrets: SecretFilter,
    private val profile: AgentProfile = ClankyardCodingAgent.profile,
    private val maxToolRounds: Int = 8,
) : AgentOrchestrator {
    private val json = Json { ignoreUnknownKeys = true }

    override fun run(request: AgentTurnRequest): Flow<AgentEvent> = flow {
        val packet = contextEngine.assemble(request.context)
        emit(AgentEvent.ContextReady(packet))
        if (packet.ambiguous.isNotEmpty()) {
            emit(AgentEvent.Error("ambiguous @mention; pick a file", retryable = false))
            return@flow
        }
        val credential = request.credential
        val messages = ArrayList<ChatMessage>()
        messages += systemMessage(request.mode, packet)
        messages += ChatMessage(ChatRole.User, listOf(ContentPart.Text(request.context.prompt)))
        val edits = ArrayList<ProposedEdit>()
        if (!driveRounds(request, credential, messages, edits)) return@flow
        val engine = request.patchEngine
        if (request.mode == AgentMode.Edit && edits.isNotEmpty() && engine != null) {
            emit(AgentEvent.PatchProposed(engine.validateAndDiff(edits)))
        }
        emit(AgentEvent.Completed)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.driveRounds(
        request: AgentTurnRequest,
        credential: Credential,
        messages: MutableList<ChatMessage>,
        edits: MutableList<ProposedEdit>,
    ): Boolean {
        var round = 0
        while (round <= maxToolRounds) {
            val outcome = collectModel(request, messages, credential)
            for (event in outcome.events) emit(event)
            if (outcome.failed) return false
            if (outcome.calls.isEmpty()) return true
            if (round == maxToolRounds) {
                emit(AgentEvent.Error("tool round limit", retryable = false))
                return true
            }
            val parts = runTools(request, outcome.calls, edits)
            messages += assistantToolUses(outcome.calls)
            messages += ChatMessage(ChatRole.Tool, parts)
            round++
        }
        return true
    }

    private suspend fun collectModel(
        request: AgentTurnRequest,
        messages: List<ChatMessage>,
        credential: Credential,
    ): ModelRound {
        val calls = ArrayList<ChatEvent.ToolCall>()
        val events = ArrayList<AgentEvent>()
        var failed = false
        provider.chat(chatRequest(request, messages), credential).collect { event ->
            when (event) {
                is ChatEvent.Delta -> events += AgentEvent.Text(event.text)
                is ChatEvent.ToolCall -> calls += event
                is ChatEvent.Usage -> events += AgentEvent.Usage(event.inputTokens, event.outputTokens)
                is ChatEvent.Error -> {
                    failed = true
                    events += AgentEvent.Error(event.message, event.retryable)
                }
                ChatEvent.Completed -> Unit
            }
        }
        return ModelRound(events, calls, failed)
    }

    private data class ModelRound(
        val events: List<AgentEvent>,
        val calls: List<ChatEvent.ToolCall>,
        val failed: Boolean,
    )

    override suspend fun cancel(requestId: RequestId) {
        provider.cancel(requestId)
    }

    private fun chatRequest(
        request: AgentTurnRequest,
        messages: List<ChatMessage>,
    ): ChatRequest = ChatRequest(
        requestId = request.requestId,
        model = request.model,
        messages = messages,
        tools = tools.specsFor(request.mode),
        stream = true,
    )

    private fun systemMessage(mode: AgentMode, packet: ContextPacket): ChatMessage {
        val body = buildString {
            appendLine(profile.systemPrompt)
            appendLine(ClankyardCodingAgent.modeHint(mode))
            appendLine("Context:")
            for (chunk in packet.chunks) {
                if (chunk.omitted) continue
                appendLine("--- ${chunk.label} ---")
                appendLine(chunk.text)
            }
        }
        return ChatMessage(ChatRole.System, listOf(ContentPart.Text(body)))
    }

    private suspend fun runTools(
        request: AgentTurnRequest,
        calls: List<ChatEvent.ToolCall>,
        edits: MutableList<ProposedEdit>,
    ): List<ContentPart.ToolResult> {
        val ctx = ToolContext(request.workspace, request.mode, git = request.git)
        val parts = ArrayList<ContentPart.ToolResult>()
        for (call in calls) {
            val tool = tools.get(call.name)
            val raw = if (tool == null) {
                "unknown tool: ${call.name}"
            } else {
                val args = parseArgs(call.argumentsJson)
                val result = tool.invoke(args, ctx)
                edits += result.proposedEdits
                result.content
            }
            val filtered = secrets.filterToolResult(raw)
            parts += ContentPart.ToolResult(
                toolCallId = call.id,
                content = filtered.text,
                isError = tool == null || filtered.omissions.isNotEmpty(),
            )
        }
        return parts
    }

    private fun assistantToolUses(calls: List<ChatEvent.ToolCall>): ChatMessage {
        val parts = calls.map { call ->
            ContentPart.ToolUse(call.id, call.name, call.argumentsJson)
        }
        return ChatMessage(ChatRole.Assistant, parts)
    }

    private fun parseArgs(raw: String): JsonObject {
        if (raw.isBlank()) return JsonObject(emptyMap())
        val parsed = json.parseToJsonElement(raw)
        return parsed as? JsonObject ?: JsonObject(emptyMap())
    }
}
