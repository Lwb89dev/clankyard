package dev.clankyard.ai.tools

import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.git.GitDiff
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class GitDiffTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "git_diff",
        description = "Show the current git diff, optionally for one workspace path.",
        parametersJsonSchema = GIT_DIFF_SCHEMA,
    )
    override val risk = ToolRisk.SafeRead

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val git = ctx.git ?: return toolError("no git repository")
        val path = when (val parsed = args.pathArg("path")) {
            is PathArg.Ok -> parsed.path
            is PathArg.Invalid -> return toolError(parsed.message)
            PathArg.Missing -> null
        }
        return try {
            toolOk(formatDiffs(git.diff(path)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolError(e.message ?: "git diff failed")
        }
    }
}

internal fun formatDiffs(diffs: List<GitDiff>): String {
    if (diffs.isEmpty()) return "no diff"
    return diffs.joinToString("\n") { it.unified }
}
