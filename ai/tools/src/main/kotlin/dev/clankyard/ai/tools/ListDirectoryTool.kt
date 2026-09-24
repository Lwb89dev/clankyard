package dev.clankyard.ai.tools

import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.FileMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class ListDirectoryTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "list_directory",
        description = "List a directory. Empty path or \"/\" is the workspace root.",
        parametersJsonSchema = LIST_DIRECTORY_SCHEMA,
    )
    override val risk = ToolRisk.SafeRead

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val path = when (val parsed = args.pathArg("path")) {
            is PathArg.Ok -> parsed.path
            is PathArg.Invalid -> return toolError(parsed.message)
            PathArg.Missing -> WorkspacePath.ROOT
        }
        return try {
            val meta = ctx.workspace.metadata(path) ?: return toolError("not found: ${pathLabel(path)}")
            if (!meta.isDirectory) return toolError("not a directory: ${pathLabel(path)}")
            toolOk(formatListing(ctx.workspace.list(path)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolError(e.message ?: "list failed")
        }
    }
}

internal fun pathLabel(path: WorkspacePath): String = path.relative.ifEmpty { "/" }

internal fun formatListing(entries: List<FileMetadata>): String {
    if (entries.isEmpty()) return "(empty)"
    return entries.joinToString("\n") { meta ->
        val name = meta.path.relative.ifEmpty { "/" }
        if (meta.isDirectory) "$name/" else name
    }
}
