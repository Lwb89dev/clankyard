package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspacePath
import java.io.File

private const val ABSENT = "ABSENT"
private const val STATUS_PENDING = "pending"
private const val STATUS_APPLIED = "applied"
private const val STATUS_ROLLED_BACK = "rolled_back"
private const val STATUS_UNDONE = "undone"
private const val MANIFEST_NAME = "manifest"
private const val HEADER = "CLANKYARD_JOURNAL_V1"

internal data class JournalRow(
    val changeNumber: Int,
    val seq: Int,
    var status: String,
    val kind: String,
    val path: String,
    val snapshotRel: String,
    val destRel: String,
    val from: String,
    val to: String,
) {
    fun encode(): String =
        listOf(changeNumber, seq, status, kind, path, snapshotRel, destRel, from, to).joinToString("\t")

    companion object {
        fun decode(line: String): JournalRow? {
            if (line.isBlank()) return null
            val p = line.split('\t')
            if (p.size < 9) return null
            val n = p[0].toIntOrNull() ?: return null
            val seq = p[1].toIntOrNull() ?: return null
            return JournalRow(n, seq, p[2], p[3], p[4], p[5], p[6], p[7], p[8])
        }
    }
}

internal class DiskChangeJournal(
    private val journalDir: File,
    private val workspace: DiskFileBackedWorkspace,
    private val crashPoint: JournalCrashPoint? = null,
) : ChangeJournal {
    private val rows = ArrayList<JournalRow>()
    private val lock = Any()

    fun recover() {
        synchronized(lock) {
            journalDir.mkdirs()
            rows.clear()
            rows += readManifest()
            rollbackPendingLocked()
            deleteOrphanSnapshots()
        }
    }

    override suspend fun apply(ops: List<JournalOp>): JournalApplyResult {
        if (ops.isEmpty()) return JournalApplyResult.Failed("empty apply set")
        synchronized(lock) {
            return applyLocked(ops)
        }
    }

    override suspend fun undo(changeNumber: Int): JournalApplyResult {
        synchronized(lock) {
            return undoLocked(changeNumber)
        }
    }

    override fun listChanges(): List<JournaledChange> {
        synchronized(lock) {
            return rows.filter { it.status == STATUS_APPLIED }
                .groupBy { it.changeNumber }
                .toSortedMap()
                .map { (n, group) ->
                    JournaledChange(n, "Clanker change #$n", group.map { WorkspacePath.parse(it.path) })
                }
        }
    }

    private fun applyLocked(ops: List<JournalOp>): JournalApplyResult {
        val n = nextChangeNumber()
        val started = ArrayList<JournalRow>()
        try {
            for ((seq, op) in ops.withIndex()) {
                val row = snapshotAndPending(n, seq, op)
                started += row
                val result = perform(op)
                if (result !is WriteResult.Applied) {
                    rollbackStarted(started)
                    return JournalApplyResult.Failed(failReason(result))
                }
                maybeCrash(JournalCrashPoint.AFTER_CONTENT_BEFORE_APPLIED)
                row.status = STATUS_APPLIED
                persist()
            }
            return JournalApplyResult.Applied(n, ops.map { it.path })
        } catch (crash: SimulatedJournalCrash) {
            throw crash
        } catch (e: Exception) {
            rollbackStarted(started)
            return JournalApplyResult.Failed(e.message ?: "apply failed")
        }
    }

    private fun undoLocked(changeNumber: Int): JournalApplyResult {
        val group = rows.filter { it.changeNumber == changeNumber && it.status == STATUS_APPLIED }
        if (group.isEmpty()) return JournalApplyResult.Failed("no such applied change")
        for (row in group.asReversed()) {
            rollbackRow(row)
            row.status = STATUS_UNDONE
        }
        persist()
        return JournalApplyResult.Applied(changeNumber, group.map { WorkspacePath.parse(it.path) })
    }

    private fun snapshotAndPending(changeNumber: Int, seq: Int, op: JournalOp): JournalRow {
        validateOp(op)
        val snapshotRel = writeSnapshot(changeNumber, seq, op)
        maybeCrash(JournalCrashPoint.AFTER_SNAPSHOT)
        val row = rowFor(changeNumber, seq, op, snapshotRel)
        rows += row
        persist()
        return row
    }

    private fun validateOp(op: JournalOp) {
        val src = workspace.resolve(op.path)
        if (!workspace.containsCanonical(src)) error("path escapes workspace")
        when (op) {
            is JournalOp.Create -> if (src.exists()) error("file exists")
            is JournalOp.Replace -> if (!src.isFile) error("file missing")
            is JournalOp.Rename -> validateRename(src, op.to)
        }
    }

    private fun validateRename(src: File, to: WorkspacePath) {
        if (!src.isFile) error("file missing")
        val dst = workspace.resolve(to)
        if (!workspace.containsCanonical(dst)) error("path escapes workspace")
        if (dst.exists()) error("destination exists")
    }

    private fun writeSnapshot(changeNumber: Int, seq: Int, op: JournalOp): String {
        if (op is JournalOp.Create) return ABSENT
        val src = workspace.resolve(op.path)
        if (!workspace.containsCanonical(src) || !src.isFile) {
            error("cannot snapshot ${op.path.relative}")
        }
        val rel = "snapshots/$changeNumber-$seq"
        writeAndFsync(File(journalDir, rel), src.readBytes())
        return rel
    }

    private fun perform(op: JournalOp): WriteResult = when (op) {
        is JournalOp.Create -> workspace.writeAtomicBlocking(
            WriteRequest(op.path, op.after, expectedHash = null),
        )
        is JournalOp.Replace -> workspace.writeAtomicBlocking(
            WriteRequest(op.path, op.after, op.expectedHash),
        )
        is JournalOp.Rename -> workspace.renameBlocking(op.path, op.to, op.expectedHash)
    }

    private fun rollbackStarted(started: List<JournalRow>) {
        for (row in started.asReversed()) {
            rollbackRow(row)
            row.status = STATUS_ROLLED_BACK
        }
        persist()
    }

    private fun rollbackPendingLocked() {
        val pending = rows.filter { it.status == STATUS_PENDING }
        if (pending.isEmpty()) {
            persist()
            return
        }
        for (row in pending.asReversed()) {
            rollbackRow(row)
            row.status = STATUS_ROLLED_BACK
        }
        persist()
    }

    private fun rollbackRow(row: JournalRow) {
        if (row.kind == "rename") {
            rollbackRename(row)
            return
        }
        val dest = workspace.resolve(WorkspacePath.parse(row.destRel.ifEmpty { row.path }))
        if (!workspace.containsCanonical(dest)) return
        if (row.snapshotRel == ABSENT) {
            deleteQuietly(dest)
            return
        }
        restoreSnapshot(row.snapshotRel, dest)
    }

    private fun rollbackRename(row: JournalRow) {
        val from = workspace.resolve(WorkspacePath.parse(row.from))
        val to = workspace.resolve(WorkspacePath.parse(row.to))
        if (workspace.containsCanonical(from) && row.snapshotRel != ABSENT) {
            restoreSnapshot(row.snapshotRel, from)
        }
        if (!workspace.containsCanonical(to)) return
        if (sameCanonical(from, to)) return
        deleteQuietly(to)
    }

    private fun restoreSnapshot(snapshotRel: String, dest: File) {
        val snap = File(journalDir, snapshotRel)
        if (!snap.isFile) return
        dest.parentFile?.mkdirs()
        val temp = newSameDirTemp(dest)
        try {
            writeAndFsync(temp, snap.readBytes())
            if (!renameOver(temp, dest)) temp.delete()
        } catch (_: Exception) {
            temp.delete()
        }
    }

    private fun persist() {
        journalDir.mkdirs()
        File(journalDir, "snapshots").mkdirs()
        val dest = File(journalDir, MANIFEST_NAME)
        val temp = newSameDirTemp(dest)
        val body = buildString {
            appendLine(HEADER)
            for (row in rows) appendLine(row.encode())
        }
        writeAndFsync(temp, body.toByteArray(Charsets.UTF_8))
        if (!renameOver(temp, dest)) {
            temp.delete()
            error("failed to persist journal manifest")
        }
    }

    private fun readManifest(): List<JournalRow> {
        val dest = File(journalDir, MANIFEST_NAME)
        if (!dest.isFile) return emptyList()
        val lines = dest.readLines(Charsets.UTF_8)
        if (lines.isEmpty()) return emptyList()
        if (lines.first() != HEADER) return emptyList()
        return lines.drop(1).mapNotNull(JournalRow::decode)
    }

    private fun deleteOrphanSnapshots() {
        val snapDir = File(journalDir, "snapshots")
        val live = rows.map { it.snapshotRel }.toSet()
        val files = snapDir.listFiles() ?: return
        for (file in files) {
            val rel = "snapshots/${file.name}"
            if (rel !in live) file.delete()
        }
    }

    private fun nextChangeNumber(): Int = (rows.maxOfOrNull { it.changeNumber } ?: 0) + 1

    private fun maybeCrash(point: JournalCrashPoint) {
        if (crashPoint == point) throw SimulatedJournalCrash(point)
    }
}

private fun rowFor(changeNumber: Int, seq: Int, op: JournalOp, snapshotRel: String): JournalRow {
    val path = op.path.relative
    return when (op) {
        is JournalOp.Create -> JournalRow(
            changeNumber, seq, STATUS_PENDING, "create", path, snapshotRel, path, "", "",
        )
        is JournalOp.Replace -> JournalRow(
            changeNumber, seq, STATUS_PENDING, "replace", path, snapshotRel, path, "", "",
        )
        is JournalOp.Rename -> JournalRow(
            changeNumber, seq, STATUS_PENDING, "rename", path, snapshotRel, op.to.relative, path, op.to.relative,
        )
    }
}

private fun failReason(result: WriteResult): String = when (result) {
    is WriteResult.Conflict -> result.reason
    is WriteResult.Rejected -> result.reason
    is WriteResult.Applied -> "applied"
}

private fun sameCanonical(a: File, b: File): Boolean {
    val ca = runCatching { a.canonicalFile }.getOrNull() ?: return false
    val cb = runCatching { b.canonicalFile }.getOrNull() ?: return false
    return ca == cb
}

