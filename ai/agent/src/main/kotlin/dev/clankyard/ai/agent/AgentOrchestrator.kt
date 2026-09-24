package dev.clankyard.ai.agent

import dev.clankyard.ai.context.AgentMode
import dev.clankyard.ai.context.ContextPacket
import dev.clankyard.ai.context.ContextRequest
import dev.clankyard.ai.patch.PatchSet
import dev.clankyard.ai.patch.ProposedEdit
import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.RequestId
import dev.clankyard.git.GitHandle
import dev.clankyard.ai.patch.PatchEngine
import dev.clankyard.workspace.Workspace
import kotlinx.coroutines.flow.Flow

data class AgentTurnRequest(
    val requestId: RequestId,
    val mode: AgentMode,
    val model: String,
    val credential: Credential,
    val context: ContextRequest,
    val workspace: Workspace,
    val git: GitHandle? = null,
    val patchEngine: PatchEngine? = null,
)

sealed interface AgentEvent {
    data class ContextReady(val packet: ContextPacket) : AgentEvent
    data class Text(val delta: String) : AgentEvent
    data class ToolStarted(val name: String) : AgentEvent
    data class ToolFinished(val name: String, val error: Boolean) : AgentEvent
    data class PatchProposed(val patch: PatchSet) : AgentEvent
    data class Usage(val inputTokens: Int?, val outputTokens: Int?) : AgentEvent
    data class Error(val message: String, val retryable: Boolean) : AgentEvent
    data object Completed : AgentEvent
}

interface AgentOrchestrator {
    fun run(request: AgentTurnRequest): Flow<AgentEvent>
    suspend fun cancel(requestId: RequestId)
}
