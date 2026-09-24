package dev.clankyard.feature.explorer

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.FileMetadata
import dev.clankyard.workspace.Workspace
import dev.clankyard.workspace.WriteRequest
import dev.clankyard.workspace.WriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExplorerViewModel(
    private val workspace: Workspace,
    private val scope: CoroutineScope,
) {
    private val expanded = linkedSetOf(WorkspacePath.ROOT)
    private val _state = MutableStateFlow(ExplorerUiState())
    val state: StateFlow<ExplorerUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ExplorerUiEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<ExplorerUiEffect> = _effects.asSharedFlow()

    fun onEvent(event: ExplorerUiEvent) {
        scope.launch { handle(event) }
    }

    suspend fun handle(event: ExplorerUiEvent) {
        when (event) {
            is ExplorerUiEvent.Toggle -> toggle(event.path)
            is ExplorerUiEvent.Open -> open(event.path)
            is ExplorerUiEvent.ShowContextMenu -> showContextMenu(event.path)
            ExplorerUiEvent.DismissContextMenu -> _state.update { it.copy(contextMenuPath = null) }
            is ExplorerUiEvent.RequestNewFile -> prompt(NamePromptKind.NewFile, event.parent)
            is ExplorerUiEvent.RequestNewFolder -> prompt(NamePromptKind.NewFolder, event.parent)
            is ExplorerUiEvent.RequestRename -> requestRename(event.path)
            is ExplorerUiEvent.RequestDelete -> requestDelete(event.path)
            is ExplorerUiEvent.RequestSaveAs -> requestSaveAs(event.path)
            is ExplorerUiEvent.ConfirmDelete -> confirmDelete(event.path)
            ExplorerUiEvent.DismissDelete -> _state.update { it.copy(pendingDelete = null) }
            is ExplorerUiEvent.SubmitName -> submitName(event.value)
            ExplorerUiEvent.DismissName -> _state.update { it.copy(namePrompt = null) }
            ExplorerUiEvent.Refresh -> rebuild()
        }
    }

    private suspend fun toggle(path: WorkspacePath) {
        val row = row(path) ?: return
        if (!row.isDirectory) return
        if (path in expanded) expanded -= path else expanded += path
        _state.update { it.copy(selected = path, contextMenuPath = null, message = null) }
        rebuild()
    }

    private suspend fun open(path: WorkspacePath) {
        val meta = workspace.metadata(path) ?: return
        _state.update { it.copy(selected = path, contextMenuPath = null, message = null) }
        if (meta.isDirectory) {
            toggle(path)
            return
        }
        _effects.emit(ExplorerUiEffect.OpenFile(path))
    }

    private fun showContextMenu(path: WorkspacePath) {
        if (row(path) == null) return
        _state.update { it.copy(selected = path, contextMenuPath = path, message = null) }
    }

    private fun prompt(kind: NamePromptKind, parent: WorkspacePath?) {
        val dir = directoryFor(parent)
        _state.update {
            it.copy(
                contextMenuPath = null,
                namePrompt = NamePrompt(kind = kind, parent = dir),
                message = null,
            )
        }
    }

    private fun requestRename(path: WorkspacePath?) {
        val row = resolveRow(path) ?: return
        _state.update {
            it.copy(
                selected = row.path,
                contextMenuPath = null,
                namePrompt = NamePrompt(
                    kind = NamePromptKind.Rename,
                    parent = row.path.parent(),
                    target = row.path,
                    initial = row.name,
                ),
                message = null,
            )
        }
    }

    private fun requestDelete(path: WorkspacePath?) {
        val row = resolveRow(path) ?: return
        _state.update {
            it.copy(
                selected = row.path,
                contextMenuPath = null,
                pendingDelete = row,
                message = null,
            )
        }
    }

    private fun requestSaveAs(path: WorkspacePath?) {
        val row = resolveRow(path) ?: return
        if (row.isDirectory) {
            _state.update { it.copy(message = "save-as is for files") }
            return
        }
        _state.update {
            it.copy(
                selected = row.path,
                contextMenuPath = null,
                namePrompt = NamePrompt(
                    kind = NamePromptKind.SaveAs,
                    parent = row.path.parent(),
                    target = row.path,
                    initial = row.path.relative,
                ),
                message = null,
            )
        }
    }

    private suspend fun confirmDelete(path: WorkspacePath) {
        val pending = _state.value.pendingDelete
        if (pending == null || pending.path != path) {
            _state.update { it.copy(message = "delete requires confirm") }
            return
        }
        val expected = if (pending.isDirectory) null else pending.hash
        if (!pending.isDirectory && expected == null) {
            _state.update { it.copy(pendingDelete = null, message = "expectedHash required for files") }
            return
        }
        when (val result = workspace.delete(path, expected)) {
            is WriteResult.Applied -> {
                expanded -= path
                _state.update { it.copy(pendingDelete = null, selected = null, message = null) }
                _effects.emit(ExplorerUiEffect.Deleted(path))
                rebuild()
            }
            is WriteResult.Conflict -> failDelete(result.reason)
            is WriteResult.Rejected -> failDelete(result.reason)
        }
    }

    private fun failDelete(reason: String) {
        _state.update { it.copy(pendingDelete = null, message = reason) }
    }

    private suspend fun submitName(raw: String) {
        val prompt = _state.value.namePrompt ?: return
        val dest = parseDest(prompt, raw)
        if (dest == null) {
            _state.update { it.copy(message = "illegal path") }
            return
        }
        val result = applyPrompt(prompt, dest)
        finishWrite(prompt, dest, result)
    }

    private suspend fun applyPrompt(prompt: NamePrompt, dest: WorkspacePath): WriteResult =
        when (prompt.kind) {
            NamePromptKind.NewFile -> createFile(dest)
            NamePromptKind.NewFolder -> createFolder(dest)
            NamePromptKind.Rename -> rename(prompt.target, dest)
            NamePromptKind.SaveAs -> saveAs(prompt.target, dest)
        }

    private suspend fun createFile(path: WorkspacePath): WriteResult =
        workspace.writeAtomic(WriteRequest(path, ByteArray(0), expectedHash = null))

    private suspend fun createFolder(path: WorkspacePath): WriteResult {
        val existing = workspace.metadata(path)
        if (existing != null) return WriteResult.Conflict(existing.hash, "already exists")
        val marker = path.child(DIR_MARKER)
        val written = workspace.writeAtomic(WriteRequest(marker, ByteArray(0), expectedHash = null))
        if (written !is WriteResult.Applied) return written
        val removed = workspace.delete(marker, written.newHash)
        if (removed !is WriteResult.Applied) {
            return WriteResult.Rejected("folder created but marker remains")
        }
        return written
    }

    private suspend fun rename(from: WorkspacePath?, dest: WorkspacePath): WriteResult {
        if (from == null) return WriteResult.Rejected("nothing to rename")
        val meta = workspace.metadata(from) ?: return WriteResult.Rejected("file missing")
        val expected = if (meta.isDirectory) null else meta.hash
        if (!meta.isDirectory && expected == null) {
            return WriteResult.Rejected("expectedHash required for files")
        }
        return workspace.rename(from, dest, expected)
    }

    private suspend fun saveAs(from: WorkspacePath?, dest: WorkspacePath): WriteResult {
        if (from == null) return WriteResult.Rejected("nothing to save")
        val meta = workspace.metadata(from) ?: return WriteResult.Rejected("file missing")
        if (meta.isDirectory) return WriteResult.Rejected("save-as is for files")
        val bytes = workspace.openRead(from).use { it.readBytes() }
        return workspace.writeAtomic(WriteRequest(dest, bytes, expectedHash = null))
    }

    private suspend fun finishWrite(prompt: NamePrompt, dest: WorkspacePath, result: WriteResult) {
        when (result) {
            is WriteResult.Applied -> {
                expandParents(dest)
                _state.update { it.copy(namePrompt = null, selected = dest, message = null) }
                emitApplied(prompt, dest)
                rebuild()
            }
            is WriteResult.Conflict -> _state.update { it.copy(message = result.reason) }
            is WriteResult.Rejected -> _state.update { it.copy(message = result.reason) }
        }
    }

    private suspend fun emitApplied(prompt: NamePrompt, dest: WorkspacePath) {
        val from = prompt.target ?: return
        when (prompt.kind) {
            NamePromptKind.Rename -> _effects.emit(ExplorerUiEffect.Renamed(from, dest))
            NamePromptKind.SaveAs -> _effects.emit(ExplorerUiEffect.SavedAs(from, dest))
            else -> Unit
        }
    }

    private fun parseDest(prompt: NamePrompt, raw: String): WorkspacePath? {
        val name = raw.trim()
        if (name.isEmpty()) return null
        return when (prompt.kind) {
            NamePromptKind.SaveAs -> runCatching { WorkspacePath.parse(name) }.getOrNull()
            else -> runCatching { prompt.parent.child(name) }.getOrNull()
        }
    }

    private fun directoryFor(explicit: WorkspacePath?): WorkspacePath {
        if (explicit != null) {
            val row = row(explicit)
            if (row == null || row.isDirectory) return explicit
            return explicit.parent()
        }
        val selected = _state.value.selected ?: return WorkspacePath.ROOT
        val row = row(selected) ?: return WorkspacePath.ROOT
        return if (row.isDirectory) row.path else row.path.parent()
    }

    private fun resolveRow(path: WorkspacePath?): ExplorerRow? {
        val target = path ?: _state.value.selected ?: return null
        return row(target)
    }

    private fun row(path: WorkspacePath): ExplorerRow? =
        _state.value.rows.find { it.path == path }

    private fun expandParents(path: WorkspacePath) {
        var current = path.parent()
        while (!current.isRoot) {
            expanded += current
            current = current.parent()
        }
        expanded += WorkspacePath.ROOT
    }

    private suspend fun rebuild() {
        val rows = ArrayList<ExplorerRow>()
        val children = workspace.list(WorkspacePath.ROOT)
        for (child in children) {
            if (dev.clankyard.workspace.GeneratedDirNames.hides(child.path.name)) continue
            appendExpanded(child, 0, rows)
        }
        _state.update { it.copy(rows = rows) }
    }

    private suspend fun appendExpanded(meta: FileMetadata, depth: Int, into: MutableList<ExplorerRow>) {
        val isExpanded = meta.isDirectory && meta.path in expanded
        into += ExplorerRow(
            path = meta.path,
            name = meta.path.name,
            isDirectory = meta.isDirectory,
            depth = depth,
            expanded = isExpanded,
            hash = meta.hash,
        )
        if (!isExpanded) return
        val children = workspace.list(meta.path)
        for (child in children) {
            if (dev.clankyard.workspace.GeneratedDirNames.hides(child.path.name)) continue
            appendExpanded(child, depth + 1, into)
        }
    }
}

private const val DIR_MARKER = ".clankyard-dir"
