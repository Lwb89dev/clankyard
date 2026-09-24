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

    fun unzip(
        zip: File,
        dest: File,
        maxExtractedBytes: Long = MAX_EXTRACTED_BYTES,
        maxEntries: Int = MAX_ENTRIES,
    ) {
        require(zip.isFile) { "runtime archive does not exist: ${zip.path}" }
        require(maxExtractedBytes >= 0) { "maxExtractedBytes must be non-negative" }
        require(maxEntries > 0) { "maxEntries must be positive" }
        dest.mkdirs()
        val root = dest.canonicalFile
        var extracted = 0L
        var entries = 0
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                entries++
                if (entries > maxEntries) throw IOException("runtime archive has too many entries")
                if (entry.name.length > MAX_ENTRY_NAME_CHARS) {
                    throw IOException("runtime archive entry name is too long")
                }
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
                    FileOutputStream(out).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val count = zin.read(buffer)
                            if (count < 0) break
                            extracted += count
                            if (extracted > maxExtractedBytes) {
                                throw IOException("runtime archive expands beyond allowed size")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
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

    private const val BUFFER_BYTES = 64 * 1024
    private const val MAX_ENTRY_NAME_CHARS = 4_096
    private const val MAX_ENTRIES = 250_000
    internal const val MAX_EXTRACTED_BYTES = 8L * 1024L * 1024L * 1024L
}
