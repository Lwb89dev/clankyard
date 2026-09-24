package dev.clankyard.app.session

import dev.clankyard.core.model.WorkspaceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorBindingPolicyTest {
    @Test
    fun sameWorkspaceDoesNotRebindWhenChromeChanges() {
        val id = WorkspaceId("ws-1")
        assertFalse(EditorBindingPolicy.shouldBind(currentlyBound = id, workspaceId = id))
    }

    @Test
    fun newWorkspaceRebinds() {
        assertTrue(
            EditorBindingPolicy.shouldBind(
                currentlyBound = WorkspaceId("old"),
                workspaceId = WorkspaceId("new"),
            ),
        )
        assertTrue(
            EditorBindingPolicy.shouldBind(
                currentlyBound = null,
                workspaceId = WorkspaceId("new"),
            ),
        )
    }

    @Test
    fun compactDestinationIsNotPartOfBindKey() {
        val id = WorkspaceId("ws-1")
        CompactDestination.entries.forEach { _ ->
            assertFalse(EditorBindingPolicy.shouldBind(id, id))
        }
    }
}
