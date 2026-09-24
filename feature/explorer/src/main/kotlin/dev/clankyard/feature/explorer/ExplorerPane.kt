package dev.clankyard.feature.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.theme.PathTextStyle
import dev.clankyard.core.ui.theme.WorkshopSectionHeader
import dev.clankyard.core.ui.theme.WorkshopStatusPill
import dev.clankyard.core.ui.theme.WorkshopWindowSurface

@Composable
fun ExplorerPane(
    state: ExplorerUiState,
    onEvent: (ExplorerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WorkshopSectionHeader(
            kicker = "scrap index",
            title = "Files",
            trailing = { WorkshopStatusPill("${state.rows.size} items") },
        )
        ExplorerToolbar(onEvent)
        state.message?.let {
            Text(
                "> $it",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(state.rows, key = { it.path.relative }) { row ->
                ExplorerRowItem(
                    row = row,
                    selected = row.path == state.selected,
                    contextMenuOpen = row.path == state.contextMenuPath,
                    onEvent = onEvent,
                )
            }
        }
    }
    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            path = pending.path,
            isDirectory = pending.isDirectory,
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
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(4.dp),
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
    contextMenuOpen: Boolean,
    onEvent: (ExplorerUiEvent) -> Unit,
) {
    val prefix = when {
        !row.isDirectory -> "· "
        row.expanded -> "▾ [D] "
        else -> "▸ [D] "
    }
    val label = prefix + row.name
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else androidx.compose.ui.graphics.Color.Transparent,
            ),
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            text = label,
            style = PathTextStyle,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (row.isDirectory) onEvent(ExplorerUiEvent.Toggle(row.path))
                        else onEvent(ExplorerUiEvent.Open(row.path))
                    },
                    onLongClick = { onEvent(ExplorerUiEvent.ShowContextMenu(row.path)) },
                )
                .padding(start = (8 + row.depth * 12).dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                .semantics { contentDescription = row.path.relative },
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        DropdownMenu(
            expanded = contextMenuOpen,
            onDismissRequest = { onEvent(ExplorerUiEvent.DismissContextMenu) },
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = { onEvent(ExplorerUiEvent.RequestRename(row.path)) },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { onEvent(ExplorerUiEvent.RequestDelete(row.path)) },
            )
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    path: WorkspacePath,
    isDirectory: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete") },
        text = { Text(confirmDeleteCopy(path, isDirectory)) },
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
    Dialog(
        onDismissRequest = { onEvent(ExplorerUiEvent.DismissName) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            WorkshopWindowSurface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(promptTitle(prompt.kind), style = MaterialTheme.typography.titleLarge)
                    TextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { onEvent(ExplorerUiEvent.SubmitName(value)) },
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEvent(ExplorerUiEvent.DismissName) }) { Text("Cancel") }
                        TextButton(onClick = { onEvent(ExplorerUiEvent.SubmitName(value)) }) { Text("OK") }
                    }
                }
            }
        }
    }
}

private fun promptTitle(kind: NamePromptKind): String = when (kind) {
    NamePromptKind.NewFile -> "New file"
    NamePromptKind.NewFolder -> "New folder"
    NamePromptKind.Rename -> "Rename"
    NamePromptKind.SaveAs -> "Save as"
}
