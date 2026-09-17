package dev.clankyard.editor

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.BinaryFileException
import dev.clankyard.workspace.Utf8BomDetectedException
import dev.clankyard.workspace.Workspace
import dev.clankyard.workspace.WriteRequest
import dev.clankyard.workspace.WriteResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets

data class OpenDocument(
    val path: WorkspacePath,
    val text: String,
    val lastSavedText: String,
    val diskHash: ContentHash,
    val dirty: Boolean,
    val contentEpoch: Long,
    val conflict: String? = null,
)

/** Activity-retained editor buffers. Dirty paths must block PatchEngine.apply. */
class EditorSession(
    draftsRoot: File,
    private val workspace: Workspace,
    private val scope: CoroutineScope,
    private val draftDebounceMs: Long = 400L,
    private val maxUtf8Bytes: Long = DEFAULT_MAX_UTF8_BYTES,
) {
    private val drafts = DraftStore(draftsRoot)
    private val draftJobs = HashMap<WorkspacePath, Job>()
    private var epoch = 0L

    private val _documents = MutableStateFlow<Map<WorkspacePath, OpenDocument>>(emptyMap())
    val documents: StateFlow<Map<WorkspacePath, OpenDocument>> = _documents.asStateFlow()

    private val _dirty = MutableStateFlow<Set<WorkspacePath>>(emptySet())
    val dirty: StateFlow<Set<WorkspacePath>> = _dirty.asStateFlow()

    private val _active = MutableStateFlow<WorkspacePath?>(null)
    val activePath: StateFlow<WorkspacePath?> = _active.asStateFlow()

    fun dirtyPaths(): Set<WorkspacePath> = _dirty.value

    fun document(path: WorkspacePath): OpenDocument? = _documents.value[path]

    suspend fun open(path: WorkspacePath): OpenDocument? {
        val existing = _documents.value[path]
        if (existing != null) {
            _active.value = path
            return existing
        }
        val loaded = load(path) ?: return null
        put(loaded)
        _active.value = path
        return loaded
    }

    fun edit(path: WorkspacePath, text: String) {
        val doc = _documents.value[path] ?: return
        val next = doc.copy(text = text, dirty = text != doc.lastSavedText)
        put(next)
        scheduleDraft(path)
    }

    suspend fun save(path: WorkspacePath): WriteResult {
        val doc = _documents.value[path] ?: return WriteResult.Rejected("not open")
        if (!doc.dirty) return WriteResult.Applied(doc.diskHash)
        draftJobs.remove(path)?.cancel()
        val bytes = doc.text.toByteArray(StandardCharsets.UTF_8)
        val result = workspace.writeAtomic(WriteRequest(path, bytes, expectedHash = doc.diskHash))
        when (result) {
            is WriteResult.Applied -> {
                put(doc.copy(lastSavedText = doc.text, diskHash = result.newHash, dirty = false, conflict = null))
                drafts.delete(workspace.id, path)
            }
            is WriteResult.Conflict -> put(doc.copy(conflict = result.reason))
            is WriteResult.Rejected -> put(doc.copy(conflict = result.reason))
        }
        return result
    }

    suspend fun saveActive(): WriteResult {
        val path = _active.value ?: return WriteResult.Rejected("no active document")
        return save(path)
    }

    suspend fun saveAll(): Map<WorkspacePath, WriteResult> {
        val out = LinkedHashMap<WorkspacePath, WriteResult>()
        for (path in dirtyPaths().toList()) out[path] = save(path)
        return out
    }

    suspend fun saveAs(from: WorkspacePath, to: WorkspacePath): WriteResult {
        val doc = _documents.value[from] ?: return WriteResult.Rejected("not open")
        val bytes = doc.text.toByteArray(StandardCharsets.UTF_8)
        val result = workspace.writeAtomic(WriteRequest(to, bytes, expectedHash = null))
        if (result !is WriteResult.Applied) return result
        draftJobs.remove(from)?.cancel()
        drafts.delete(workspace.id, from)
        remove(from)
        put(
            OpenDocument(
                path = to,
                text = doc.text,
                lastSavedText = doc.text,
                diskHash = result.newHash,
                dirty = false,
                contentEpoch = nextEpoch(),
            ),
        )
        _active.value = to
        return result
    }

    suspend fun close(path: WorkspacePath, discard: Boolean = false) {
        draftJobs.remove(path)?.cancel()
        if (discard) drafts.delete(workspace.id, path)
        else persistDraftSync(path)
        remove(path)
        if (_active.value == path) _active.value = _documents.value.keys.firstOrNull()
    }

    fun notifyDeleted(path: WorkspacePath) {
        draftJobs.remove(path)?.cancel()
        drafts.delete(workspace.id, path)
        remove(path)
        if (_active.value == path) _active.value = _documents.value.keys.firstOrNull()
    }

    fun notifyRenamed(from: WorkspacePath, to: WorkspacePath) {
        val doc = _documents.value[from] ?: return
        drafts.delete(workspace.id, from)
        remove(from)
        put(doc.copy(path = to, contentEpoch = nextEpoch()))
        if (_active.value == from) _active.value = to
        scheduleDraft(to)
    }

    fun closeSession() {
        for (job in draftJobs.values) job.cancel()
        draftJobs.clear()
        for (path in _documents.value.keys) persistDraftSync(path)
    }

    suspend fun flushDrafts() {
        for (job in draftJobs.values) job.cancel()
        draftJobs.clear()
        for (path in _documents.value.keys) persistDraftSync(path)
    }

    private suspend fun load(path: WorkspacePath): OpenDocument? {
        val meta = workspace.metadata(path) ?: return null
        val diskHash = meta.hash ?: return null
        if (meta.isDirectory) return null
        val diskText = readUtf8(path) ?: return null
        val draft = drafts.load(workspace.id, path)
        if (draft == null) {
            return OpenDocument(path, diskText, diskText, diskHash, false, nextEpoch())
        }
        val conflict = if (draft.expectedHash != diskHash) "on-disk hash changed" else null
        return OpenDocument(
            path = path,
            text = draft.text,
            lastSavedText = diskText,
            diskHash = draft.expectedHash,
            dirty = draft.text != diskText,
            contentEpoch = nextEpoch(),
            conflict = conflict,
        )
    }

    private suspend fun readUtf8(path: WorkspacePath): String? =
        try {
            workspace.readUtf8(path, maxUtf8Bytes)
        } catch (bom: Utf8BomDetectedException) {
            bom.strippedUtf8
        } catch (_: BinaryFileException) {
            null
        } catch (_: Exception) {
            null
        }

    private fun scheduleDraft(path: WorkspacePath) {
        draftJobs.remove(path)?.cancel()
        draftJobs[path] = scope.launch {
            if (draftDebounceMs > 0) delay(draftDebounceMs)
            persistDraftSync(path)
        }
    }

    private fun persistDraftSync(path: WorkspacePath) {
        val doc = _documents.value[path] ?: return
        if (!doc.dirty) {
            drafts.delete(workspace.id, path)
            return
        }
        drafts.save(workspace.id, path, doc.text, doc.diskHash)
    }

    private fun put(doc: OpenDocument) {
        _documents.update { it + (doc.path to doc) }
        syncDirty()
    }

    private fun remove(path: WorkspacePath) {
        _documents.update { it - path }
        syncDirty()
    }

    private fun syncDirty() {
        _dirty.value = _documents.value.filter { it.value.dirty }.keys
    }

    private fun nextEpoch(): Long {
        epoch += 1
        return epoch
    }

    companion object {
        const val DEFAULT_MAX_UTF8_BYTES: Long = 2L * 1024L * 1024L
    }
}
