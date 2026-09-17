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
        val content = contentFile(workspaceId, path)
        val hash = hashFile(workspaceId, path)
        if (!content.isFile || !hash.isFile) return null
        if (!contained(content) || !contained(hash)) return null
        val hex = hash.readText(StandardCharsets.UTF_8).trim()
        if (hex.isEmpty()) return null
        return Draft(path, content.readText(StandardCharsets.UTF_8), ContentHash(hex))
    }

    fun save(workspaceId: WorkspaceId, path: WorkspacePath, text: String, expectedHash: ContentHash) {
        val content = contentFile(workspaceId, path)
        val hash = hashFile(workspaceId, path)
        if (!contained(content) || !contained(hash)) return
        writeAtomic(content, text.toByteArray(StandardCharsets.UTF_8))
        writeAtomic(hash, expectedHash.sha256Hex.toByteArray(StandardCharsets.UTF_8))
    }

    fun delete(workspaceId: WorkspaceId, path: WorkspacePath) {
        val content = contentFile(workspaceId, path)
        val hash = hashFile(workspaceId, path)
        if (contained(content)) content.delete()
        if (contained(hash)) hash.delete()
    }

    private fun contentFile(id: WorkspaceId, path: WorkspacePath): File =
        File(File(root, id.value), "content/${path.relative}")

    private fun hashFile(id: WorkspaceId, path: WorkspacePath): File =
        File(File(root, id.value), "hash/${path.relative}")

    private fun contained(file: File): Boolean {
        val canon = runCatching { file.canonicalFile }.getOrNull() ?: return false
        val base = runCatching { root.canonicalFile }.getOrNull() ?: return false
        return canon == base || canon.path.startsWith(base.path + File.separatorChar)
    }
}

private fun writeAtomic(dest: File, bytes: ByteArray) {
    dest.parentFile?.mkdirs()
    val temp = File.createTempFile(".${dest.name}.tmp-", "", dest.parentFile)
    try {
        FileOutputStream(temp).use { fos ->
            fos.write(bytes)
            fos.flush()
            fos.fd.sync()
        }
        if (temp.renameTo(dest)) return
        Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: Exception) {
        temp.delete()
    }
}
