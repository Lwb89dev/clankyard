package dev.clankyard.app.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.BottomTab
import dev.clankyard.core.ui.OpenTab
import dev.clankyard.core.ui.SizeClass
import dev.clankyard.core.ui.SizeClassRestore
import dev.clankyard.core.ui.WorkshopShortcut
import dev.clankyard.core.ui.WorkspaceUiState
import dev.clankyard.core.ui.WorkspaceUiStore
import dev.clankyard.core.ui.restoreSizeClass
import dev.clankyard.editor.CodeEditorController
import dev.clankyard.editor.EditorSession
import dev.clankyard.feature.explorer.ExplorerUiEffect
import dev.clankyard.feature.explorer.ExplorerUiEvent
import dev.clankyard.feature.explorer.ExplorerViewModel
import dev.clankyard.feature.search.SearchUiEvent
import dev.clankyard.feature.search.SearchViewModel
import dev.clankyard.search.ProjectSearch
import dev.clankyard.workspace.FileWorkspaceRegistry
import dev.clankyard.workspace.Workspace
import dev.clankyard.workspace.WorkspaceRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CompactDestination { Editor, Files, Clanker, Terminal, Git }

enum class WorkshopPaletteKind { None, File, Command, Search, Settings }

/** Activity-retained workshop chrome. Compact nav must not call [EditorSessionViewModel.bind]. */
@HiltViewModel
class WorkspaceSessionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val uiStore: WorkspaceUiStore,
    private val registry: FileWorkspaceRegistry,
    private val projectSearch: ProjectSearch,
) : ViewModel() {
    val uiState: StateFlow<WorkspaceUiState> = uiStore.state

    private val _workspace = MutableStateFlow<Workspace?>(null)
    val workspace: StateFlow<Workspace?> = _workspace.asStateFlow()

    private val _records = MutableStateFlow<List<WorkspaceRecord>>(emptyList())
    val records: StateFlow<List<WorkspaceRecord>> = _records.asStateFlow()

    private val _chrome = MutableStateFlow<SizeClassRestore?>(null)
    val chrome: StateFlow<SizeClassRestore?> = _chrome.asStateFlow()

    private val _compactDestination = MutableStateFlow(readCompactDest())
    val compactDestination: StateFlow<CompactDestination> = _compactDestination.asStateFlow()

    private val _palette = MutableStateFlow(WorkshopPaletteKind.None)
    val palette: StateFlow<WorkshopPaletteKind> = _palette.asStateFlow()

    private val _fileHits = MutableStateFlow<List<WorkspacePath>>(emptyList())
    val fileHits: StateFlow<List<WorkspacePath>> = _fileHits.asStateFlow()

    private val _fileQuery = MutableStateFlow("")
    val fileQuery: StateFlow<String> = _fileQuery.asStateFlow()

    /** Ephemeral. Never written to DataStore. Process death drops it. */
    private val _clankerTranscript = MutableStateFlow<List<String>>(emptyList())
    val clankerTranscript: StateFlow<List<String>> = _clankerTranscript.asStateFlow()

    private val _explorer = MutableStateFlow<ExplorerViewModel?>(null)
    val explorer: StateFlow<ExplorerViewModel?> = _explorer.asStateFlow()

    private val _search = MutableStateFlow<SearchViewModel?>(null)
    val search: StateFlow<SearchViewModel?> = _search.asStateFlow()

    var editorController: CodeEditorController? = null
    private var editorVm: EditorSessionViewModel? = null
    private var cursorJob: Job? = null
    private var effectsJob: Job? = null

    init {
        _records.value = registry.list()
        viewModelScope.launch { restoreWorkspace() }
    }

    fun onSizeClass(next: SizeClass) {
        val stored = uiStore.state.value
        val from = _chrome.value?.to ?: stored.lastSizeClass
        if (from == next && _chrome.value != null) return
        _chrome.value = restoreSizeClass(from, next, stored)
        persist { it.copy(lastSizeClass = next) }
    }

    fun attachEditor(editor: EditorSessionViewModel) {
        editorVm = editor
        val ws = _workspace.value ?: return
        if (!EditorBindingPolicy.shouldBind(editor.boundWorkspaceId(), ws.id)) return
        val session = editor.bind(ws)
        viewModelScope.launch { restoreTabs(session) }
    }

    fun createWorkshop(name: String) {
        val ws = registry.create(name.ifBlank { "workshop" })
        _records.value = registry.list()
        bindWorkspace(ws)
    }

    fun openWorkspace(id: WorkspaceId) {
        val ws = registry.open(id) ?: return
        bindWorkspace(ws)
    }

    fun closeWorkspace() {
        _workspace.value = null
        _explorer.value = null
        _search.value = null
        savedStateHandle[KEY_WORKSPACE_ID] = null
        persist { it.copy(workspaceId = null, tabs = emptyList(), activePath = null) }
    }

    fun navigateCompact(dest: CompactDestination) {
        _compactDestination.value = dest
        savedStateHandle[KEY_COMPACT_DEST] = dest.name
    }

    fun onExplorer(event: ExplorerUiEvent) {
        _explorer.value?.onEvent(event)
    }

    fun onSearch(event: SearchUiEvent) {
        _search.value?.onEvent(event)
    }

    fun openPath(path: WorkspacePath, line: Int = 0, col: Int = 0) {
        viewModelScope.launch { openInEditor(path, line, col) }
    }

    fun selectTab(path: WorkspacePath) {
        viewModelScope.launch {
            editorVm?.session?.value?.open(path)
            persist { it.copy(activePath = path) }
            savedStateHandle[KEY_ACTIVE_PATH] = path.relative
        }
    }

    fun closeTab(path: WorkspacePath) {
        viewModelScope.launch {
            editorVm?.session?.value?.close(path)
            persist { state ->
                val tabs = state.tabs.filterNot { it.path == path }
                val active = if (state.activePath == path) tabs.lastOrNull()?.path else state.activePath
                state.copy(tabs = tabs, activePath = active)
            }
        }
    }

    fun edit(path: WorkspacePath, text: String) {
        editorVm?.session?.value?.edit(path, text)
    }

    fun onCursor(path: WorkspacePath, line: Int, col: Int) {
        cursorJob?.cancel()
        cursorJob = viewModelScope.launch {
            delay(250)
            persist { state ->
                state.copy(
                    tabs = state.tabs.map { tab ->
                        if (tab.path == path) tab.copy(cursorLine = line, cursorCol = col) else tab
                    },
                )
            }
        }
    }

    fun onWeights(files: Float, editor: Float, clanker: Float, bottom: Float) {
        val next = _chrome.value?.copy(
            filesWeight = files,
            editorWeight = editor,
            clankerWeight = clanker,
            bottomWeight = bottom,
        )
        if (next != null) _chrome.value = next
        persist {
            it.copy(
                filesWeight = files,
                editorWeight = editor,
                clankerWeight = clanker,
                bottomWeight = bottom,
            )
        }
    }

    fun setBottomTab(tab: BottomTab) {
        _chrome.value = _chrome.value?.copy(bottomTab = tab)
        persist { it.copy(bottomTab = tab) }
    }

    fun toggleFiles() {
        val next = !(_chrome.value?.filesCollapsed ?: false)
        _chrome.value = _chrome.value?.copy(filesCollapsed = next)
        persist { it.copy(filesCollapsed = next) }
    }

    fun toggleClanker() {
        val next = !(_chrome.value?.clankerVisible ?: true)
        val size = _chrome.value?.to ?: SizeClass.Compact
        _chrome.value = _chrome.value?.copy(
            clankerVisible = next,
            clankerDocked = next && size == SizeClass.Expanded,
            clankerOverlay = next && size == SizeClass.Medium,
        )
        persist { it.copy(clankerVisible = next) }
        savedStateHandle[KEY_CLANKER_VISIBLE] = next
    }

    fun toggleBottom() {
        val nextCollapsed = !(_chrome.value?.bottomCollapsed ?: true)
        _chrome.value = _chrome.value?.copy(bottomCollapsed = nextCollapsed)
        persist { it.copy(bottomCollapsed = nextCollapsed) }
    }

    fun saveActive() {
        viewModelScope.launch { editorVm?.saveActive() }
    }

    fun saveAll() {
        viewModelScope.launch { editorVm?.saveAll() }
    }

    fun setFileQuery(value: String) {
        _fileQuery.value = value
    }

    fun onShortcut(shortcut: WorkshopShortcut): Boolean = when (shortcut) {
        WorkshopShortcut.FilePalette -> {
            openPalette(WorkshopPaletteKind.File)
            true
        }
        WorkshopShortcut.ProjectSearch -> {
            openPalette(WorkshopPaletteKind.Search)
            true
        }
        WorkshopShortcut.Save -> {
            saveActive()
            true
        }
        WorkshopShortcut.SaveAll -> {
            saveAll()
            true
        }
        WorkshopShortcut.ToggleTerminal -> {
            toggleTerminal()
            true
        }
        WorkshopShortcut.CommandPalette -> {
            openPalette(WorkshopPaletteKind.Command)
            true
        }
        WorkshopShortcut.Undo -> {
            editorController?.undo()
            true
        }
        WorkshopShortcut.Redo -> {
            editorController?.redo()
            true
        }
        WorkshopShortcut.DismissPalettes -> dismissPalette()
    }

    fun openPalette(kind: WorkshopPaletteKind) {
        _palette.value = kind
        if (kind == WorkshopPaletteKind.File) {
            _fileQuery.value = ""
            viewModelScope.launch { _fileHits.value = indexFiles() }
        }
    }

    fun dismissPalette(): Boolean {
        if (_palette.value == WorkshopPaletteKind.None) return false
        _palette.value = WorkshopPaletteKind.None
        return true
    }

    fun runCommand(command: WorkshopCommand) {
        _palette.value = WorkshopPaletteKind.None
        when (command) {
            WorkshopCommand.Save -> saveActive()
            WorkshopCommand.SaveAll -> saveAll()
            WorkshopCommand.GoToFile -> openPalette(WorkshopPaletteKind.File)
            WorkshopCommand.Search -> openPalette(WorkshopPaletteKind.Search)
            WorkshopCommand.ToggleTerminal -> toggleTerminal()
            WorkshopCommand.ToggleClanker -> toggleClanker()
            WorkshopCommand.ToggleFiles -> toggleFiles()
            WorkshopCommand.Settings -> openPalette(WorkshopPaletteKind.Settings)
        }
    }

    private fun toggleTerminal() {
        val size = _chrome.value?.to ?: SizeClass.Compact
        if (size == SizeClass.Compact) {
            navigateCompact(CompactDestination.Terminal)
            return
        }
        val collapsed = _chrome.value?.bottomCollapsed ?: true
        _chrome.value = _chrome.value?.copy(
            bottomCollapsed = !collapsed,
            bottomTab = BottomTab.Terminal,
        )
        persist { it.copy(bottomCollapsed = !collapsed, bottomTab = BottomTab.Terminal) }
    }

    private suspend fun restoreWorkspace() {
        val stored = uiStore.snapshot()
        val idValue = savedStateHandle.get<String>(KEY_WORKSPACE_ID) ?: stored.workspaceId?.value
        val id = idValue?.let(::WorkspaceId) ?: return
        val ws = registry.open(id) ?: return
        bindWorkspace(ws, persistId = false)
        savedStateHandle.get<Boolean>(KEY_CLANKER_VISIBLE)?.let { visible ->
            if (visible != stored.clankerVisible) persist { it.copy(clankerVisible = visible) }
        }
    }

    private fun bindWorkspace(ws: Workspace, persistId: Boolean = true) {
        _workspace.value = ws
        savedStateHandle[KEY_WORKSPACE_ID] = ws.id.value
        if (persistId) persist { it.copy(workspaceId = ws.id) }
        val explorerVm = ExplorerViewModel(ws, viewModelScope)
        explorerVm.onEvent(ExplorerUiEvent.Refresh)
        _explorer.value = explorerVm
        _search.value = SearchViewModel(ws, projectSearch, viewModelScope)
        collectEffects()
        val editor = editorVm
        if (editor != null) attachEditor(editor)
    }

    private fun collectEffects() {
        effectsJob?.cancel()
        val explorerVm = _explorer.value ?: return
        val searchVm = _search.value ?: return
        effectsJob = viewModelScope.launch {
            launch {
                explorerVm.effects.collect { effect -> handleExplorerEffect(effect) }
            }
            launch {
                searchVm.openHit.collect { hit ->
                    openInEditor(hit.path, (hit.lineNumber - 1).coerceAtLeast(0), hit.column)
                }
            }
        }
    }

    private suspend fun handleExplorerEffect(effect: ExplorerUiEffect) {
        val session = editorVm?.session?.value
        when (effect) {
            is ExplorerUiEffect.OpenFile -> openInEditor(effect.path)
            is ExplorerUiEffect.Deleted -> session?.notifyDeleted(effect.path)
            is ExplorerUiEffect.Renamed -> {
                session?.notifyRenamed(effect.from, effect.to)
                persist { state ->
                    state.copy(
                        tabs = state.tabs.map { tab ->
                            if (tab.path == effect.from) tab.copy(path = effect.to) else tab
                        },
                        activePath = if (state.activePath == effect.from) effect.to else state.activePath,
                    )
                }
            }
            is ExplorerUiEffect.SavedAs -> openInEditor(effect.to)
        }
    }

    private suspend fun restoreTabs(session: EditorSession) {
        val stored = uiStore.snapshot()
        for (tab in stored.tabs) session.open(tab.path)
        stored.activePath?.let { session.open(it) }
        savedStateHandle[KEY_ACTIVE_PATH] = stored.activePath?.relative
    }

    private suspend fun openInEditor(path: WorkspacePath, line: Int = 0, col: Int = 0) {
        val session = editorVm?.session?.value ?: return
        session.open(path)
        persist { state ->
            val exists = state.tabs.any { it.path == path }
            val tabs = if (exists) {
                state.tabs.map { tab ->
                    if (tab.path == path) tab.copy(cursorLine = line, cursorCol = col) else tab
                }
            } else {
                state.tabs + OpenTab(path, line, col)
            }
            state.copy(tabs = tabs, activePath = path)
        }
        savedStateHandle[KEY_ACTIVE_PATH] = path.relative
        navigateCompact(CompactDestination.Editor)
        _palette.value = WorkshopPaletteKind.None
        if (line != 0 || col != 0) editorController?.moveCursor(line, col)
    }

    private suspend fun indexFiles(): List<WorkspacePath> {
        val ws = _workspace.value ?: return emptyList()
        val out = ArrayList<WorkspacePath>()
        walkFiles(ws, WorkspacePath.ROOT, out)
        return out
    }

    private suspend fun walkFiles(
        ws: Workspace,
        dir: WorkspacePath,
        out: MutableList<WorkspacePath>,
    ) {
        for (child in ws.list(dir)) {
            if (child.isDirectory) walkFiles(ws, child.path, out)
            else out += child.path
        }
    }

    private fun persist(transform: (WorkspaceUiState) -> WorkspaceUiState) {
        viewModelScope.launch { uiStore.update(transform) }
    }

    private fun readCompactDest(): CompactDestination {
        val raw = savedStateHandle.get<String>(KEY_COMPACT_DEST) ?: return CompactDestination.Editor
        return runCatching { CompactDestination.valueOf(raw) }.getOrDefault(CompactDestination.Editor)
    }

    private companion object {
        const val KEY_WORKSPACE_ID = "workspaceId"
        const val KEY_ACTIVE_PATH = "activePath"
        const val KEY_CLANKER_VISIBLE = "clankerVisible"
        const val KEY_COMPACT_DEST = "compactDestination"
    }
}

enum class WorkshopCommand(val label: String) {
    Save("Save"),
    SaveAll("Save all"),
    GoToFile("Go to file"),
    Search("Search workshop"),
    ToggleTerminal("Toggle terminal"),
    ToggleClanker("Toggle Clanker"),
    ToggleFiles("Toggle files pane"),
    Settings("Settings"),
}
