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
    val matchLength: Int = 0,
) {
    val lineNumber: Int get() = line
    val lineText: String get() = preview
}

data class SearchQuery(
    val pattern: String,
    val path: WorkspacePath? = null,
    val caseSensitive: Boolean = false,
    val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    val maxHits: Int = DEFAULT_MAX_HITS,
) {
    val maxMatches: Int get() = maxHits
}

interface TextSearch {
    suspend fun search(workspace: Workspace, query: SearchQuery): List<SearchHit>
}
