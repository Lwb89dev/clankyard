package dev.clankyard.ai.tools

import dev.clankyard.ai.context.AgentMode
import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.search.TextSearch

class DefaultToolRegistry(tools: List<Tool>) : ToolRegistry {
    private val tools: List<Tool> = tools.toList()
    private val byName = tools.associateBy { it.spec.name }

    init {
        require(tools.none { it.risk == ToolRisk.HighRisk }) {
            "HIGH_RISK tools are not registered"
        }
    }

    override fun specsFor(mode: AgentMode): List<ToolSpec> {
        val includeWrites = mode == AgentMode.Edit
        return tools.mapNotNull { tool -> specIfVisible(tool, includeWrites) }
    }

    override fun get(name: String): Tool? = byName[name]

    companion object {
        fun mvp(search: TextSearch): DefaultToolRegistry =
            DefaultToolRegistry(mvpTools(search))
    }
}

internal fun mvpTools(search: TextSearch): List<Tool> = listOf(
    ReadFileTool(),
    ListDirectoryTool(),
    SearchTextTool(search),
    GitStatusTool(),
    GitDiffTool(),
    CreateFileTool(),
    ReplaceTextTool(),
    RenameFileTool(),
)

private fun specIfVisible(tool: Tool, includeWrites: Boolean): ToolSpec? = when (tool.risk) {
    ToolRisk.HighRisk -> null
    ToolRisk.ReviewRequired -> if (includeWrites) tool.spec else null
    ToolRisk.SafeRead -> tool.spec
}
