package dev.clankyard.feature.search

import dev.clankyard.search.ProjectSearch
import dev.clankyard.search.SearchHit
import dev.clankyard.search.SearchQuery
import dev.clankyard.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val caseSensitive: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val truncated: Boolean = false,
    val message: String? = null,
)

sealed interface SearchUiEvent {
    data class QueryChanged(val value: String) : SearchUiEvent
    data class CaseSensitive(val value: Boolean) : SearchUiEvent
    data object Submit : SearchUiEvent
    data class OpenHit(val hit: SearchHit) : SearchUiEvent
}

class SearchViewModel(
    private val workspace: Workspace,
    private val search: ProjectSearch,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _openHit = MutableSharedFlow<SearchHit>(extraBufferCapacity = 16)
    val openHit: SharedFlow<SearchHit> = _openHit.asSharedFlow()

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.QueryChanged -> _state.update { it.copy(query = event.value) }
            is SearchUiEvent.CaseSensitive -> _state.update { it.copy(caseSensitive = event.value) }
            SearchUiEvent.Submit -> scope.launch { runSearch() }
            is SearchUiEvent.OpenHit -> scope.launch { _openHit.emit(event.hit) }
        }
    }

    suspend fun runSearch() {
        val current = _state.value
        _state.update { it.copy(searching = true, message = null) }
        val query = SearchQuery(
            pattern = current.query,
            caseSensitive = current.caseSensitive,
        )
        val result = runCatching { search.search(workspace, query) }
        val hits = result.getOrDefault(emptyList())
        _state.update {
            it.copy(
                searching = false,
                hits = hits,
                truncated = hits.size >= SearchQuery.DEFAULT_MAX_MATCHES,
                message = result.exceptionOrNull()?.message,
            )
        }
    }
}
