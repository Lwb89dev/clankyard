package dev.clankyard.ai.provider.http

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.BufferedSource

fun interface SseHandler {
    suspend fun onEvent(event: String, data: String)
}

/**
 * Minimal SSE reader. Does not buffer the whole body.
 * [Call.cancel] closing the source unblocks [BufferedSource.readUtf8Line].
 */
suspend fun BufferedSource.consumeSse(handler: SseHandler) {
    var event = ""
    val data = StringBuilder()
    while (true) {
        currentCoroutineContext().ensureActive()
        val line = readUtf8Line() ?: break
        when {
            line.isEmpty() -> flushSse(event, data, handler).also { event = "" }
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> event = line.substring(6).trim()
            line.startsWith("data:") -> appendData(data, line)
        }
    }
    if (data.isNotEmpty()) handler.onEvent(event, data.toString())
}

private suspend fun flushSse(event: String, data: StringBuilder, handler: SseHandler) {
    if (data.isEmpty()) return
    handler.onEvent(event, data.toString())
    data.clear()
}

private fun appendData(data: StringBuilder, line: String) {
    if (data.isNotEmpty()) data.append('\n')
    val payload = line.substring(5)
    data.append(if (payload.startsWith(" ")) payload.substring(1) else payload)
}
