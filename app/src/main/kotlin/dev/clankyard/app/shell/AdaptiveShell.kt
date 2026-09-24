package dev.clankyard.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.clankyard.app.session.CompactDestination
import dev.clankyard.app.session.WorkshopCommand
import dev.clankyard.app.session.WorkshopPaletteKind
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.BottomTab
import dev.clankyard.core.ui.OpenTab
import dev.clankyard.core.ui.SizeClass
import dev.clankyard.core.ui.SizeClassRestore
import dev.clankyard.core.ui.WorkshopLayout
import dev.clankyard.core.ui.WorkshopSemantics
import dev.clankyard.core.ui.isHeightCompact
import dev.clankyard.core.ui.sizeClassFromWidthDp
import dev.clankyard.core.ui.theme.PathTextStyle
import dev.clankyard.core.ui.theme.WorkshopWindowSurface
import dev.clankyard.editor.CodeEditorController
import dev.clankyard.editor.OpenDocument
import dev.clankyard.feature.explorer.ExplorerPane
import dev.clankyard.feature.explorer.ExplorerUiEvent
import dev.clankyard.feature.explorer.ExplorerUiState
import dev.clankyard.feature.search.SearchUiEvent
import dev.clankyard.feature.search.SearchUiState

data class AdaptiveShellState(
    val sizeClass: SizeClass,
    val heightCompact: Boolean,
    val chrome: SizeClassRestore,
    val workshopName: String,
    val compactDestination: CompactDestination,
    val explorerState: ExplorerUiState,
    val documents: List<OpenDocument>,
    val tabs: List<OpenTab>,
    val activePath: WorkspacePath?,
    val searchState: SearchUiState,
    val palette: WorkshopPaletteKind,
    val fileQuery: String,
    val fileHits: List<WorkspacePath>,
    val dirty: Boolean,
)

@Composable
fun rememberWorkshopSizeClass(): SizeClass {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val widthDp = with(density) { size.width.toDp() }
    return sizeClassFromWidthDp(widthDp.value)
}

@Composable
fun rememberHeightCompact(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val heightDp = with(density) { size.height.toDp() }
    return isHeightCompact(heightDp.value)
}

@Composable
fun AdaptiveShell(
    state: AdaptiveShellState,
    controller: CodeEditorController,
    onCompactNavigate: (CompactDestination) -> Unit,
    onExplorer: (ExplorerUiEvent) -> Unit,
    onSearch: (SearchUiEvent) -> Unit,
    onOpenPath: (WorkspacePath) -> Unit,
    onSelectTab: (WorkspacePath) -> Unit,
    onCloseTab: (WorkspacePath) -> Unit,
    onEdit: (WorkspacePath, String) -> Unit,
    onCursor: (WorkspacePath, Int, Int) -> Unit,
    onWeights: (Float, Float, Float, Float) -> Unit,
    onBottomTab: (BottomTab) -> Unit,
    onToggleFiles: () -> Unit,
    onToggleClanker: () -> Unit,
    onToggleBottom: () -> Unit,
    onSave: () -> Unit,
    onOpenPalette: (WorkshopPaletteKind) -> Unit,
    onDismissPalette: () -> Unit,
    onFileQuery: (String) -> Unit,
    onCommand: (WorkshopCommand) -> Unit,
    onCloseWorkspace: () -> Unit,
    clankerContent: @Composable (Modifier) -> Unit = { ClankerPlaceholder(it) },
    terminalContent: @Composable (Modifier) -> Unit = { FeaturePlaceholder("Terminal", "Sandbox shell", it) },
    gitContent: @Composable (Modifier) -> Unit = { FeaturePlaceholder("Git", "Local git", it) },
    settingsContent: @Composable (() -> Unit) -> Unit = { onDismiss -> SettingsPlaceholder(onDismiss) },
) {
    val useRail = state.sizeClass == SizeClass.Compact && state.heightCompact
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime)),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            WorkshopTopBar(
                name = state.workshopName,
                dirty = state.dirty,
                sizeClass = state.sizeClass,
                onSave = onSave,
                onToggleFiles = onToggleFiles,
                onToggleClanker = onToggleClanker,
                onToggleBottom = onToggleBottom,
                onSettings = { onOpenPalette(WorkshopPaletteKind.Settings) },
                onCloseWorkspace = onCloseWorkspace,
            )
        },
        bottomBar = {
            if (state.sizeClass == SizeClass.Compact && !useRail) {
                CompactBottomNav(state.compactDestination, onCompactNavigate)
            }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                WorkshopBody(
                    state = state,
                    controller = controller,
                    useRail = useRail,
                    onCompactNavigate = onCompactNavigate,
                    onExplorer = onExplorer,
                    onSelectTab = onSelectTab,
                    onCloseTab = onCloseTab,
                    onEdit = onEdit,
                    onCursor = onCursor,
                    onWeights = onWeights,
                    onBottomTab = onBottomTab,
                    clankerContent = clankerContent,
                    terminalContent = terminalContent,
                    gitContent = gitContent,
                )
                WorkshopOverlays(
                    state = state,
                    onSearch = onSearch,
                    onOpenPath = onOpenPath,
                    onDismissPalette = onDismissPalette,
                    onFileQuery = onFileQuery,
                    onCommand = onCommand,
                    settingsContent = settingsContent,
                )
            }
        }
    }
}

@Composable
private fun WorkshopBody(
    state: AdaptiveShellState,
    controller: CodeEditorController,
    useRail: Boolean,
    onCompactNavigate: (CompactDestination) -> Unit,
    onExplorer: (ExplorerUiEvent) -> Unit,
    onSelectTab: (WorkspacePath) -> Unit,
    onCloseTab: (WorkspacePath) -> Unit,
    onEdit: (WorkspacePath, String) -> Unit,
    onCursor: (WorkspacePath, Int, Int) -> Unit,
    onWeights: (Float, Float, Float, Float) -> Unit,
    onBottomTab: (BottomTab) -> Unit,
    clankerContent: @Composable (Modifier) -> Unit,
    terminalContent: @Composable (Modifier) -> Unit,
    gitContent: @Composable (Modifier) -> Unit,
) {
    when (state.chrome.layout) {
        WorkshopLayout.CompactDestinations -> CompactBody(
            state = state,
            controller = controller,
            useRail = useRail,
            onCompactNavigate = onCompactNavigate,
            onExplorer = onExplorer,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onEdit = onEdit,
            onCursor = onCursor,
            clankerContent = clankerContent,
            terminalContent = terminalContent,
            gitContent = gitContent,
        )
        WorkshopLayout.MediumListDetail -> MediumBody(
            state = state,
            controller = controller,
            onExplorer = onExplorer,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onEdit = onEdit,
            onCursor = onCursor,
            onWeights = onWeights,
            onBottomTab = onBottomTab,
            clankerContent = clankerContent,
            terminalContent = terminalContent,
            gitContent = gitContent,
        )
        WorkshopLayout.ExpandedThreePane -> ExpandedBody(
            state = state,
            controller = controller,
            onExplorer = onExplorer,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onEdit = onEdit,
            onCursor = onCursor,
            onWeights = onWeights,
            onBottomTab = onBottomTab,
            clankerContent = clankerContent,
            terminalContent = terminalContent,
            gitContent = gitContent,
        )
    }
}

@Composable
private fun CompactBody(
    state: AdaptiveShellState,
    controller: CodeEditorController,
    useRail: Boolean,
    onCompactNavigate: (CompactDestination) -> Unit,
    onExplorer: (ExplorerUiEvent) -> Unit,
    onSelectTab: (WorkspacePath) -> Unit,
    onCloseTab: (WorkspacePath) -> Unit,
    onEdit: (WorkspacePath, String) -> Unit,
    onCursor: (WorkspacePath, Int, Int) -> Unit,
    clankerContent: @Composable (Modifier) -> Unit,
    terminalContent: @Composable (Modifier) -> Unit,
    gitContent: @Composable (Modifier) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        if (useRail) CompactNavRail(state.compactDestination, onCompactNavigate)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val showEditor = state.compactDestination == CompactDestination.Editor
            if (showEditor) WorkshopWindowSurface(Modifier.fillMaxSize()) {}
            // Keep the editor composed so changing destinations cannot drop the
            // active buffer, but remove it from the layout while hidden.
            EditorHost(
                documents = state.documents,
                tabs = state.tabs,
                activePath = state.activePath,
                controller = controller,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onEdit = onEdit,
                onCursor = onCursor,
                modifier = if (showEditor) Modifier.fillMaxSize() else Modifier.size(0.dp),
            )
            if (!showEditor) {
                WorkshopWindowSurface(Modifier.fillMaxSize()) {
                    CompactDestinationPane(
                        state,
                        onExplorer,
                        clankerContent,
                        terminalContent,
                        gitContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactDestinationPane(
    state: AdaptiveShellState,
    onExplorer: (ExplorerUiEvent) -> Unit,
    clankerContent: @Composable (Modifier) -> Unit,
    terminalContent: @Composable (Modifier) -> Unit,
    gitContent: @Composable (Modifier) -> Unit,
) {
    when (state.compactDestination) {
        CompactDestination.Editor -> Unit
        CompactDestination.Files -> ExplorerPane(
            state = state.explorerState,
            onEvent = onExplorer,
            modifier = Modifier.semantics { contentDescription = WorkshopSemantics.FILES_PANE },
        )
        CompactDestination.Clanker -> clankerContent(Modifier.fillMaxSize())
        CompactDestination.Terminal -> terminalContent(Modifier.fillMaxSize())
        CompactDestination.Git -> gitContent(Modifier.fillMaxSize())
    }
}

@Composable
private fun MediumBody(
    state: AdaptiveShellState,
    controller: CodeEditorController,
    onExplorer: (ExplorerUiEvent) -> Unit,
    onSelectTab: (WorkspacePath) -> Unit,
    onCloseTab: (WorkspacePath) -> Unit,
    onEdit: (WorkspacePath, String) -> Unit,
    onCursor: (WorkspacePath, Int, Int) -> Unit,
    onWeights: (Float, Float, Float, Float) -> Unit,
    onBottomTab: (BottomTab) -> Unit,
    clankerContent: @Composable (Modifier) -> Unit,
    terminalContent: @Composable (Modifier) -> Unit,
    gitContent: @Composable (Modifier) -> Unit,
) {
    var filesW by remember(state.chrome.filesWeight) { mutableFloatStateOf(state.chrome.filesWeight) }
    var editorW by remember(state.chrome.editorWeight) { mutableFloatStateOf(state.chrome.editorWeight) }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (!state.chrome.filesCollapsed) {
                WorkshopWindowSurface(
                    modifier = Modifier
                        .weight(filesW.coerceAtLeast(0.12f))
                        .widthIn(min = 140.dp),
                ) {
                    ExplorerPane(
                        state = state.explorerState,
                        onEvent = onExplorer,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = WorkshopSemantics.FILES_PANE },
                    )
                }
                VerticalPaneHandle(
                    contentDescription = WorkshopSemantics.HANDLE_FILES,
                    onDrag = { dx ->
                        val pair = shiftWeight(filesW, editorW, dx)
                        filesW = pair.first
                        editorW = pair.second
                    },
                    onDragEnd = { onWeights(filesW, editorW, state.chrome.clankerWeight, state.chrome.bottomWeight) },
                )
            }
            WorkshopWindowSurface(Modifier.weight(1f)) {
                EditorColumn(
                    state = state,
                    controller = controller,
                    bottomWeight = state.chrome.bottomWeight,
                    onSelectTab = onSelectTab,
                    onCloseTab = onCloseTab,
                    onEdit = onEdit,
                    onCursor = onCursor,
                    onWeights = { b -> onWeights(filesW, editorW, state.chrome.clankerWeight, b) },
                    onBottomTab = onBottomTab,
                    modifier = Modifier.fillMaxSize(),
                    terminalContent = terminalContent,
                    gitContent = gitContent,
                )
            }
        }
        if (state.chrome.clankerOverlay) {
            WorkshopWindowSurface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 280.dp, max = 420.dp)
                    .align(Alignment.CenterEnd),
                tonalElevation = 4.dp,
            ) {
                clankerContent(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ExpandedBody(
    state: AdaptiveShellState,
    controller: CodeEditorController,
    onExplorer: (ExplorerUiEvent) -> Unit,
    onSelectTab: (WorkspacePath) -> Unit,
    onCloseTab: (WorkspacePath) -> Unit,
    onEdit: (WorkspacePath, String) -> Unit,
    onCursor: (WorkspacePath, Int, Int) -> Unit,
    onWeights: (Float, Float, Float, Float) -> Unit,
    onBottomTab: (BottomTab) -> Unit,
    clankerContent: @Composable (Modifier) -> Unit,
    terminalContent: @Composable (Modifier) -> Unit,
    gitContent: @Composable (Modifier) -> Unit,
) {
    var filesW by remember(state.chrome.filesWeight) { mutableFloatStateOf(state.chrome.filesWeight) }
    var editorW by remember(state.chrome.editorWeight) { mutableFloatStateOf(state.chrome.editorWeight) }
    var clankerW by remember(state.chrome.clankerWeight) { mutableFloatStateOf(state.chrome.clankerWeight) }
    var bottomW by remember(state.chrome.bottomWeight) { mutableFloatStateOf(state.chrome.bottomWeight) }
    var rowWidth by remember { mutableFloatStateOf(1f) }
    Row(Modifier.fillMaxSize().onSizeChanged { rowWidth = it.width.toFloat().coerceAtLeast(1f) }) {
        if (!state.chrome.filesCollapsed) {
            WorkshopWindowSurface(
                modifier = Modifier
                    .weight(filesW.coerceAtLeast(0.12f))
                    .widthIn(min = 140.dp),
            ) {
                ExplorerPane(
                    state = state.explorerState,
                    onEvent = onExplorer,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = WorkshopSemantics.FILES_PANE },
                )
            }
            VerticalPaneHandle(
                contentDescription = WorkshopSemantics.HANDLE_FILES,
                onDrag = { dx ->
                    val pair = shiftWeight(filesW, editorW, dx / rowWidth)
                    filesW = pair.first
                    editorW = pair.second
                },
                onDragEnd = { onWeights(filesW, editorW, clankerW, bottomW) },
            )
        }
        WorkshopWindowSurface(
            modifier = Modifier
                .weight(editorW.coerceAtLeast(0.3f))
                .widthIn(min = 240.dp),
        ) {
            EditorColumn(
                state = state,
                controller = controller,
                bottomWeight = bottomW,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onEdit = onEdit,
                onCursor = onCursor,
                onWeights = { b ->
                    bottomW = b
                    onWeights(filesW, editorW, clankerW, b)
                },
                onBottomTab = onBottomTab,
                onBottomDrag = { dy, colH ->
                    val delta = -dy / colH
                    bottomW = (bottomW + delta).coerceIn(0.12f, 0.6f)
                },
                modifier = Modifier.fillMaxSize(),
                terminalContent = terminalContent,
                gitContent = gitContent,
            )
        }
        if (state.chrome.clankerDocked || state.chrome.clankerVisible) {
            VerticalPaneHandle(
                contentDescription = WorkshopSemantics.HANDLE_CLANKER,
                onDrag = { dx ->
                    val pair = shiftWeight(editorW, clankerW, dx / rowWidth)
                    editorW = pair.first
                    clankerW = pair.second
                },
                onDragEnd = { onWeights(filesW, editorW, clankerW, bottomW) },
            )
            WorkshopWindowSurface(
                modifier = Modifier
                    .weight(clankerW.coerceAtLeast(0.12f))
                    .widthIn(min = 160.dp),
            ) {
                clankerContent(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun EditorColumn(
    state: AdaptiveShellState,
    controller: CodeEditorController,
    bottomWeight: Float,
    onSelectTab: (WorkspacePath) -> Unit,
    onCloseTab: (WorkspacePath) -> Unit,
    onEdit: (WorkspacePath, String) -> Unit,
    onCursor: (WorkspacePath, Int, Int) -> Unit,
    onWeights: (Float) -> Unit,
    onBottomTab: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
    onBottomDrag: ((Float, Float) -> Unit)? = null,
    terminalContent: @Composable (Modifier) -> Unit = {},
    gitContent: @Composable (Modifier) -> Unit = {},
) {
    var colH by remember { mutableFloatStateOf(1f) }
    Column(modifier.fillMaxSize().onSizeChanged { colH = it.height.toFloat().coerceAtLeast(1f) }) {
        EditorHost(
            documents = state.documents,
            tabs = state.tabs,
            activePath = state.activePath,
            controller = controller,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onEdit = onEdit,
            onCursor = onCursor,
            modifier = Modifier.weight(1f),
        )
        if (!state.chrome.bottomCollapsed) {
            HorizontalPaneHandle(
                contentDescription = WorkshopSemantics.HANDLE_BOTTOM,
                onDrag = { dy -> onBottomDrag?.invoke(dy, colH) },
                onDragEnd = { onWeights(bottomWeight) },
            )
            BottomToolsPane(
                selected = state.chrome.bottomTab,
                onSelect = onBottomTab,
                modifier = Modifier.weight(bottomWeight.coerceAtLeast(0.12f)),
                terminalContent = terminalContent,
                gitContent = gitContent,
            )
        }
    }
}

@Composable
private fun WorkshopOverlays(
    state: AdaptiveShellState,
    onSearch: (SearchUiEvent) -> Unit,
    onOpenPath: (WorkspacePath) -> Unit,
    onDismissPalette: () -> Unit,
    onFileQuery: (String) -> Unit,
    onCommand: (WorkshopCommand) -> Unit,
    settingsContent: @Composable (() -> Unit) -> Unit,
) {
    when (state.palette) {
        WorkshopPaletteKind.None -> Unit
        WorkshopPaletteKind.File -> FilePaletteDialog(
            query = state.fileQuery,
            hits = state.fileHits,
            onQuery = onFileQuery,
            onPick = onOpenPath,
            onDismiss = onDismissPalette,
        )
        WorkshopPaletteKind.Command -> CommandPaletteDialog(
            onCommand = onCommand,
            onDismiss = onDismissPalette,
        )
        WorkshopPaletteKind.Search -> SearchPaletteDialog(
            state = state.searchState,
            onEvent = onSearch,
            onDismiss = onDismissPalette,
        )
        WorkshopPaletteKind.Settings -> settingsContent(onDismissPalette)
    }
}

@Composable
private fun WorkshopTopBar(
    name: String,
    dirty: Boolean,
    sizeClass: SizeClass,
    onSave: () -> Unit,
    onToggleFiles: () -> Unit,
    onToggleClanker: () -> Unit,
    onToggleBottom: () -> Unit,
    onSettings: () -> Unit,
    onCloseWorkspace: () -> Unit,
) {
    var confirmClose by remember { mutableStateOf(false) }
    val mark = if (dirty) "• " else ""
    WorkshopWindowSurface {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$mark$name", style = PathTextStyle, modifier = Modifier.weight(1f))
            TextButton(onClick = onSave) { Text("Save") }
            if (sizeClass != SizeClass.Compact) {
                TextButton(onClick = onToggleFiles) { Text("Files") }
                TextButton(onClick = onToggleClanker) { Text("Clanker") }
                TextButton(onClick = onToggleBottom) { Text("Panel") }
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.semantics { contentDescription = WorkshopSemantics.SETTINGS_BUTTON },
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = { confirmClose = true },
                modifier = Modifier.semantics { contentDescription = WorkshopSemantics.CLOSE_WORKSHOP },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                )
            }
        }
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close workshop") },
            text = {
                Text("Leave this workshop? Unsaved editor buffers stay in drafts on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClose = false
                        onCloseWorkspace()
                    },
                ) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text("Stay") }
            },
        )
    }
}

@Composable
private fun CompactBottomNav(
    selected: CompactDestination,
    onNavigate: (CompactDestination) -> Unit,
) {
    WorkshopWindowSurface {
        NavigationBar(
            // The gradient underneath can be too close to the item colors in
            // themed variants, so the destination switcher gets its own surface.
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            compactDestinations().forEach { dest ->
                NavigationBarItem(
                    selected = dest == selected,
                    onClick = { onNavigate(dest) },
                    icon = { Text(dest.name.take(1)) },
                    label = { Text(dest.displayName()) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.semantics { contentDescription = dest.contentDescription() },
                )
            }
        }
    }
}

@Composable
private fun CompactNavRail(
    selected: CompactDestination,
    onNavigate: (CompactDestination) -> Unit,
) {
    WorkshopWindowSurface {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            compactDestinations().forEach { dest ->
                NavigationRailItem(
                    selected = dest == selected,
                    onClick = { onNavigate(dest) },
                    icon = { Text(dest.name.take(1)) },
                    label = { Text(dest.displayName()) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.semantics { contentDescription = dest.contentDescription() },
                )
            }
        }
    }
}

private fun compactDestinations(): List<CompactDestination> = listOf(
    CompactDestination.Editor,
    CompactDestination.Files,
    CompactDestination.Clanker,
    CompactDestination.Terminal,
    CompactDestination.Git,
)

private fun CompactDestination.contentDescription(): String = when (this) {
    CompactDestination.Editor -> WorkshopSemantics.NAV_EDITOR
    CompactDestination.Files -> WorkshopSemantics.NAV_FILES
    CompactDestination.Clanker -> WorkshopSemantics.NAV_CLANKER
    CompactDestination.Terminal -> WorkshopSemantics.NAV_TERMINAL
    CompactDestination.Git -> WorkshopSemantics.NAV_GIT
}

private fun CompactDestination.displayName(): String = when (this) {
    CompactDestination.Editor -> "Editor"
    CompactDestination.Files -> "Files"
    CompactDestination.Clanker -> "AI chat"
    CompactDestination.Terminal -> "Terminal"
    CompactDestination.Git -> "Git"
}

private fun shiftWeight(left: Float, right: Float, delta: Float): Pair<Float, Float> {
    val nextLeft = (left + delta).coerceIn(0.12f, 0.7f)
    val consumed = nextLeft - left
    val nextRight = (right - consumed).coerceIn(0.12f, 0.8f)
    return nextLeft to nextRight
}
