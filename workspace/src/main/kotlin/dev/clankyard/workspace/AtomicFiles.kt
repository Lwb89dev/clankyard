package dev.clankyard.workspace

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun newSameDirTemp(dest: File): File {
    val parent = dest.parentFile ?: error("destination has no parent")
    parent.mkdirs()
    val prefix = ".${dest.name}.tmp-"
    val safe = if (prefix.length < 3) ".tmp-" else prefix.take(200)
    return File.createTempFile(safe, "", parent)
}

internal fun writeAndFsync(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    FileOutputStream(file).use { fos ->
        fos.write(bytes)
        fos.flush()
        fos.fd.sync()
    }
}

internal fun renameOver(temp: File, dest: File): Boolean {
    if (temp.renameTo(dest)) return true
    return runCatching {
        Files.move(
            temp.toPath(),
            dest.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
        true
    }.getOrDefault(false)
}

internal fun isAtomicTempName(name: String): Boolean =
    name.startsWith(".") && name.contains(".tmp-")

/** Delete a file or tree without following symlinks. */
internal fun deleteUnfollowed(file: File): Boolean {
    if (!file.exists() && !Files.isSymbolicLink(file.toPath())) return true
    if (Files.isSymbolicLink(file.toPath()) || file.isFile) return file.delete()
    val children = file.listFiles() ?: return file.delete()
    var ok = true
    for (child in children) {
        ok = deleteUnfollowed(child) && ok
    }
    return file.delete() && ok
}

internal fun deleteQuietly(file: File) {
    deleteUnfollowed(file)
}
