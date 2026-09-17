package dev.clankyard.feature.explorer

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.WorkspacePath

data class ExplorerRow(
    val path: WorkspacePath,
    val name: String,
    val isDirectory: Boolean,
    val depth: Int,
    val expanded: Boolean,
    val hash: ContentHash?,
)

enum class NamePromptKind { NewFile, NewFolder, Rename, SaveAs }

data class NamePrompt(
    val kind: NamePromptKind,
    val parent: WorkspacePath,
    val target: WorkspacePath? = null,
    val initial: String = "",
)

data class ExplorerUiState(
    val rows: List<ExplorerRow> = emptyList(),
    val selected: WorkspacePath? = null,
    val pendingDelete: ExplorerRow? = null,
    val namePrompt: NamePrompt? = null,
    val message: String? = null,
)

sealed interface ExplorerUiEvent {
    data class Toggle(val path: WorkspacePath) : ExplorerUiEvent
    data class Open(val path: WorkspacePath) : ExplorerUiEvent
    data class RequestNewFile(val parent: WorkspacePath? = null) : ExplorerUiEvent
    data class RequestNewFolder(val parent: WorkspacePath? = null) : ExplorerUiEvent
    data class RequestRename(val path: WorkspacePath? = null) : ExplorerUiEvent
    data class RequestDelete(val path: WorkspacePath? = null) : ExplorerUiEvent
    data class RequestSaveAs(val path: WorkspacePath? = null) : ExplorerUiEvent
    data class ConfirmDelete(val path: WorkspacePath) : ExplorerUiEvent
    data object DismissDelete : ExplorerUiEvent
    data class SubmitName(val value: String) : ExplorerUiEvent
    data object DismissName : ExplorerUiEvent
    data object Refresh : ExplorerUiEvent
}

sealed interface ExplorerUiEffect {
    data class OpenFile(val path: WorkspacePath) : ExplorerUiEffect
    data class Deleted(val path: WorkspacePath) : ExplorerUiEffect
    data class Renamed(val from: WorkspacePath, val to: WorkspacePath) : ExplorerUiEffect
    data class SavedAs(val from: WorkspacePath, val to: WorkspacePath) : ExplorerUiEffect
}

fun confirmDeleteCopy(path: WorkspacePath, isDirectory: Boolean): String =
    if (isDirectory) {
        "Delete ${path.relative} and all contents? This cannot be undone from the explorer."
    } else {
        "Delete ${path.relative}? This cannot be undone from the explorer."
    }
