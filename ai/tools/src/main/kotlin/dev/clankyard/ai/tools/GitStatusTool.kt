package dev.clankyard.ai.tools

import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.git.GitStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class GitStatusTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "git_status",
        description = "Show staged, unstaged, untracked, and conflicted workspace paths.",
        parametersJsonSchema = GIT_STATUS_SCHEMA,
    )
    override val risk = ToolRisk.SafeRead

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val git = ctx.git ?: return toolError("no git repository")
        return try {
            toolOk(formatStatus(git.status()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolError(e.message ?: "git status failed")
        }
    }
}

internal fun formatStatus(status: GitStatus): String = buildString {
    append("branch: ")
    appendLine(status.branch ?: "(none)")
    appendSection("staged", status.staged)
    appendSection("unstaged", status.unstaged)
    appendSection("untracked", status.untracked)
    appendSection("conflicts", status.conflicts)
}

private fun StringBuilder.appendSection(title: String, paths: List<WorkspacePath>) {
    appendLine("$title:")
    for (path in paths) {
        append("  ")
        appendLine(path.relative)
    }
}
