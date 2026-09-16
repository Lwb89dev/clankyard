package dev.clankyard.core.ui

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.proto.WorkspaceUiStateProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceUiStateMapperTest {
    @Test
    fun defaultProtoMapsToKotlinDefaults() {
        val model = WorkspaceUiStateProto.getDefaultInstance().toModel()
        assertEquals(WorkspaceUiState(), model)
        assertNull(model.workspaceId)
        assertTrue(model.clankerVisible)
        assertTrue(model.bottomCollapsed)
        assertEquals(0.22f, model.filesWeight, 0.0f)
        assertEquals(BottomTab.Terminal, model.bottomTab)
        assertEquals(SizeClass.Compact, model.lastSizeClass)
    }

    @Test
    fun populatedStateRoundTrips() {
        val original = WorkspaceUiState(
            workspaceId = WorkspaceId("ws-1"),
            tabs = listOf(
                OpenTab(WorkspacePath.parse("src/Main.kt"), cursorLine = 12, cursorCol = 4),
                OpenTab(WorkspacePath.parse("README.md"), cursorLine = 1, cursorCol = 0),
            ),
            activePath = WorkspacePath.parse("src/Main.kt"),
            filesWeight = 0.2f,
            editorWeight = 0.55f,
            clankerWeight = 0.25f,
            bottomWeight = 0.3f,
            filesCollapsed = true,
            clankerVisible = false,
            bottomCollapsed = false,
            bottomTab = BottomTab.Git,
            lastSizeClass = SizeClass.Expanded,
        )
        val restored = original.toProto().toModel()
        assertEquals(original, restored)
    }

    @Test
    fun protoBytesRoundTrip() {
        val original = WorkspaceUiState(
            workspaceId = WorkspaceId("ws-bytes"),
            tabs = listOf(OpenTab(WorkspacePath.parse("a/b.txt"), 3, 1)),
            activePath = WorkspacePath.parse("a/b.txt"),
            lastSizeClass = SizeClass.Medium,
        )
        val bytes = original.toProto().toByteArray()
        val restored = WorkspaceUiStateProto.parseFrom(bytes).toModel()
        assertEquals(original, restored)
    }
}
