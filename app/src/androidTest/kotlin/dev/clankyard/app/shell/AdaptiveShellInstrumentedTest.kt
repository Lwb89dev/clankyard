package dev.clankyard.app.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.clankyard.app.session.CompactDestination
import dev.clankyard.app.session.WorkshopPaletteKind
import dev.clankyard.core.ui.SizeClass
import dev.clankyard.core.ui.WorkshopSemantics
import dev.clankyard.core.ui.WorkspaceUiState
import dev.clankyard.core.ui.restoreSizeClass
import dev.clankyard.core.ui.theme.ClankyardTheme
import dev.clankyard.editor.CodeEditorController
import dev.clankyard.feature.explorer.ExplorerUiState
import dev.clankyard.feature.search.SearchUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveShellInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun expandedShowsThreePanesAndDragHandles() {
        compose.setContent {
            ClankyardTheme {
                AdaptiveShell(
                    state = shellState(SizeClass.Expanded, CompactDestination.Editor),
                    controller = remember { CodeEditorController() },
                    onCompactNavigate = {},
                    onExplorer = {},
                    onSearch = {},
                    onOpenPath = {},
                    onSelectTab = {},
                    onCloseTab = {},
                    onEdit = { _, _ -> },
                    onCursor = { _, _, _ -> },
                    onWeights = { _, _, _, _ -> },
                    onBottomTab = {},
                    onToggleFiles = {},
                    onToggleClanker = {},
                    onToggleBottom = {},
                    onSave = {},
                    onOpenPalette = {},
                    onDismissPalette = {},
                    onFileQuery = {},
                    onCommand = {},
                    onCloseWorkspace = {},
                )
            }
        }
        compose.onNodeWithContentDescription(WorkshopSemantics.FILES_PANE).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.EDITOR_PANE).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.CLANKER_PANE).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.HANDLE_FILES).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.HANDLE_CLANKER).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.HANDLE_BOTTOM).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.BOTTOM_PANE).assertExists()
    }

    @Test
    fun compactOpeningClankerKeepsEditorSemantics() {
        compose.setContent {
            ClankyardTheme {
                var dest by remember { mutableStateOf(CompactDestination.Editor) }
                AdaptiveShell(
                    state = shellState(SizeClass.Compact, dest),
                    controller = remember { CodeEditorController() },
                    onCompactNavigate = { dest = it },
                    onExplorer = {},
                    onSearch = {},
                    onOpenPath = {},
                    onSelectTab = {},
                    onCloseTab = {},
                    onEdit = { _, _ -> },
                    onCursor = { _, _, _ -> },
                    onWeights = { _, _, _, _ -> },
                    onBottomTab = {},
                    onToggleFiles = {},
                    onToggleClanker = {},
                    onToggleBottom = {},
                    onSave = {},
                    onOpenPalette = {},
                    onDismissPalette = {},
                    onFileQuery = {},
                    onCommand = {},
                    onCloseWorkspace = {},
                )
            }
        }
        compose.onNodeWithContentDescription(WorkshopSemantics.NAV_EDITOR).assertIsDisplayed()
        compose.onNodeWithContentDescription(WorkshopSemantics.NAV_FILES).assertIsDisplayed()
        compose.onNodeWithContentDescription(WorkshopSemantics.NAV_CLANKER).assertIsDisplayed()
        compose.onNodeWithContentDescription(WorkshopSemantics.NAV_TERMINAL).assertIsDisplayed()
        compose.onNodeWithContentDescription(WorkshopSemantics.NAV_GIT).assertIsDisplayed()
        compose.onNodeWithContentDescription(WorkshopSemantics.EDITOR_PANE).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.NAV_CLANKER).performClick()
        compose.onNodeWithContentDescription(WorkshopSemantics.CLANKER_PANE).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.CLANKER_STILL).assertExists()
        compose.onNodeWithContentDescription(WorkshopSemantics.EDITOR_PANE).assertExists()
    }
}

private fun shellState(
    sizeClass: SizeClass,
    dest: CompactDestination,
): AdaptiveShellState {
    val stored = WorkspaceUiState(
        clankerVisible = true,
        bottomCollapsed = false,
        lastSizeClass = SizeClass.Expanded,
    )
    return AdaptiveShellState(
        sizeClass = sizeClass,
        heightCompact = false,
        chrome = restoreSizeClass(SizeClass.Expanded, sizeClass, stored),
        workshopName = "workshop",
        compactDestination = dest,
        explorerState = ExplorerUiState(),
        documents = emptyList(),
        tabs = emptyList(),
        activePath = null,
        searchState = SearchUiState(),
        palette = WorkshopPaletteKind.None,
        fileQuery = "",
        fileHits = emptyList(),
        dirty = false,
    )
}
