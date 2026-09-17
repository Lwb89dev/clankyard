package dev.clankyard.workspace

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.WorkspacePath

sealed interface JournalOp {
    val path: WorkspacePath

    data class Create(
        override val path: WorkspacePath,
        val after: ByteArray,
    ) : JournalOp

    data class Replace(
        override val path: WorkspacePath,
        val after: ByteArray,
        val expectedHash: ContentHash,
    ) : JournalOp

    data class Rename(
        override val path: WorkspacePath,
        val to: WorkspacePath,
        val expectedHash: ContentHash,
    ) : JournalOp
}

sealed interface JournalApplyResult {
    data class Applied(val changeNumber: Int, val paths: List<WorkspacePath>) : JournalApplyResult
    data class Failed(val reason: String) : JournalApplyResult
}

data class JournaledChange(
    val changeNumber: Int,
    val label: String,
    val paths: List<WorkspacePath>,
)

/** WAL for Clanker edits. Never a Partial apply. */
interface ChangeJournal {
    suspend fun apply(ops: List<JournalOp>): JournalApplyResult
    suspend fun undo(changeNumber: Int): JournalApplyResult
    fun listChanges(): List<JournaledChange>
}

internal enum class JournalCrashPoint {
    AFTER_SNAPSHOT,
    AFTER_CONTENT_BEFORE_APPLIED,
    AFTER_FIRST_APPLIED_SECOND_PENDING,
}

internal class SimulatedJournalCrash(val point: JournalCrashPoint) : Error("simulated crash at $point")
