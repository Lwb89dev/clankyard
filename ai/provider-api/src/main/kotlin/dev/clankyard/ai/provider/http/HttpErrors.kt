package dev.clankyard.ai.provider.http

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.core.common.SecretRedactor
import dev.clankyard.core.model.Credential
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

fun requireApiKey(credential: Credential): String {
    val key = (credential as? Credential.ApiKey)?.secret?.trim().orEmpty()
    require(key.isNotEmpty()) { "API key required" }
    return key
}

fun httpErrorEvent(response: Response, apiKey: String = ""): ChatEvent.Error {
    val raw = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
    return httpErrorEvent(response.code, raw, response.message, apiKey)
}

fun httpErrorEvent(
    code: Int,
    rawBody: String,
    reason: String = "",
    apiKey: String = "",
): ChatEvent.Error {
    val server = extractErrorMessage(rawBody).ifBlank { reason }.ifBlank { "request failed" }
    val combined = redactExactKey("HTTP $code: $server", apiKey)
    return ChatEvent.Error(
        message = SecretRedactor.redact(combined),
        retryable = code == 429 || code >= 500,
        cause = null,
    )
}

fun redactedIoError(error: Throwable, apiKey: String = ""): ChatEvent.Error {
    val raw = error.message ?: error::class.java.simpleName
    return ChatEvent.Error(
        message = SecretRedactor.redact(redactExactKey(raw, apiKey)),
        retryable = true,
        cause = SecretRedactor.wrap(error),
    )
}

internal fun redactExactKey(text: String, apiKey: String): String {
    if (apiKey.length < 8) return text
    return text.replace(apiKey, "[REDACTED]")
}

private fun extractErrorMessage(body: String): String {
    if (body.isBlank()) return ""
    val element = runCatching { ProviderJson.parseToJsonElement(body) }.getOrNull()
    val obj = element as? JsonObject ?: return body.take(500)
    val err = obj["error"]
    val fromError = when (err) {
        is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull ?: err.toString()
        is JsonPrimitive -> err.content
        else -> null
    }
    return fromError ?: obj["message"]?.jsonPrimitive?.contentOrNull ?: body.take(500)
}
