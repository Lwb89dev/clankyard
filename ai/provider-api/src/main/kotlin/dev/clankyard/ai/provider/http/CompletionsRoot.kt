package dev.clankyard.ai.provider.http

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Thrown when a user-supplied OpenAI-compatible base URL uses `http://`.
 * Local models (Ollama on LAN) are post-MVP [LocalModelProvider].
 */
class CleartextEndpointException(message: String = MESSAGE) : IllegalArgumentException(message) {
    companion object {
        const val MESSAGE =
            "http:// is not supported. Local models (Ollama and similar) will ship as " +
                "post-MVP LocalModelProvider. Use https:// with the system certificate store."
    }
}

/**
 * Normalize a Completions origin (KD-17 / KD-20).
 *
 * - Require `https` (reject `http` with copy pointing at post-MVP LocalModelProvider).
 * - Empty path or `/` → append `/v1`.
 * - Path already ending in `/v1` is kept.
 * - Strip a trailing `/chat/completions`.
 * Result is the root used for `/chat/completions` and `/models`.
 *
 * `https://api.x.ai` and `https://api.x.ai/v1` both become `https://api.x.ai/v1`.
 */
fun normalizeCompletionsRoot(userInput: String): HttpUrl {
    val parsed = userInput.trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("invalid URL: $userInput")
    if (!parsed.isHttps) throw CleartextEndpointException()
    val segments = parsed.pathSegments.filter { it.isNotEmpty() }.toMutableList()
    stripChatCompletions(segments)
    if (segments.isEmpty()) segments += "v1"
    val builder = parsed.newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .username("")
        .password("")
    for (segment in segments) builder.addPathSegment(segment)
    return builder.build()
}

fun HttpUrl.chatCompletionsUrl(): HttpUrl =
    newBuilder().addPathSegment("chat").addPathSegment("completions").build()

fun HttpUrl.modelsUrl(): HttpUrl =
    newBuilder().addPathSegment("models").build()

fun HttpUrl.messagesUrl(): HttpUrl =
    newBuilder().addPathSegment("messages").build()

private fun stripChatCompletions(segments: MutableList<String>) {
    if (segments.size < 2) return
    if (segments.last() != "completions") return
    if (segments[segments.lastIndex - 1] != "chat") return
    segments.removeAt(segments.lastIndex)
    segments.removeAt(segments.lastIndex)
}
