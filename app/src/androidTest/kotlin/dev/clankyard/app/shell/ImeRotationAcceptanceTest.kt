package dev.clankyard.app.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.OpenTab
import dev.clankyard.core.ui.SizeClass
import dev.clankyard.core.ui.WorkspaceUiState
import dev.clankyard.core.ui.restoreSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CLANK-017 IME / rotation / edge-to-edge acceptance.
 *
 * Automated:
 * - Size-class restore mapping (this class + [dev.clankyard.core.ui.SizeClassMappingTest])
 * - Expanded three-pane + Compact nav: [AdaptiveShellInstrumentedTest]
 *
 * Wiring in the shell:
 * - Root scaffold pads WindowInsets.safeDrawing excluding IME
 * - sora uses [dev.clankyard.editor.codeEditorImePadding] so the caret is not
 *   under the keyboard; MainActivity uses windowSoftInputMode=adjustResize
 * - Compact bottom nav is hidden while IME is visible
 *
 * Manual device checks (API 29 and 36, tablet + phone):
 * 1. Compact Editor: open the IME. The sora caret stays above the keyboard.
 *    Tabs do not sit under the nav bar or caption bar.
 * 2. Rotate Compact ↔ Expanded. Open tabs, cursor line/col, pane weights,
 *    clankerVisible, and bottomCollapsed restore from DataStore WorkspaceUiState.
 *    EditorSession is Activity-retained; opening Clanker does not drop dirty buffers.
 * 3. Process death: tabs restore from DataStore; Clanker transcript does not.
 */
@RunWith(AndroidJUnit4::class)
class ImeRotationAcceptanceTest {
    @Test
    fun rotationRestoreKeepsTabsAndDoesNotShowWeightsOnCompact() {
        val stored = WorkspaceUiState(
            tabs = listOf(OpenTab(WorkspacePath.parse("Main.kt"), cursorLine = 4, cursorCol = 1)),
            clankerVisible = true,
            bottomCollapsed = false,
            filesWeight = 0.22f,
            editorWeight = 0.5f,
            clankerWeight = 0.28f,
        )
        val toCompact = restoreSizeClass(SizeClass.Expanded, SizeClass.Compact, stored)
        assertTrue(toCompact.editorAsDestination)
        assertFalse(toCompact.showWeights)
        assertEquals(stored.tabs, toCompact.tabs)
        val back = restoreSizeClass(SizeClass.Compact, SizeClass.Expanded, stored)
        assertTrue(back.applyStoredWeights)
        assertTrue(back.clankerDocked)
        assertFalse(back.bottomCollapsed)
        assertEquals(stored.tabs, back.tabs)
    }
}
