package dev.clankyard.ai.provider.http

import java.io.IOException
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource

internal object ResponseLimits {
    const val JSON_BODY_BYTES = 2L * 1024 * 1024
    const val ERROR_BODY_BYTES = 64L * 1024
    const val SSE_LINE_BYTES = 256L * 1024
    const val SSE_EVENT_CHARS = 1024 * 1024
    const val SSE_STREAM_BYTES = 16L * 1024 * 1024
}

fun ResponseBody.readUtf8Limited(maxBytes: Long): String {
    require(maxBytes >= 0) { "maxBytes must be non-negative" }
    val declared = contentLength()
    if (declared > maxBytes) throw IOException("response body exceeds $maxBytes bytes")
    return source().readUtf8Limited(maxBytes)
}

internal fun BufferedSource.readUtf8Limited(maxBytes: Long): String {
    require(maxBytes >= 0) { "maxBytes must be non-negative" }
    val sink = Buffer()
    var total = 0L
    while (true) {
        if (total > maxBytes) throw IOException("response body exceeds $maxBytes bytes")
        val remaining = maxBytes - total
        val allowance = if (remaining >= 8_192L) 8_192L else remaining + 1L
        val read = read(sink, allowance)
        if (read == -1L) return sink.readUtf8()
        total += read
    }
}

internal fun BufferedSource.readUtf8LineLimited(maxBytes: Long): String? {
    val newline = indexOf('\n'.code.toByte(), 0L, maxBytes + 1)
    if (newline >= 0L) return readUtf8Line()
    if (buffer.size > maxBytes) throw IOException("SSE line exceeds $maxBytes bytes")
    return readUtf8Line()
}
