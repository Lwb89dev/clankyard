package dev.clankyard.feature.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.theme.PathTextStyle

@Composable
fun ExplorerPane(
    state: ExplorerUiState,
    onEvent: (ExplorerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ExplorerToolbar(onEvent)
        state.message?.let { Text(it, modifier = Modifier.padding(8.dp)) }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(state.rows, key = { it.path.relative }) { row ->
                ExplorerRowItem(row, selected = row.path == state.selected, onEvent = onEvent)
            }
        }
    }
    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            path = pending.path,
            onConfirm = { onEvent(ExplorerUiEvent.ConfirmDelete(pending.path)) },
            onDismiss = { onEvent(ExplorerUiEvent.DismissDelete) },
        )
    }
    state.namePrompt?.let { prompt ->
        NamePromptDialog(prompt, onEvent)
    }
}

@Composable
private fun ExplorerToolbar(onEvent: (ExplorerUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = { onEvent(ExplorerUiEvent.RequestNewFile()) }) { Text("New file") }
        TextButton(onClick = { onEvent(ExplorerUiEvent.RequestNewFolder()) }) { Text("New folder") }
        TextButton(onClick = { onEvent(ExplorerUiEvent.RequestRename()) }) { Text("Rename") }
        TextButton(onClick = { onEvent(ExplorerUiEvent.RequestDelete()) }) { Text("Delete") }
        TextButton(onClick = { onEvent(ExplorerUiEvent.RequestSaveAs()) }) { Text("Save as") }
    }
}

@Composable
private fun ExplorerRowItem(
    row: ExplorerRow,
    selected: Boolean,
    onEvent: (ExplorerUiEvent) -> Unit,
) {
    val prefix = when {
        !row.isDirectory -> "  "
        row.expanded -> "▾ "
        else -> "▸ "
    }
    val label = prefix + row.name
    Text(
        text = label,
        style = PathTextStyle,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onEvent(ExplorerUiEvent.Select(row.path))
                if (row.isDirectory) onEvent(ExplorerUiEvent.Toggle(row.path))
                else onEvent(ExplorerUiEvent.Open(row.path))
            }
            .padding(start = (8 + row.depth * 12).dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
            .semantics { contentDescription = row.path.relative },
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
fun ConfirmDeleteDialog(
    path: WorkspacePath,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete") },
        text = { Text(confirmDeleteCopy(path)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NamePromptDialog(
    prompt: NamePrompt,
    onEvent: (ExplorerUiEvent) -> Unit,
) {
    var value by remember(prompt) { mutableStateOf(prompt.initial) }
    AlertDialog(
        onDismissRequest = { onEvent(ExplorerUiEvent.DismissName) },
        title = { Text(promptTitle(prompt.kind)) },
        text = {
            TextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ExplorerUiEvent.SubmitName(value)) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ExplorerUiEvent.DismissName) }) { Text("Cancel") }
        },
    )
}

private fun promptTitle(kind: NamePromptKind): String = when (kind) {
    NamePromptKind.NewFile -> "New file"
    NamePromptKind.NewFolder -> "New folder"
    NamePromptKind.Rename -> "Rename"
    NamePromptKind.SaveAs -> "Save as"
}
