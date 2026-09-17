package dev.clankyard.ai.patch

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.PatchSetId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.diff.DiffEngine
import dev.clankyard.workspace.BinaryFileException
import dev.clankyard.workspace.FileBackedWorkspace
import dev.clankyard.workspace.FileTooLargeException
import dev.clankyard.workspace.JournalApplyResult
import dev.clankyard.workspace.JournalOp
import dev.clankyard.workspace.Utf8BomDetectedException
import kotlinx.coroutines.CancellationException
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val READ_MAX_BYTES = 32L * 1024 * 1024
private const val GNU_PATCH_REJECTED = "GNU-patch apply is not supported; afterUtf8 required"
private const val DIRTY_REFUSE = "dirty editor buffers: save or discard first"

class WorkspacePatchEngine(
    private val workspace: FileBackedWorkspace,
    private val diffEngine: DiffEngine,
) : PatchEngine {
    private val lock = Any()
    private val pending = LinkedHashMap<PatchSetId, StoredPatch>()
    private val applied = LinkedHashMap<PatchSetId, AppliedPatch>()

    init {
        for (change in workspace.journal.listChanges()) {
            applied[PatchSetId(change.changeNumber.toString())] = AppliedPatch(change.changeNumber)
        }
    }

    override suspend fun validateAndDiff(edits: List<ProposedEdit>): PatchSet {
        val diffs = ArrayList<FileDiff>(edits.size)
        val applicable = ArrayList<ProposedEdit>(edits.size)
        val seen = HashSet<WorkspacePath>()
        for (edit in edits) {
            val diff = classify(edit, seen)
            diffs += diff
            if (!diff.conflict) applicable += edit
        }
        val id = PatchSetId(UUID.randomUUID().toString())
        val set = PatchSet(id, "Clanker change", diffs, edits)
        synchronized(lock) { pending[id] = StoredPatch(applicable) }
        return set
    }

    override suspend fun apply(
        id: PatchSetId,
        accepted: Set<WorkspacePath>,
        dirty: Set<WorkspacePath>,
    ): ApplyResult {
        val stored = synchronized(lock) { pending[id] } ?: return missing(id)
        if (accepted.any { it in dirty }) return ApplyResult.Failed(DIRTY_REFUSE)
        val ops = journalOps(stored, accepted)
        if (ops.isEmpty()) return ApplyResult.Failed("empty apply set")
        return finishApply(id, workspace.journal.apply(ops))
    }

    override suspend fun reject(id: PatchSetId) {
        synchronized(lock) { pending.remove(id) }
    }

    override suspend fun undo(id: PatchSetId): ApplyResult {
        val rec = synchronized(lock) { applied[id] } ?: return ApplyResult.Failed("no such applied change")
        return when (val result = workspace.journal.undo(rec.changeNumber)) {
            is JournalApplyResult.Applied -> {
                synchronized(lock) { dropApplied(rec.changeNumber) }
                ApplyResult.Applied(id, result.paths)
            }
            is JournalApplyResult.Failed -> ApplyResult.Failed(result.reason)
        }
    }

    private suspend fun classify(edit: ProposedEdit, seen: MutableSet<WorkspacePath>): FileDiff {
        gnuPatchReject(edit)?.let { return it }
        if (!seen.add(edit.path)) return conflict(edit.path, "", "duplicate path")
        fieldConflict(edit)?.let { return it }
        val to = edit.renameTo
        if (to != null && !seen.add(to)) return conflict(edit.path, "", "duplicate path")
        if (escapes(edit.path) || (to != null && escapes(to))) {
            return conflict(edit.path, "", "path escapes workspace")
        }
        return when (edit.kind) {
            EditKind.Create -> classifyCreate(edit)
            EditKind.Replace -> classifyReplace(edit)
            EditKind.Rename -> classifyRename(edit)
        }
    }

    private fun gnuPatchReject(edit: ProposedEdit): FileDiff? {
        if (edit.kind == EditKind.Rename) return null
        if (edit.afterUtf8 != null) return null
        return FileDiff(edit.path, "", true, GNU_PATCH_REJECTED)
    }

    private fun fieldConflict(edit: ProposedEdit): FileDiff? {
        if (edit.path.isRoot) return conflict(edit.path, "", "cannot patch workspace root")
        return when (edit.kind) {
            EditKind.Create -> createFields(edit)
            EditKind.Replace -> replaceFields(edit)
            EditKind.Rename -> renameFields(edit)
        }
    }

    private fun createFields(edit: ProposedEdit): FileDiff? {
        if (edit.expectedHash != null) {
            return conflict(edit.path, previewCreate(edit), "expectedHash must be null for Create")
        }
        if (edit.renameTo != null) {
            return conflict(edit.path, previewCreate(edit), "renameTo must be null for Create")
        }
        return null
    }

    private fun replaceFields(edit: ProposedEdit): FileDiff? {
        if (edit.expectedHash == null) {
            return conflict(edit.path, previewReplace(edit, ""), "expectedHash required")
        }
        if (edit.renameTo != null) {
            return conflict(edit.path, previewReplace(edit, ""), "renameTo must be null for Replace")
        }
        return null
    }

    private fun renameFields(edit: ProposedEdit): FileDiff? {
        if (edit.expectedHash == null) return conflict(edit.path, "", "expectedHash required")
        val to = edit.renameTo ?: return conflict(edit.path, "", "renameTo required")
        if (to.isRoot) return conflict(edit.path, "", "cannot rename to workspace root")
        if (to == edit.path) return conflict(edit.path, "", "source and destination are the same")
        return null
    }

    private suspend fun classifyCreate(edit: ProposedEdit): FileDiff {
        val preview = previewCreate(edit)
        val meta = workspace.metadata(edit.path)
        if (meta != null) return conflict(edit.path, preview, "file exists")
        return FileDiff(edit.path, preview, false, null)
    }

    private suspend fun classifyReplace(edit: ProposedEdit): FileDiff {
        val before = readBefore(edit.path)
        val preview = previewReplace(edit, before.text)
        if (before.reason != null) return conflict(edit.path, preview, before.reason)
        if (before.hash != edit.expectedHash) {
            return conflict(edit.path, preview, "expectedHash mismatch")
        }
        return FileDiff(edit.path, preview, false, null)
    }

    private suspend fun classifyRename(edit: ProposedEdit): FileDiff {
        val to = edit.renameTo!!
        val preview = previewRename(edit.path, to)
        val src = workspace.metadata(edit.path)
        if (src == null || src.isDirectory) return conflict(edit.path, preview, "file missing")
        if (src.hash != edit.expectedHash) return conflict(edit.path, preview, "expectedHash mismatch")
        if (workspace.metadata(to) != null) return conflict(edit.path, preview, "destination exists")
        return FileDiff(edit.path, preview, false, null)
    }

    private suspend fun readBefore(path: WorkspacePath): Before {
        val meta = workspace.metadata(path)
        if (meta == null) return Before("", null, "file missing")
        if (meta.isDirectory) return Before("", null, "is a directory")
        return readUtf8Before(path, meta.hash)
    }

    private suspend fun readUtf8Before(path: WorkspacePath, hash: ContentHash?): Before {
        return try {
            Before(workspace.readUtf8(path, READ_MAX_BYTES), hash, null)
        } catch (e: Utf8BomDetectedException) {
            Before(e.strippedUtf8, hash, null)
        } catch (_: BinaryFileException) {
            Before("", hash, "binary file")
        } catch (_: FileTooLargeException) {
            Before("", hash, "file too large")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Before("", hash, e.message ?: "unreadable")
        }
    }

    private fun previewCreate(edit: ProposedEdit): String =
        diffEngine.unified("", edit.afterUtf8.orEmpty(), edit.path.relative)

    private fun previewReplace(edit: ProposedEdit, before: String): String =
        diffEngine.unified(before, edit.afterUtf8.orEmpty(), edit.path.relative)

    private fun previewRename(from: WorkspacePath, to: WorkspacePath): String =
        "--- a/${from.relative}\n+++ b/${to.relative}\n"

    private fun escapes(path: WorkspacePath): Boolean =
        !workspace.containsCanonical(workspace.resolve(path))

    private fun journalOps(stored: StoredPatch, accepted: Set<WorkspacePath>): List<JournalOp> {
        val ops = ArrayList<JournalOp>()
        for (edit in stored.applicable) {
            if (edit.path !in accepted) continue
            ops += toOp(edit)
        }
        return ops
    }

    private fun toOp(edit: ProposedEdit): JournalOp {
        val hash = edit.expectedHash
        val after = edit.afterUtf8
        return when (edit.kind) {
            EditKind.Create -> JournalOp.Create(edit.path, utf8(after!!))
            EditKind.Replace -> JournalOp.Replace(edit.path, utf8(after!!), hash!!)
            EditKind.Rename -> JournalOp.Rename(edit.path, edit.renameTo!!, hash!!)
        }
    }

    private fun finishApply(id: PatchSetId, result: JournalApplyResult): ApplyResult {
        return when (result) {
            is JournalApplyResult.Applied -> {
                synchronized(lock) {
                    pending.remove(id)
                    applied[id] = AppliedPatch(result.changeNumber)
                    applied[PatchSetId(result.changeNumber.toString())] = AppliedPatch(result.changeNumber)
                }
                ApplyResult.Applied(id, result.paths)
            }
            is JournalApplyResult.Failed -> ApplyResult.Failed(result.reason)
        }
    }

    private fun dropApplied(changeNumber: Int) {
        val keys = applied.filterValues { it.changeNumber == changeNumber }.keys.toList()
        for (key in keys) applied.remove(key)
    }

    private fun missing(id: PatchSetId): ApplyResult.Failed {
        if (synchronized(lock) { id in applied }) return ApplyResult.Failed("already applied")
        return ApplyResult.Failed("unknown patch set")
    }

    private fun conflict(path: WorkspacePath, unified: String, reason: String) =
        FileDiff(path, unified, true, reason)

    private fun utf8(text: String): ByteArray = text.toByteArray(StandardCharsets.UTF_8)

    private data class StoredPatch(val applicable: List<ProposedEdit>)
    private data class AppliedPatch(val changeNumber: Int)
    private data class Before(val text: String, val hash: ContentHash?, val reason: String?)
}
