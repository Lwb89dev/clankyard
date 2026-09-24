package dev.clankyard.ai.tools

import dev.clankyard.ai.patch.EditKind
import dev.clankyard.ai.patch.ProposedEdit
import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.core.model.WorkspacePath
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class RenameFileTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "rename_file",
        description = "Propose renaming a file. Fails if from is missing or to exists. Does not write.",
        parametersJsonSchema = RENAME_FILE_SCHEMA,
    )
    override val risk = ToolRisk.ReviewRequired

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val from = when (val parsed = args.requiredPath("from")) {
            is PathArg.Ok -> parsed.path
            is PathArg.Invalid -> return toolError(parsed.message)
            PathArg.Missing -> return toolError("from is required")
        }
        val to = when (val parsed = args.requiredPath("to")) {
            is PathArg.Ok -> parsed.path
            is PathArg.Invalid -> return toolError(parsed.message)
            PathArg.Missing -> return toolError("to is required")
        }
        if (from.isRoot || to.isRoot) return toolError("cannot rename workspace root")
        if (from == to) return toolError("source and destination are the same")
        return try {
            proposeRename(ctx, from, to)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolError(e.message ?: "rename_file failed")
        }
    }

    private suspend fun proposeRename(
        ctx: ToolContext,
        from: WorkspacePath,
        to: WorkspacePath,
    ): ToolResult {
        val src = ctx.workspace.metadata(from) ?: return toolError("from missing: ${from.relative}")
        val hash = src.hash ?: return toolError("from is not a file: ${from.relative}")
        if (ctx.workspace.metadata(to) != null) return toolError("to exists: ${to.relative}")
        val edit = ProposedEdit(
            path = from,
            kind = EditKind.Rename,
            expectedHash = hash,
            renameTo = to,
        )
        return toolOk("rename ${from.relative} -> ${to.relative}", listOf(edit))
    }
}
