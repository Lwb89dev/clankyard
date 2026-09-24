package dev.clankyard.build.runtime

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object ZipUnpacker {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
    }

    fun unzip(zip: File, dest: File) {
        require(zip.isFile) { "runtime archive does not exist: ${zip.path}" }
        dest.mkdirs()
        val root = dest.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val out = File(root, entry.name).canonicalFile
                if (!contains(root, out)) {
                    throw SecurityException("zip slip: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    val parent = out.parentFile ?: throw IOException("archive entry has no parent")
                    if (!contains(root, parent)) throw SecurityException("zip slip: ${entry.name}")
                    parent.mkdirs()
                    if (!parent.isDirectory) throw IOException("archive parent is not a directory")
                    FileOutputStream(out).use { zin.copyTo(it) }
                }
                zin.closeEntry()
            }
        }
    }

    private fun contains(root: File, target: File): Boolean {
        val rootPath = root.canonicalPath
        val targetPath = target.canonicalPath
        return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    }
}
