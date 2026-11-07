package dev.clankyard.ai.tools

import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.build.api.BuildTask
import dev.clankyard.build.api.ProposedBuild
import kotlinx.serialization.json.JsonObject

class GetBuildStatusTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "get_build_status",
        description = "Read the current on-device build snapshot. Does not start a build.",
        parametersJsonSchema = EMPTY_OBJECT_SCHEMA,
    )
    override val risk = ToolRisk.SafeRead

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        rejectExtraKeys(args)?.let { return it }
        val query = ctx.buildQuery ?: return toolError("no build session")
        val snap = query.snapshot()
        return toolOk(
            "running=${snap.running} last=${snap.last} task=${snap.task} logChars=${snap.logChars} " +
                "diagnostics=${snap.diagnostics.size} apk=${snap.lastApk?.relative}",
        )
    }
}

class GetBuildLogTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "get_build_log",
        description = "Read the tail of the current build log. Does not start a build.",
        parametersJsonSchema = EMPTY_OBJECT_SCHEMA,
    )
    override val risk = ToolRisk.SafeRead

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        rejectExtraKeys(args)?.let { return it }
        val query = ctx.buildQuery ?: return toolError("no build session")
        return toolOk(query.logTail(8_000).ifBlank { "(empty log)" })
    }
}

class GetBuildDiagnosticsTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "get_diagnostics",
        description = "Read parsed compiler diagnostics from the last build.",
        parametersJsonSchema = EMPTY_OBJECT_SCHEMA,
    )
    override val risk = ToolRisk.SafeRead

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        rejectExtraKeys(args)?.let { return it }
        val query = ctx.buildQuery ?: return toolError("no build session")
        val items = query.diagnostics()
        if (items.isEmpty()) return toolOk("(no diagnostics)")
        val body = items.joinToString("\n") { item ->
            val path = item.path?.relative ?: "?"
            "$path:${item.line + 1}:${item.column + 1} ${item.severity} ${item.message}"
        }
        return toolOk(body)
    }
}

class ProposeBuildTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "propose_build",
        description = "Propose assembleDebug, clean, or rebuild. Does not start Gradle. Human must accept.",
        parametersJsonSchema = PROPOSE_BUILD_SCHEMA,
    )
    override val risk = ToolRisk.ReviewRequired

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val unknown = args.keys.filter { it != "task" }
        if (unknown.isNotEmpty()) {
            return toolError("unexpected argument: ${unknown.joinToString()}")
        }
        val raw = args.string("task") ?: return toolError("task is required")
        val task = TASKS[raw] ?: return toolError("task must be assembleDebug, clean, or rebuild")
        return toolOk("propose ${task.name}", builds = listOf(ProposedBuild(task)))
    }
}

private val TASKS = mapOf(
    "assembleDebug" to BuildTask.AssembleDebug,
    "clean" to BuildTask.Clean,
    "rebuild" to BuildTask.RebuildDebug,
)

private fun rejectExtraKeys(args: JsonObject): ToolResult? {
    if (args.isEmpty()) return null
    return toolError("unexpected argument: ${args.keys.joinToString()}")
}
