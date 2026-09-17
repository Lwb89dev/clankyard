package dev.clankyard.workspace

import dev.clankyard.core.model.ContentHash
import java.io.File
import java.security.MessageDigest

private const val HEX = "0123456789abcdef"

internal fun hashBytes(bytes: ByteArray): ContentHash {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return ContentHash(toHex(digest))
}

internal fun hashFile(file: File): ContentHash {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
    }
    return ContentHash(toHex(md.digest()))
}

internal val EMPTY_HASH = ContentHash("")

private fun toHex(bytes: ByteArray): String {
    val out = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xff
        out[i * 2] = HEX[v ushr 4]
        out[i * 2 + 1] = HEX[v and 0x0f]
    }
    return String(out)
}
