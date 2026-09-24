package dev.clankyard.ai.tools

import dev.clankyard.ai.context.AgentMode
import kotlinx.serialization.json.JsonObject

abstract class GuardedTool : Tool {
    final override suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult {
        denyHighRiskUnlessGranted(ctx)
        if (risk == ToolRisk.ReviewRequired && ctx.mode != AgentMode.Edit) {
            return toolError("writes are hidden in ${ctx.mode.name} mode")
        }
        return execute(args, ctx)
    }

    protected abstract suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}
