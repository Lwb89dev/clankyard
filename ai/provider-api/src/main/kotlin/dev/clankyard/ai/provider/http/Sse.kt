package dev.clankyard.ai.provider.http

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import okio.BufferedSource

/**
 * Minimal SSE reader. Does not buffer the whole body.
 * Lines, events, and the whole stream are bounded so a hostile compatible
 * endpoint cannot grow memory indefinitely.
 * [runInterruptible] lets coroutine cancellation interrupt a stalled read.
 */
suspend fun BufferedSource.consumeSse(
    handler: suspend (event: String, data: String) -> Unit,
) {
    var event = ""
    val data = StringBuilder()
    var streamBytes = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val line = runInterruptible(Dispatchers.IO) {
            readUtf8LineLimited(ResponseLimits.SSE_LINE_BYTES)
        } ?: break
        streamBytes += line.toByteArray(Charsets.UTF_8).size + 1L
        if (streamBytes > ResponseLimits.SSE_STREAM_BYTES) {
            throw IOException("SSE stream exceeds ${ResponseLimits.SSE_STREAM_BYTES} bytes")
        }
        when {
            line.isEmpty() -> flushSse(event, data, handler).also { event = "" }
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> event = line.substring(6).trim()
            line.startsWith("data:") -> appendData(data, line)
        }
    }
    if (data.isNotEmpty()) handler(event, data.toString())
}

private suspend fun flushSse(
    event: String,
    data: StringBuilder,
    handler: suspend (String, String) -> Unit,
) {
    if (data.isEmpty()) return
    handler(event, data.toString())
    data.clear()
}

private fun appendData(data: StringBuilder, line: String) {
    val payload = line.substring(5)
    val value = if (payload.startsWith(" ")) payload.substring(1) else payload
    val added = value.length + if (data.isNotEmpty()) 1 else 0
    if (data.length + added > ResponseLimits.SSE_EVENT_CHARS) {
        throw IOException("SSE event exceeds ${ResponseLimits.SSE_EVENT_CHARS} characters")
    }
    if (data.isNotEmpty()) data.append('\n')
    data.append(value)
}
