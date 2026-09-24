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
    val raw = runCatching {
        response.body?.readUtf8Limited(ResponseLimits.ERROR_BODY_BYTES).orEmpty()
    }.getOrDefault("")
    return httpErrorEvent(response.code, raw, response.message, apiKey)
}

fun httpErrorEvent(
    code: Int,
    rawBody: String,
    reason: String = "",
    apiKey: String = "",
): ChatEvent.Error {
    val parsed = parseApiError(rawBody)
    val server = parsed.message.ifBlank { reason }.ifBlank { "request failed" }
    val friendly = friendlyHttpMessage(code, server, parsed.code, parsed.type)
    val combined = redactExactKey(friendly, apiKey)
    return ChatEvent.Error(
        message = SecretRedactor.redact(combined),
        retryable = isTransientHttp(code, parsed.code, server),
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

internal data class ParsedApiError(
    val message: String,
    val type: String?,
    val code: String?,
)

internal fun parseApiError(body: String): ParsedApiError {
    if (body.isBlank()) return ParsedApiError("", null, null)
    val element = runCatching { ProviderJson.parseToJsonElement(body) }.getOrNull()
    val obj = element as? JsonObject ?: return ParsedApiError(body.take(500), null, null)
    val err = obj["error"]
    val errObj = err as? JsonObject
    val message = when (err) {
        is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull ?: err.toString()
        is JsonPrimitive -> err.content
        else -> obj["message"]?.jsonPrimitive?.contentOrNull
    } ?: body.take(500)
    val code = errObj?.get("code")?.jsonPrimitive?.contentOrNull
        ?: obj["code"]?.jsonPrimitive?.contentOrNull
    val type = errObj?.get("type")?.jsonPrimitive?.contentOrNull
        ?: obj["type"]?.jsonPrimitive?.contentOrNull
    return ParsedApiError(message, type, code)
}

private val BILLING_CODES = setOf(
    "credit_balance_exhausted",
    "insufficient_quota",
    "billing_not_active",
    "organization_spend_limit_exceeded",
    "project_spend_limit_exceeded",
    "organization_usage_limit_exceeded",
)

private fun isBilling(code: String?, message: String): Boolean {
    if (code != null && code.lowercase() in BILLING_CODES) return true
    val m = message.lowercase()
    return m.contains("credits remaining") ||
        m.contains("credit balance") ||
        m.contains("insufficient_quota") ||
        m.contains("exceeded your current quota") ||
        m.contains("billing details")
}

private fun isTransientHttp(httpCode: Int, apiCode: String?, message: String): Boolean {
    if (isBilling(apiCode, message)) return false
    val code = apiCode?.lowercase()
    if (code == "rate_limit_exceeded" || code == "slow_down") return true
    return httpCode == 429 || httpCode >= 500
}

internal fun friendlyHttpMessage(
    httpCode: Int,
    server: String,
    apiCode: String?,
    apiType: String?,
): String {
    if (isBilling(apiCode, server)) {
        return "HTTP $httpCode: prepaid API credits are empty for this key's organization. " +
            "ChatGPT Plus/Team is not API credit. Buy credits at " +
            "https://platform.openai.com/settings/organization/billing " +
            "(same org as the key). [$server]"
    }
    val detail = listOfNotNull(apiCode, apiType).joinToString("/")
    val suffix = if (detail.isEmpty()) server else "$server ($detail)"
    return "HTTP $httpCode: $suffix"
}
