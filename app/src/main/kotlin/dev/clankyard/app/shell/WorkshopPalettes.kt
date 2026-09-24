package dev.clankyard.app.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.clankyard.app.session.WorkshopCommand
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.theme.PathTextStyle
import dev.clankyard.feature.search.SearchPane
import dev.clankyard.feature.search.SearchUiEvent
import dev.clankyard.feature.search.SearchUiState

@Composable
fun FilePaletteDialog(
    query: String,
    hits: List<WorkspacePath>,
    onQuery: (String) -> Unit,
    onPick: (WorkspacePath) -> Unit,
    onDismiss: () -> Unit,
) {
    val filtered = hits.filter { it.relative.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("// GO TO FILE") },
        text = {
            Column {
                TextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "File palette filter" },
                    singleLine = true,
                    placeholder = { Text("Ctrl+P") },
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.relative }) { path ->
                        Text(
                            text = "> ${path.relative}",
                            style = PathTextStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(path) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
fun CommandPaletteDialog(
    onCommand: (WorkshopCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("// COMMAND DECK") },
        text = {
            Column {
                WorkshopCommand.entries.forEach { command ->
                    Text(
                        text = "> ${command.label}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCommand(command) }
                            .padding(vertical = 8.dp)
                            .semantics { contentDescription = command.label },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
fun SearchPaletteDialog(
    state: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("// YARD SCANNER") },
        text = {
            SearchPane(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.heightIn(max = 420.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
