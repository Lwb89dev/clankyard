package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspaceId
import java.io.File
import java.util.UUID

private const val REGISTRY_NAME = "registry"

data class WorkspaceRecord(
    val id: WorkspaceId,
    val displayName: String,
    val root: File,
)

class FileWorkspaceRegistry(
    val workspacesDir: File,
    val journalRoot: File,
) {
    private val lock = Any()
    private val inflight = HashSet<WorkspaceId>()

    val treeOps: DiskWorkshopTreeOps = DiskWorkshopTreeOps { open(it) }

    init {
        synchronized(lock) { reapOrphansLocked() }
    }

    fun create(displayName: String): DiskFileBackedWorkspace {
        workspacesDir.mkdirs()
        journalRoot.mkdirs()
        val id = WorkspaceId(UUID.randomUUID().toString())
        val root = File(workspacesDir, id.value).apply { mkdirs() }
        return register(id, displayName, root)
    }

    fun prepareCopy(displayName: String): DiskFileBackedWorkspace {
        workspacesDir.mkdirs()
        journalRoot.mkdirs()
        val id = WorkspaceId(UUID.randomUUID().toString())
        val staging = File(workspacesDir, ".staging-${id.value}").apply { mkdirs() }
        synchronized(lock) { inflight += id }
        return DiskFileBackedWorkspace(id, displayName, staging, journalDir(id))
    }

    fun publish(ws: DiskFileBackedWorkspace): DiskFileBackedWorkspace {
        val dest = File(workspacesDir, ws.id.value)
        val staging = ws.root
        try {
            if (staging.canonicalFile != dest.canonicalFile && !staging.renameTo(dest)) {
                error("failed to publish workshop ${ws.id.value}")
            }
            return register(ws.id, ws.displayName, dest)
        } catch (e: Exception) {
            abortFailedPublish(ws.id, dest)
            throw e
        } finally {
            synchronized(lock) { inflight -= ws.id }
        }
    }

    fun discardUnpublished(ws: DiskFileBackedWorkspace) {
        synchronized(lock) { inflight -= ws.id }
        deleteUnfollowed(ws.root)
        deleteUnfollowed(journalDir(ws.id))
    }

    fun registerInPlace(root: File, displayName: String): DiskFileBackedWorkspace {
        val id = WorkspaceId(UUID.randomUUID().toString())
        return register(id, displayName, root.canonicalFile)
    }

    fun open(id: WorkspaceId): DiskFileBackedWorkspace? {
        val rec = synchronized(lock) { loadRecords().find { it.id == id } } ?: return null
        return DiskFileBackedWorkspace(rec.id, rec.displayName, rec.root, journalDir(id))
    }

    fun openRoot(root: File): DiskFileBackedWorkspace? {
        val canon = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val rec = synchronized(lock) {
            loadRecords().find { it.root.canonicalFile == canon }
        } ?: return null
        return open(rec.id)
    }

    fun list(): List<WorkspaceRecord> {
        synchronized(lock) {
            reapOrphansLocked()
            return loadRecords()
        }
    }

    fun remove(id: WorkspaceId) {
        synchronized(lock) {
            val all = loadRecords().toMutableList()
            val rec = all.find { it.id == id } ?: return
            if (containsCanonical(workspacesDir, rec.root)) {
                deleteUnfollowed(rec.root)
            }
            deleteUnfollowed(journalDir(id))
            all.removeAll { it.id == id }
            writeRecords(all)
        }
    }

    private fun register(id: WorkspaceId, displayName: String, root: File): DiskFileBackedWorkspace {
        synchronized(lock) {
            val all = loadRecords().toMutableList()
            all += WorkspaceRecord(id, displayName, root.canonicalFile)
            writeRecords(all)
        }
        return DiskFileBackedWorkspace(id, displayName, root, journalDir(id))
    }

    private fun abortFailedPublish(id: WorkspaceId, dest: File) {
        val registered = synchronized(lock) { loadRecords().any { it.id == id } }
        if (registered) return
        deleteUnfollowed(dest)
        deleteUnfollowed(journalDir(id))
    }

    private fun journalDir(id: WorkspaceId): File = File(journalRoot, id.value)

    private fun loadRecords(): List<WorkspaceRecord> {
        workspacesDir.mkdirs()
        val file = File(workspacesDir, REGISTRY_NAME)
        if (!file.isFile) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull(::parseRecord)
    }

    private fun reapOrphansLocked() {
        workspacesDir.mkdirs()
        journalRoot.mkdirs()
        val live = HashSet<String>()
        for (rec in loadRecords()) live += rec.id.value
        for (id in inflight) live += id.value
        reapWorkspaceDir(live)
        reapJournalDir(live)
    }

    private fun reapWorkspaceDir(live: Set<String>) {
        val children = workspacesDir.listFiles() ?: return
        for (child in children) {
            if (keepWorkspaceChild(child, live)) continue
            deleteUnfollowed(child)
        }
    }

    private fun keepWorkspaceChild(child: File, live: Set<String>): Boolean {
        val name = child.name
        if (name == REGISTRY_NAME) return true
        if (isAtomicTempName(name)) return false
        if (name.startsWith(".staging-")) return name.removePrefix(".staging-") in live
        if (!child.isDirectory) return true
        return name in live
    }

    private fun reapJournalDir(live: Set<String>) {
        val children = journalRoot.listFiles() ?: return
        for (child in children) {
            if (child.name in live) continue
            deleteUnfollowed(child)
        }
    }

    private fun writeRecords(all: List<WorkspaceRecord>) {
        workspacesDir.mkdirs()
        val dest = File(workspacesDir, REGISTRY_NAME)
        val body = all.joinToString("\n") { "${it.id.value}\t${escape(it.displayName)}\t${it.root.canonicalPath}" }
        val payload = (body + if (body.isEmpty()) "" else "\n").toByteArray(Charsets.UTF_8)
        val temp = newSameDirTemp(dest)
        writeAndFsync(temp, payload)
        if (!renameOver(temp, dest)) {
            temp.delete()
            error("failed to persist workspace registry")
        }
    }

    private fun parseRecord(line: String): WorkspaceRecord? {
        if (line.isBlank()) return null
        val p = line.split('\t')
        if (p.size < 3) return null
        return WorkspaceRecord(WorkspaceId(p[0]), unescape(p[1]), File(p[2]))
    }
}

private fun escape(name: String): String = name.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

private fun unescape(name: String): String = buildString {
    var i = 0
    while (i < name.length) {
        val c = name[i]
        if (c != '\\' || i + 1 >= name.length) {
            append(c)
            i++
            continue
        }
        when (name[i + 1]) {
            't' -> append('\t')
            'n' -> append('\n')
            '\\' -> append('\\')
            else -> append(name[i + 1])
        }
        i += 2
    }
}
