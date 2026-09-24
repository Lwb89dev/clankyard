package dev.clankyard.ai.tools

import dev.clankyard.ai.context.AgentMode
import dev.clankyard.ai.patch.ProposedEdit
import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.core.model.RequestId
import dev.clankyard.git.GitHandle
import dev.clankyard.workspace.Workspace
import kotlinx.serialization.json.JsonObject

enum class ToolRisk { SafeRead, ReviewRequired, HighRisk }

/** Per-invocation, default deny. Never session-wide. MVP never issues these. */
data class HighRiskGrant(val toolName: String, val requestId: RequestId)

data class ToolResult(
    val content: String,
    val isError: Boolean,
    val proposedEdits: List<ProposedEdit> = emptyList(),
)

data class ToolContext(
    val workspace: Workspace,
    val mode: AgentMode,
    val grant: HighRiskGrant? = null,
    val git: GitHandle? = null,
)

interface Tool {
    val spec: ToolSpec
    val risk: ToolRisk

    /**
     * Throws SecurityException if risk == HighRisk and grant is null or name mismatch.
     * Path args go through WorkspacePath.parse only.
     */
    suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult
}

interface ToolRegistry {
    fun specsFor(mode: AgentMode): List<ToolSpec>
    fun get(name: String): Tool?
}

internal fun Tool.denyHighRiskUnlessGranted(ctx: ToolContext) {
    if (risk != ToolRisk.HighRisk) return
    val grant = ctx.grant
    if (grant == null || grant.toolName != spec.name) {
        throw SecurityException("HIGH_RISK ${spec.name} requires a per-invocation HighRiskGrant")
    }
}

internal fun toolError(message: String): ToolResult =
    ToolResult(content = message, isError = true, proposedEdits = emptyList())

internal fun toolOk(content: String, edits: List<ProposedEdit> = emptyList()): ToolResult =
    ToolResult(content = content, isError = false, proposedEdits = edits)
