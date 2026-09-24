package dev.clankyard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.clankyard.app.session.EditorSessionViewModel
import dev.clankyard.app.session.WorkspaceSessionViewModel
import dev.clankyard.app.onboarding.OnboardingScreen
import dev.clankyard.app.onboarding.OnboardingViewModel
import dev.clankyard.app.shell.AdaptiveShell
import dev.clankyard.app.shell.AdaptiveShellState
import dev.clankyard.app.shell.FeaturePlaceholder
import dev.clankyard.app.shell.WorkshopPickerScreen
import dev.clankyard.app.shell.rememberHeightCompact
import dev.clankyard.app.shell.rememberIsLandscape
import dev.clankyard.app.shell.rememberWorkshopSizeClass
import dev.clankyard.app.shell.toWorkshopShortcut
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.restoreSizeClass
import dev.clankyard.core.ui.theme.ClankyardTheme
import dev.clankyard.core.ui.theme.WorkshopTheme
import dev.clankyard.core.ui.theme.WorkshopWindowSurface
import dev.clankyard.editor.CodeEditorController
import dev.clankyard.editor.EditorSession
import dev.clankyard.editor.OpenDocument
import dev.clankyard.feature.clanker.ClankerPane
import dev.clankyard.feature.explorer.ExplorerUiState
import dev.clankyard.feature.explorer.ExplorerViewModel
import dev.clankyard.feature.git.GitScreen
import dev.clankyard.feature.search.SearchUiState
import dev.clankyard.feature.search.SearchViewModel
import dev.clankyard.feature.settings.SettingsScreen
import dev.clankyard.feature.settings.WorkshopSettingsStore
import dev.clankyard.feature.terminal.TerminalPane
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val workspaceSession: WorkspaceSessionViewModel by viewModels()
    private val editorSession: EditorSessionViewModel by viewModels()
    private val onboarding: OnboardingViewModel by viewModels()
    @Inject lateinit var workshopSettings: WorkshopSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var selectedTheme by remember { mutableStateOf(workshopSettings.read().theme) }
            ClankyardTheme(theme = selectedTheme) {
                WorkshopWindowSurface(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            val shortcut = event.toWorkshopShortcut()
                                ?: return@onPreviewKeyEvent false
                            workspaceSession.onShortcut(shortcut)
                        },
                ) {
                    ClankyardApp(
                        workspaceSession = workspaceSession,
                        editorSession = editorSession,
                        onboarding = onboarding,
                        onThemeChanged = { selectedTheme = it },
                    )
                }
            }
        }
    }
}

@Composable
fun ClankyardApp(
    workspaceSession: WorkspaceSessionViewModel,
    editorSession: EditorSessionViewModel,
    onboarding: OnboardingViewModel,
    onThemeChanged: (WorkshopTheme) -> Unit = {},
) {
    val onboardingState by onboarding.state.collectAsStateWithLifecycle()
    val workspace by workspaceSession.workspace.collectAsStateWithLifecycle()
    val records by workspaceSession.records.collectAsStateWithLifecycle()
    LaunchedEffect(onboardingState.selectedWorkspaceId) {
        onboardingState.selectedWorkspaceId?.let(workspaceSession::openWorkspace)
    }
    if (onboardingState.visible) {
        OnboardingScreen(viewModel = onboarding)
        return
    }
    val current = workspace
    if (current == null) {
        WorkshopPickerScreen(
            records = records,
            onOpen = workspaceSession::openWorkspace,
            onCreate = workspaceSession::createWorkshop,
        )
        return
    }
    WorkshopScreen(
        workspaceId = current.id,
        workshopName = current.displayName,
        workspaceSession = workspaceSession,
        editorSession = editorSession,
        onThemeChanged = onThemeChanged,
    )
}

@Composable
private fun WorkshopScreen(
    workspaceId: WorkspaceId,
    workshopName: String,
    workspaceSession: WorkspaceSessionViewModel,
    editorSession: EditorSessionViewModel,
    onThemeChanged: (WorkshopTheme) -> Unit,
) {
    val sizeClass = rememberWorkshopSizeClass()
    val landscape = rememberIsLandscape()
    val heightCompact = rememberHeightCompact()
    val uiState by workspaceSession.uiState.collectAsStateWithLifecycle()
    val chromeState by workspaceSession.chrome.collectAsStateWithLifecycle()
    val compactDest by workspaceSession.compactDestination.collectAsStateWithLifecycle()
    val palette by workspaceSession.palette.collectAsStateWithLifecycle()
    val fileQuery by workspaceSession.fileQuery.collectAsStateWithLifecycle()
    val fileHits by workspaceSession.fileHits.collectAsStateWithLifecycle()
    val explorerVm by workspaceSession.explorer.collectAsStateWithLifecycle()
    val searchVm by workspaceSession.search.collectAsStateWithLifecycle()
    val gitVm by workspaceSession.git.collectAsStateWithLifecycle()
    val clankerVm by workspaceSession.clanker.collectAsStateWithLifecycle()
    val terminal by workspaceSession.terminal.collectAsStateWithLifecycle()
    val settingsVm by workspaceSession.settings.collectAsStateWithLifecycle()
    val session by editorSession.session.collectAsStateWithLifecycle()
    val explorerState = collectExplorer(explorerVm)
    val searchState = collectSearch(searchVm)
    val documentsMap = collectDocuments(session)
    val activeFromSession = collectActivePath(session)
    val dirty = collectDirty(session)
    val controller = remember { CodeEditorController() }
    val chrome = chromeState
        ?.takeIf { it.to == sizeClass }
        ?: restoreSizeClass(uiState.lastSizeClass, sizeClass, uiState)
    val activePath = activeFromSession ?: uiState.activePath
    val documents = uiState.tabs.mapNotNull { documentsMap[it.path] }
        .ifEmpty { documentsMap.values.toList() }

    LaunchedEffect(sizeClass) { workspaceSession.onSizeClass(sizeClass) }
    LaunchedEffect(workspaceId) { workspaceSession.attachEditor(editorSession) }
    LaunchedEffect(controller) { workspaceSession.editorController = controller }

    AdaptiveShell(
        state = AdaptiveShellState(
            sizeClass = sizeClass,
            landscape = landscape,
            heightCompact = heightCompact,
            chrome = chrome,
            workshopName = workshopName,
            compactDestination = compactDest,
            explorerState = explorerState,
            documents = documents,
            tabs = uiState.tabs,
            activePath = activePath,
            searchState = searchState,
            palette = palette,
            fileQuery = fileQuery,
            fileHits = fileHits,
            dirty = dirty.isNotEmpty(),
        ),
        controller = controller,
        onCompactNavigate = workspaceSession::navigateCompact,
        onExplorer = workspaceSession::onExplorer,
        onSearch = workspaceSession::onSearch,
        onOpenPath = { workspaceSession.openPath(it) },
        onSelectTab = workspaceSession::selectTab,
        onCloseTab = workspaceSession::closeTab,
        onEdit = workspaceSession::edit,
        onCursor = workspaceSession::onCursor,
        onWeights = workspaceSession::onWeights,
        onBottomTab = workspaceSession::setBottomTab,
        onToggleFiles = workspaceSession::toggleFiles,
        onToggleClanker = workspaceSession::toggleClanker,
        onToggleBottom = workspaceSession::toggleBottom,
        onSave = workspaceSession::saveActive,
        onOpenPalette = workspaceSession::openPalette,
        onDismissPalette = {
            clankerVm?.refreshKeyFlag()
            workspaceSession.dismissPalette()
        },
        onFileQuery = workspaceSession::setFileQuery,
        onCommand = workspaceSession::runCommand,
        onCloseWorkspace = workspaceSession::closeWorkspace,
        clankerContent = { modifier -> ClankerPane(clankerVm, modifier) },
        terminalContent = { modifier -> TerminalPane(terminal, modifier) },
        gitContent = { modifier ->
            if (gitVm != null) GitScreen(gitVm!!, modifier)
            else FeaturePlaceholder("Git", "Open a workshop.", modifier)
        },
        settingsContent = { onDismiss ->
            val vm = settingsVm
            if (vm != null) {
                SettingsScreen(vm, onDismiss = {
                    clankerVm?.refreshKeyFlag()
                    onDismiss()
                }, onThemeChanged = onThemeChanged)
            } else {
                FeaturePlaceholder("Settings", "Unavailable", Modifier)
            }
        },
    )
}

@Composable
private fun collectExplorer(vm: ExplorerViewModel?): ExplorerUiState {
    val fallback = remember { MutableStateFlow(ExplorerUiState()) }
    val flow: StateFlow<ExplorerUiState> = vm?.state ?: fallback
    val value by flow.collectAsStateWithLifecycle()
    return value
}

@Composable
private fun collectSearch(vm: SearchViewModel?): SearchUiState {
    val fallback = remember { MutableStateFlow(SearchUiState()) }
    val flow: StateFlow<SearchUiState> = vm?.state ?: fallback
    val value by flow.collectAsStateWithLifecycle()
    return value
}

@Composable
private fun collectDocuments(session: EditorSession?): Map<WorkspacePath, OpenDocument> {
    val fallback = remember { MutableStateFlow(emptyMap<WorkspacePath, OpenDocument>()) }
    val flow = session?.documents ?: fallback
    val value by flow.collectAsStateWithLifecycle()
    return value
}

@Composable
private fun collectActivePath(session: EditorSession?): WorkspacePath? {
    val fallback = remember { MutableStateFlow<WorkspacePath?>(null) }
    val flow = session?.activePath ?: fallback
    val value by flow.collectAsStateWithLifecycle()
    return value
}

@Composable
private fun collectDirty(session: EditorSession?): Set<WorkspacePath> {
    val fallback = remember { MutableStateFlow(emptySet<WorkspacePath>()) }
    val flow = session?.dirty ?: fallback
    val value by flow.collectAsStateWithLifecycle()
    return value
}
