package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspacePath
import java.nio.charset.StandardCharsets

private const val NUL_SCAN_BYTES = 8192

internal fun hasNulInPrefix(bytes: ByteArray): Boolean {
    val n = minOf(bytes.size, NUL_SCAN_BYTES)
    for (i in 0 until n) {
        if (bytes[i] == 0.toByte()) return true
    }
    return false
}

internal fun hasUtf8Bom(bytes: ByteArray): Boolean =
    bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()

internal fun decodeUtf8OrThrow(path: WorkspacePath, bytes: ByteArray): String {
    if (hasNulInPrefix(bytes)) throw BinaryFileException(path)
    if (hasUtf8Bom(bytes)) {
        val stripped = String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        throw Utf8BomDetectedException(path, stripped)
    }
    return String(bytes, StandardCharsets.UTF_8)
}
