package dev.clankyard.ai.tools

import dev.clankyard.ai.patch.EditKind
import dev.clankyard.ai.patch.ProposedEdit
import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.core.model.WorkspacePath
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class ReplaceTextTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "replace_text",
        description = "Propose a whole-file replace of a unique string. Does not write.",
        parametersJsonSchema = REPLACE_TEXT_SCHEMA,
    )
    override val risk = ToolRisk.ReviewRequired

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val path = when (val parsed = args.requiredPath("path")) {
            is PathArg.Ok -> parsed.path
            is PathArg.Invalid -> return toolError(parsed.message)
            PathArg.Missing -> return toolError("path is required")
        }
        if (path.isRoot) return toolError("cannot replace workspace root")
        if (!args.containsKey("old_string")) return toolError("old_string is required")
        if (!args.containsKey("new_string")) return toolError("new_string is required")
        val oldString = args.string("old_string") ?: return toolError("old_string is required")
        val newString = args.string("new_string") ?: return toolError("new_string is required")
        if (oldString.isEmpty()) return toolError("old_string must be non-empty")
        return try {
            replaceOnce(ctx, path, oldString, newString)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolError(e.readErrorMessage())
        }
    }

    private suspend fun replaceOnce(
        ctx: ToolContext,
        path: WorkspacePath,
        oldString: String,
        newString: String,
    ): ToolResult {
        val meta = ctx.workspace.metadata(path) ?: return toolError("file missing: ${path.relative}")
        val hash = meta.hash ?: return toolError("not a file: ${path.relative}")
        val current = readWorkspaceUtf8(ctx.workspace, path, EDIT_READ_MAX_BYTES)
        val matches = countOccurrences(current, oldString)
        if (matches != 1) {
            return toolError("old_string must occur exactly once (found $matches)")
        }
        val after = current.replace(oldString, newString)
        val edit = ProposedEdit(
            path = path,
            kind = EditKind.Replace,
            expectedHash = hash,
            afterUtf8 = after,
        )
        return toolOk("replace ${path.relative}", listOf(edit))
    }
}
