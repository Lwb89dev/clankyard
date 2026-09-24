package dev.clankyard.search

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.Workspace

const val DEFAULT_MAX_FILE_BYTES = 256L * 1024
const val DEFAULT_MAX_HITS = 200

data class SearchHit(
    val path: WorkspacePath,
    val line: Int,
    val column: Int,
    val preview: String,
)

data class SearchQuery(
    val pattern: String,
    val path: WorkspacePath? = null,
    val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    val maxHits: Int = DEFAULT_MAX_HITS,
)

interface TextSearch {
    suspend fun search(workspace: Workspace, query: SearchQuery): List<SearchHit>
}
