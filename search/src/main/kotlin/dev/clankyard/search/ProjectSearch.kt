package dev.clankyard.search

import dev.clankyard.workspace.Workspace

interface ProjectSearch {
    suspend fun search(workspace: Workspace, query: SearchQuery): List<SearchHit>
}
