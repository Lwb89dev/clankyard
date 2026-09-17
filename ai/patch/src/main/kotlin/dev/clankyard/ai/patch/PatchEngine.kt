package dev.clankyard.ai.patch

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.PatchSetId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.FileBackedWorkspace

enum class EditKind { Create, Replace, Rename }

data class ProposedEdit(
    val path: WorkspacePath,
    val kind: EditKind,
    val expectedHash: ContentHash?,
    val afterUtf8: String? = null,
    val renameTo: WorkspacePath? = null,
    /** If set without [afterUtf8] for Create/Replace, validateAndDiff rejects (no GNU-patch apply). */
    val unifiedDiff: String? = null,
) {
    companion object {
        fun parse(
            path: String,
            kind: EditKind,
            expectedHash: ContentHash?,
            afterUtf8: String? = null,
            renameTo: String? = null,
            unifiedDiff: String? = null,
        ): ProposedEdit = ProposedEdit(
            path = WorkspacePath.parse(path),
            kind = kind,
            expectedHash = expectedHash,
            afterUtf8 = afterUtf8,
            renameTo = renameTo?.let(WorkspacePath::parse),
            unifiedDiff = unifiedDiff,
        )
    }
}

data class FileDiff(
    val path: WorkspacePath,
    val unified: String,
    val conflict: Boolean,
    val conflictReason: String?,
)

data class PatchSet(
    val id: PatchSetId,
    val label: String,
    val diffs: List<FileDiff>,
    val edits: List<ProposedEdit>,
)

sealed interface ApplyResult {
    data class Applied(val id: PatchSetId, val files: List<WorkspacePath>) : ApplyResult
    data class Failed(val reason: String) : ApplyResult
}

fun interface PatchEngineFactory {
    fun create(workspace: FileBackedWorkspace): PatchEngine
}

interface PatchEngine {
    suspend fun validateAndDiff(edits: List<ProposedEdit>): PatchSet

    /**
     * Only a UI/use-case with an explicit user gesture may call this
     * ([ApplyPatchUseCase]). [accepted] is computed before any write.
     * [dirty] editor buffers in [accepted] fail the whole apply.
     */
    suspend fun apply(
        id: PatchSetId,
        accepted: Set<WorkspacePath>,
        dirty: Set<WorkspacePath> = emptySet(),
    ): ApplyResult

    suspend fun reject(id: PatchSetId)

    suspend fun undo(id: PatchSetId): ApplyResult
}
