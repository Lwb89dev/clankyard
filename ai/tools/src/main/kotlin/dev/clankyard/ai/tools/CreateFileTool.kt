package dev.clankyard.ai.tools

import dev.clankyard.ai.patch.EditKind
import dev.clankyard.ai.patch.ProposedEdit
import dev.clankyard.ai.provider.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class CreateFileTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "create_file",
        description = "Propose creating a new file. Fails if the destination exists. Does not write.",
        parametersJsonSchema = CREATE_FILE_SCHEMA,
    )
    override val risk = ToolRisk.ReviewRequired

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val path = when (val parsed = args.requiredPath("path")) {
            is PathArg.Ok -> parsed.path
            is PathArg.Invalid -> return toolError(parsed.message)
            PathArg.Missing -> return toolError("path is required")
        }
        if (path.isRoot) return toolError("cannot create workspace root")
        if (!args.containsKey("content")) return toolError("content is required")
        val content = args.string("content") ?: return toolError("content is required")
        return try {
            if (ctx.workspace.metadata(path) != null) {
                return toolError("destination exists: ${path.relative}")
            }
            val edit = ProposedEdit(
                path = path,
                kind = EditKind.Create,
                expectedHash = null,
                afterUtf8 = content,
            )
            toolOk("create ${path.relative}", listOf(edit))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolError(e.message ?: "create_file failed")
        }
    }
}
