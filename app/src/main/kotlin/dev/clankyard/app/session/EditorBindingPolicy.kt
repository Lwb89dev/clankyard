package dev.clankyard.app.session

import dev.clankyard.core.model.WorkspaceId

/**
 * Compact destinations swap chrome only. Bind key is workspace id, never the
 * selected destination — opening Clanker must not dispose EditorSession.
 */
object EditorBindingPolicy {
    fun shouldBind(currentlyBound: WorkspaceId?, workspaceId: WorkspaceId): Boolean =
        currentlyBound != workspaceId
}
