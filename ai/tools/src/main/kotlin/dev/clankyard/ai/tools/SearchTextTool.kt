package dev.clankyard.ai.tools

import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.search.SearchHit
import dev.clankyard.search.SearchQuery
import dev.clankyard.search.TextSearch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

class SearchTextTool(
    private val search: TextSearch,
) : GuardedTool() {
    override val spec = ToolSpec(
        name = "search_text",
        description = "Search UTF-8 files for a literal string via the workspace search engine.",
        parametersJsonSchema = SEARCH_TEXT_SCHEMA,
    )
    override val risk = ToolRisk.SafeRead

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val query = args.string("query") ?: return toolError("query is required")
        if (query.isEmpty()) return toolError("query is required")
        val path = when (val parsed = args.pathArg("path")) {
            is PathArg.Ok -> parsed.path
            is PathArg.Invalid -> return toolError(parsed.message)
            PathArg.Missing -> null
        }
        return try {
            val hits = search.search(ctx.workspace, SearchQuery(pattern = query, path = path))
            toolOk(formatHits(hits))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toolError(e.message ?: "search failed")
        }
    }
}

internal fun formatHits(hits: List<SearchHit>): String {
    if (hits.isEmpty()) return "no matches"
    return hits.joinToString("\n") { hit ->
        "${hit.path.relative}:${hit.line}:${hit.column}: ${hit.preview}"
    }
}
