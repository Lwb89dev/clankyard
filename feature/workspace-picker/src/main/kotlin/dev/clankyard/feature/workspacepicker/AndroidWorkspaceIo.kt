package dev.clankyard.feature.workspacepicker

import android.net.Uri
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.workspace.TreeExportPlan
import java.io.File

interface AndroidWorkspaceIo {
    /** SAF tree → workshop copy. Calls [dev.clankyard.workspace.WorkshopTreeOps.requireFreeSpace]. Foreground progress. */
    suspend fun copyInFromTree(treeUri: Uri, onProgress: (copied: Long, total: Long) -> Unit): WorkspaceId

    /** If [file] is already under app-specific storage and O_EXCL-safe, open in-place (no copy). */
    suspend fun openInPlaceIfSafe(file: File): WorkspaceId?

    /**
     * Zip via ACTION_CREATE_DOCUMENT, suggested name `project.zip`.
     * The system picker handles name collision. We only write a URI we just received.
     */
    suspend fun exportZip(id: WorkspaceId, destUri: Uri)

    /** Preview counts for a granted OPEN_DOCUMENT_TREE. Never deletes extra dest files. */
    suspend fun planExportToTree(id: WorkspaceId, treeUri: Uri): TreeExportPlan

    /**
     * Write-back to a granted OPEN_DOCUMENT_TREE.
     * Overwrites M dest files; never deletes D extra dest files.
     * UI must confirm if M > 0 (and still show the plan if M == 0).
     */
    suspend fun exportToTree(id: WorkspaceId, treeUri: Uri): TreeExportPlan
}
