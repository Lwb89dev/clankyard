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

    val treeOps: DiskWorkshopTreeOps = DiskWorkshopTreeOps { open(it) }

    fun create(displayName: String): DiskFileBackedWorkspace {
        workspacesDir.mkdirs()
        journalRoot.mkdirs()
        val id = WorkspaceId(UUID.randomUUID().toString())
        val root = File(workspacesDir, id.value).apply { mkdirs() }
        return register(id, displayName, root)
    }

    fun registerInPlace(root: File, displayName: String): DiskFileBackedWorkspace {
        val id = WorkspaceId(UUID.randomUUID().toString())
        return register(id, displayName, root.canonicalFile)
    }

    fun open(id: WorkspaceId): DiskFileBackedWorkspace? {
        val rec = records().find { it.id == id } ?: return null
        return DiskFileBackedWorkspace(rec.id, rec.displayName, rec.root, journalDir(id))
    }

    fun openRoot(root: File): DiskFileBackedWorkspace? {
        val canon = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val rec = records().find { it.root.canonicalFile == canon } ?: return null
        return open(rec.id)
    }

    fun list(): List<WorkspaceRecord> = records()

    fun remove(id: WorkspaceId) {
        synchronized(lock) {
            val all = records().toMutableList()
            val rec = all.find { it.id == id } ?: return
            if (containsCanonical(workspacesDir, rec.root)) {
                rec.root.deleteRecursively()
            }
            journalDir(id).deleteRecursively()
            all.removeAll { it.id == id }
            writeRecords(all)
        }
    }

    private fun register(id: WorkspaceId, displayName: String, root: File): DiskFileBackedWorkspace {
        synchronized(lock) {
            val all = records().toMutableList()
            all += WorkspaceRecord(id, displayName, root.canonicalFile)
            writeRecords(all)
        }
        return DiskFileBackedWorkspace(id, displayName, root, journalDir(id))
    }

    private fun journalDir(id: WorkspaceId): File = File(journalRoot, id.value)

    private fun records(): List<WorkspaceRecord> {
        workspacesDir.mkdirs()
        val file = File(workspacesDir, REGISTRY_NAME)
        if (!file.isFile) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull(::parseRecord)
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
