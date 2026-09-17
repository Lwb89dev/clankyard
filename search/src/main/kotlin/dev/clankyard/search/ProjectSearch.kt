package dev.clankyard.search

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.Workspace

data class SearchQuery(
    val pattern: String,
    val caseSensitive: Boolean = false,
    val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    val maxMatches: Int = DEFAULT_MAX_MATCHES,
) {
    companion object {
        const val DEFAULT_MAX_FILE_BYTES: Long = 1_048_576L
        const val DEFAULT_MAX_MATCHES: Int = 10_000
    }
}

data class SearchHit(
    val path: WorkspacePath,
    /** 1-based. */
    val lineNumber: Int,
    /** 0-based index in the line. */
    val column: Int,
    val lineText: String,
    val matchLength: Int,
)

interface ProjectSearch {
    suspend fun search(workspace: Workspace, query: SearchQuery): List<SearchHit>
}
