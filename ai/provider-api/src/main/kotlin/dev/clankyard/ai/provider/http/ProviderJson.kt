package dev.clankyard.ai.provider.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

val ProviderJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun parseJsonSchema(raw: String): JsonElement {
    if (raw.isBlank()) return emptyObjectSchema()
    return runCatching { ProviderJson.parseToJsonElement(raw) }.getOrElse { emptyObjectSchema() }
}

fun emptyObjectSchema(): JsonObject = buildJsonObject { put("type", "object") }

fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

fun parseObject(raw: String): JsonObject? =
    runCatching { ProviderJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
