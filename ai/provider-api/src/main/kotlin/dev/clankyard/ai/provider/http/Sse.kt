package dev.clankyard.ai.provider.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import okio.BufferedSource

/**
 * Minimal SSE reader. Does not buffer the whole body.
 * [okhttp3.Call.cancel] closing the source unblocks [BufferedSource.readUtf8Line].
 * [runInterruptible] lets coroutine cancellation interrupt a stalled read.
 */
suspend fun BufferedSource.consumeSse(
    handler: suspend (event: String, data: String) -> Unit,
) {
    var event = ""
    val data = StringBuilder()
    while (true) {
        currentCoroutineContext().ensureActive()
        val line = runInterruptible(Dispatchers.IO) { readUtf8Line() } ?: break
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
    if (data.isNotEmpty()) data.append('\n')
    val payload = line.substring(5)
    data.append(if (payload.startsWith(" ")) payload.substring(1) else payload)
}
