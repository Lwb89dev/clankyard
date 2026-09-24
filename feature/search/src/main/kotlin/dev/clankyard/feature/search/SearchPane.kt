package dev.clankyard.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.clankyard.core.ui.theme.PathTextStyle
import dev.clankyard.core.ui.theme.WorkshopSectionHeader
import dev.clankyard.core.ui.theme.WorkshopStatusPill
import dev.clankyard.search.SearchHit

@Composable
fun SearchPane(
    state: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WorkshopSectionHeader(
            kicker = "yard scanner",
            title = "Search",
            trailing = { WorkshopStatusPill("${state.hits.size} hits", active = state.hits.isNotEmpty()) },
        )
        TextField(
            value = state.query,
            onValueChange = { onEvent(SearchUiEvent.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            placeholder = { Text("Search in workshop") },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onEvent(SearchUiEvent.Submit) }) { Text("Find") }
            Checkbox(
                checked = state.caseSensitive,
                onCheckedChange = { onEvent(SearchUiEvent.CaseSensitive(it)) },
            )
            Text("Case sensitive")
        }
        if (state.searching) Text("Searching…", modifier = Modifier.padding(8.dp))
        if (state.truncated) Text("Results truncated.", modifier = Modifier.padding(8.dp))
        state.message?.let { Text(it, modifier = Modifier.padding(8.dp)) }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(state.hits, key = { hitKey(it) }) { hit ->
                SearchHitRow(hit) { onEvent(SearchUiEvent.OpenHit(hit)) }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.56f), MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics { contentDescription = hit.path.relative },
    ) {
        Text(
            "> ${hit.path.relative}:${hit.lineNumber}",
            style = PathTextStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(hit.lineText.trim(), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun hitKey(hit: SearchHit): String =
    "${hit.path.relative}:${hit.lineNumber}:${hit.column}"
