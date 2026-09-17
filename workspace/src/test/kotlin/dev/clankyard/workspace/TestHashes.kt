package dev.clankyard.workspace

import dev.clankyard.core.model.ContentHash
import java.security.MessageDigest

internal fun hashBytes(bytes: ByteArray): ContentHash {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = CharArray(digest.size * 2)
    val digits = "0123456789abcdef"
    for (i in digest.indices) {
        val v = digest[i].toInt() and 0xff
        hex[i * 2] = digits[v ushr 4]
        hex[i * 2 + 1] = digits[v and 0x0f]
    }
    return ContentHash(String(hex))
}
