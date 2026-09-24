package dev.clankyard.editor

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class Draft(
    val path: WorkspacePath,
    val text: String,
    val expectedHash: ContentHash,
)

class DraftStore(private val root: File) {
    fun load(workspaceId: WorkspaceId, path: WorkspacePath): Draft? {
        val file = draftFile(workspaceId, path)
        if (!file.isFile || !contained(file)) return null
        val raw = file.readText(StandardCharsets.UTF_8)
        val nl = raw.indexOf('\n')
        if (nl <= 0) return null
        val hex = raw.substring(0, nl).trim()
        if (hex.isEmpty()) return null
        return Draft(path, raw.substring(nl + 1), ContentHash(hex))
    }

    fun save(workspaceId: WorkspaceId, path: WorkspacePath, text: String, expectedHash: ContentHash) {
        val dest = draftFile(workspaceId, path)
        require(contained(dest)) { "draft path escapes" }
        val payload = (expectedHash.sha256Hex + "\n" + text).toByteArray(StandardCharsets.UTF_8)
        writeAtomic(dest, payload)
    }

    fun delete(workspaceId: WorkspaceId, path: WorkspacePath) {
        val dest = draftFile(workspaceId, path)
        if (contained(dest)) dest.delete()
    }

    private fun draftFile(id: WorkspaceId, path: WorkspacePath): File =
        File(File(root, id.value), path.relative)

    private fun contained(file: File): Boolean {
        val canon = runCatching { file.canonicalFile }.getOrNull() ?: return false
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return false
        return canon == base || canon.path.startsWith(base.path + File.separatorChar)
    }
}

private fun writeAtomic(dest: File, bytes: ByteArray) {
    val parent = dest.parentFile ?: error("draft has no parent")
    parent.mkdirs()
    val temp = File.createTempFile(".${dest.name}.tmp-", "", parent)
    try {
        FileOutputStream(temp).use { fos ->
            fos.write(bytes)
            fos.flush()
            fos.fd.sync()
        }
        if (temp.renameTo(dest)) return
        Files.move(
            temp.toPath(),
            dest.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } finally {
        temp.delete()
    }
}
