package dev.clankyard.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
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
import dev.clankyard.search.SearchHit

@Composable
fun SearchPane(
    state: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
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
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = hit.path.relative },
    ) {
        Text("${hit.path.relative}:${hit.lineNumber}", style = PathTextStyle)
        Text(hit.lineText.trim(), maxLines = 1)
    }
}

private fun hitKey(hit: SearchHit): String =
    "${hit.path.relative}:${hit.lineNumber}:${hit.column}"
