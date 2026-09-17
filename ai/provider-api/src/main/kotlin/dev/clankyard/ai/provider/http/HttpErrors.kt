package dev.clankyard.ai.provider.http

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.core.common.SecretRedactor
import dev.clankyard.core.model.Credential
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

class ProviderHttpException(
    val code: Int,
    override val message: String,
    val retryable: Boolean,
) : IOException(message)

fun requireApiKey(credential: Credential): String {
    val key = (credential as? Credential.ApiKey)?.secret?.trim().orEmpty()
    require(key.isNotEmpty()) { "API key required" }
    return key
}

fun httpErrorEvent(response: Response): ChatEvent.Error {
    val code = response.code
    val raw = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
    return httpErrorEvent(code, raw, response.message)
}

fun httpErrorEvent(code: Int, rawBody: String, reason: String = ""): ChatEvent.Error {
    val server = extractErrorMessage(rawBody).ifBlank { reason }.ifBlank { "request failed" }
    return ChatEvent.Error(
        message = SecretRedactor.redact("HTTP $code: $server"),
        retryable = code == 429 || code >= 500,
        cause = null,
    )
}

fun httpException(response: Response): ProviderHttpException {
    val event = httpErrorEvent(response)
    return ProviderHttpException(response.code, event.message, event.retryable)
}

fun redactedIoError(error: Throwable): ChatEvent.Error {
    val raw = error.message ?: error::class.java.simpleName
    return ChatEvent.Error(
        message = SecretRedactor.redact(raw),
        retryable = true,
        cause = SecretRedactor.wrap(error),
    )
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
