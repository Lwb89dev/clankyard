package dev.clankyard.ai.tools

import dev.clankyard.ai.provider.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class ReadFileTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "read_file",
        description = "Read a UTF-8 text file. Path is workspace-relative.",
        parametersJsonSchema = READ_FILE_SCHEMA,
    )
    override val risk = ToolRisk.SafeRead

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val path = when (val parsed = args.requiredPath("path")) {
            is PathArg.Ok -> parsed.path
            is PathArg.Invalid -> return toolError(parsed.message)
            PathArg.Missing -> return toolError("path is required")
        }
        if (path.isRoot) return toolError("cannot read workspace root")
        return try {
            toolOk(readWorkspaceUtf8(ctx.workspace, path, SAFE_READ_MAX_BYTES))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolError(e.readErrorMessage())
        }
    }
}
