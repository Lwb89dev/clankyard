package dev.clankyard.core.ui

import dev.clankyard.core.model.WorkspacePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SizeClassMappingTest {
    private val tabs = listOf(
        OpenTab(WorkspacePath.parse("src/Main.kt"), cursorLine = 10, cursorCol = 2),
        OpenTab(WorkspacePath.parse("README.md"), cursorLine = 0, cursorCol = 0),
    )
    private val stored = WorkspaceUiState(
        tabs = tabs,
        activePath = WorkspacePath.parse("src/Main.kt"),
        filesWeight = 0.2f,
        editorWeight = 0.55f,
        clankerWeight = 0.25f,
        bottomWeight = 0.3f,
        filesCollapsed = false,
        clankerVisible = true,
        bottomCollapsed = false,
        bottomTab = BottomTab.Git,
        lastSizeClass = SizeClass.Expanded,
    )

    @Test
    fun widthBreakpointsMatchAdaptiveTable() {
        assertEquals(WindowWidthBucket.Compact, windowWidthBucket(599f))
        assertEquals(WindowWidthBucket.Medium, windowWidthBucket(600f))
        assertEquals(WindowWidthBucket.Medium, windowWidthBucket(839f))
        assertEquals(WindowWidthBucket.Expanded, windowWidthBucket(840f))
        assertEquals(WindowWidthBucket.Expanded, windowWidthBucket(1199f))
        assertEquals(WindowWidthBucket.Large, windowWidthBucket(1200f))
        assertEquals(WindowWidthBucket.Large, windowWidthBucket(1599f))
        assertEquals(WindowWidthBucket.ExtraLarge, windowWidthBucket(1600f))
        assertEquals(SizeClass.Compact, sizeClassFromWidthDp(400f))
        assertEquals(SizeClass.Medium, sizeClassFromWidthDp(700f))
        assertEquals(SizeClass.Expanded, sizeClassFromWidthDp(900f))
        assertEquals(SizeClass.Expanded, sizeClassFromWidthDp(1400f))
        assertEquals(SizeClass.Expanded, sizeClassFromWidthDp(2000f))
    }

    @Test
    fun expandedToCompactKeepsTabsAndStoresWeights() {
        val restore = restoreSizeClass(SizeClass.Expanded, SizeClass.Compact, stored)
        assertEquals(WorkshopLayout.CompactDestinations, restore.layout)
        assertEquals(tabs, restore.tabs)
        assertEquals(stored.activePath, restore.activePath)
        assertTrue(restore.filesAsDestination)
        assertTrue(restore.editorAsDestination)
        assertTrue(restore.clankerAsDestination)
        assertTrue(restore.bottomAsDestinations)
        assertFalse(restore.showWeights)
        assertFalse(restore.applyStoredWeights)
        assertFalse(restore.clankerDocked)
        assertEquals(0.2f, restore.filesWeight, 0f)
        assertEquals(0.55f, restore.editorWeight, 0f)
        assertTrue(restore.clankerVisible)
    }

    @Test
    fun compactToExpandedRestoresClankerAndBottomAndAppliesWeights() {
        val compactStored = stored.copy(lastSizeClass = SizeClass.Compact)
        val restore = restoreSizeClass(SizeClass.Compact, SizeClass.Expanded, compactStored)
        assertEquals(WorkshopLayout.ExpandedThreePane, restore.layout)
        assertEquals(tabs, restore.tabs)
        assertTrue(restore.applyStoredWeights)
        assertTrue(restore.showWeights)
        assertTrue(restore.clankerDocked)
        assertTrue(restore.clankerVisible)
        assertFalse(restore.bottomCollapsed)
        assertFalse(restore.clankerOverlay)
        assertEquals(0.2f, restore.filesWeight, 0f)
        assertEquals(0.55f, restore.editorWeight, 0f)
        assertEquals(0.25f, restore.clankerWeight, 0f)
        assertEquals(0.3f, restore.bottomWeight, 0f)
    }

    @Test
    fun compactToExpandedHidesClankerWhenNotVisible() {
        val hidden = stored.copy(clankerVisible = false)
        val restore = restoreSizeClass(SizeClass.Compact, SizeClass.Expanded, hidden)
        assertFalse(restore.clankerVisible)
        assertFalse(restore.clankerDocked)
        assertTrue(restore.applyStoredWeights)
    }

    @Test
    fun compactToExpandedKeepsBottomCollapsedWhenStored() {
        val collapsed = stored.copy(bottomCollapsed = true)
        val restore = restoreSizeClass(SizeClass.Compact, SizeClass.Expanded, collapsed)
        assertTrue(restore.bottomCollapsed)
    }

    @Test
    fun expandedToMediumOverlaysClankerAndCollapsesBottom() {
        val restore = restoreSizeClass(SizeClass.Expanded, SizeClass.Medium, stored)
        assertEquals(WorkshopLayout.MediumListDetail, restore.layout)
        assertTrue(restore.clankerOverlay)
        assertFalse(restore.clankerDocked)
        assertTrue(restore.bottomCollapsed)
        assertTrue(restore.showWeights)
        assertTrue(restore.applyStoredWeights)
        assertEquals(tabs, restore.tabs)
        assertFalse(restore.filesAsDestination)
    }

    @Test
    fun mediumToExpandedDocksClankerAndRestoresBottom() {
        val mediumStored = stored.copy(lastSizeClass = SizeClass.Medium, bottomCollapsed = false)
        val restore = restoreSizeClass(SizeClass.Medium, SizeClass.Expanded, mediumStored)
        assertEquals(WorkshopLayout.ExpandedThreePane, restore.layout)
        assertFalse(restore.clankerOverlay)
        assertTrue(restore.clankerDocked)
        assertFalse(restore.bottomCollapsed)
        assertTrue(restore.applyStoredWeights)
        assertEquals(0.2f, restore.filesWeight, 0f)
    }

    @Test
    fun mediumStayUsesStoredBottomFlag() {
        val restore = restoreSizeClass(
            SizeClass.Medium,
            SizeClass.Medium,
            stored.copy(bottomCollapsed = false),
        )
        assertFalse(restore.bottomCollapsed)
        assertTrue(restore.clankerOverlay)
    }

    @Test
    fun protoRoundTripDoesNotHoldClankerTranscript() {
        val restored = stored.toProto().toModel()
        assertEquals(stored.tabs, restored.tabs)
        assertEquals(stored.clankerVisible, restored.clankerVisible)
        assertEquals(stored.lastSizeClass, restored.lastSizeClass)
    }
}
