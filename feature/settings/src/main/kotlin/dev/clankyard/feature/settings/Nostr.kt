package dev.clankyard.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class BunkerUri(
    val pubkeyHex: String,
    val relays: List<String>,
    val secret: String?,
) {
    companion object {
        fun parse(raw: String): BunkerUri? {
            val trimmed = raw.trim()
            if (trimmed.length > MAX_BUNKER_URI_CHARS) return null
            if (looksLikeNsec(trimmed)) return null
            if (!trimmed.startsWith("bunker://", ignoreCase = true)) return null
            val rest = trimmed.substringAfter("://")
            val body = rest.substringBefore("?")
            val query = rest.substringAfter("?", missingDelimiterValue = "")
            val pubkey = body.substringBefore("/").lowercase()
            if (!isHexPubkey(pubkey)) return null
            val parts = query.split("&")
            if (parts.size > MAX_QUERY_PARTS) return null
            val pairs = parts.mapNotNull { part ->
                val key = part.substringBefore("=")
                val value = part.substringAfter("=", missingDelimiterValue = "")
                if (key.isEmpty()) null else decodeQuery(value)?.let { key to it }
            }
            val relays = pairs.asSequence()
                .filter { it.first == "relay" }
                .map { it.second }
                .filter(::isSafeRelay)
                .distinct()
                .take(MAX_RELAYS)
                .toList()
            val secret = pairs.firstOrNull { it.first == "secret" }
                ?.second
                ?.takeIf { it.length <= MAX_SECRET_CHARS }
            return BunkerUri(pubkey, relays, secret)
        }

        private fun decodeQuery(value: String): String? = runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrNull()

        private fun isSafeRelay(value: String): Boolean {
            if (value.length > MAX_RELAY_CHARS) return false
            val uri = runCatching { URI(value) }.getOrNull() ?: return false
            return uri.scheme.equals("wss", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null
        }
    }

    /** Omits the NIP-46 pairing secret from plaintext preferences. */
    fun withoutSecret(): String = buildString {
        append("bunker://")
        append(pubkeyHex)
        relays.forEachIndexed { index, relay ->
            append(if (index == 0) "?relay=" else "&relay=")
            append(URLEncoder.encode(relay, StandardCharsets.UTF_8.name()))
        }
    }
}

data class NostrSession(
    val pubkeyHex: String,
    val signerPackage: String,
    val bunkerUri: String = "",
    val wrapSecrets: Boolean = false,
)

class AmberBridge(private val context: Context) {
    fun isInstalled(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
        return context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }

    fun getPublicKeyIntent(): Intent {
        val permissions =
            """[{"type":"nip44_encrypt"},{"type":"nip44_decrypt"},{"type":"get_public_key"}]"""
        return Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
            putExtra("type", "get_public_key")
            putExtra("permissions", permissions)
        }
    }

    fun nip44EncryptIntent(plaintext: String, session: NostrSession): Intent {
        require(plaintext.length <= MAX_SIGNER_PAYLOAD_CHARS) { "signer payload is too large" }
        require(isValidSignerPackage(session.signerPackage)) { "invalid signer package" }
        return Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$plaintext")).apply {
            `package` = session.signerPackage
            putExtra("type", "nip44_encrypt")
            putExtra("pubkey", session.pubkeyHex)
            putExtra("current_user", session.pubkeyHex)
        }
    }

    fun nip44DecryptIntent(ciphertext: String, session: NostrSession): Intent {
        require(ciphertext.length <= MAX_SIGNER_PAYLOAD_CHARS) { "signer payload is too large" }
        require(isValidSignerPackage(session.signerPackage)) { "invalid signer package" }
        return Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$ciphertext")).apply {
            `package` = session.signerPackage
            putExtra("type", "nip44_decrypt")
            putExtra("pubkey", session.pubkeyHex)
            putExtra("current_user", session.pubkeyHex)
        }
    }

    fun parsePubkeyResult(data: Intent?): Pair<String, String>? {
        if (data == null) return null
        if (data.getBooleanExtra("rejected", false)) return null
        val pubkey = data.getStringExtra("result")?.lowercase()?.trim().orEmpty()
        val pkg = data.getStringExtra("package")?.trim().orEmpty()
        if (!isHexPubkey(pubkey) || !isValidSignerPackage(pkg)) return null
        return pubkey to pkg
    }

    fun parseCipherResult(data: Intent?): String? {
        if (data == null) return null
        if (data.getBooleanExtra("rejected", false)) return null
        return data.getStringExtra("result")
            ?.takeIf { it.length <= MAX_SIGNER_RESULT_CHARS }
            ?.ifBlank { null }
    }

    fun decryptViaResolver(ciphertext: String, session: NostrSession): String? {
        if (ciphertext.length > MAX_SIGNER_PAYLOAD_CHARS) return null
        val pkg = session.signerPackage.takeIf(::isValidSignerPackage) ?: return null
        val uri = Uri.parse("content://$pkg.NIP44_DECRYPT")
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ciphertext, session.pubkeyHex, session.pubkeyHex),
            null,
            null,
            null,
        ) ?: return null
        cursor.use { rows ->
            if (rows.getColumnIndex("rejected") > -1) return null
            if (!rows.moveToFirst()) return null
            val idx = rows.getColumnIndex("result")
            if (idx < 0) return null
            return rows.getString(idx)
                ?.takeIf { it.length <= MAX_SIGNER_RESULT_CHARS }
                ?.ifBlank { null }
        }
    }
}

internal fun isHexPubkey(value: String): Boolean =
    value.length == 64 && value.all { it in 'a'..'f' || it in '0'..'9' }

internal fun isValidSignerPackage(value: String): Boolean =
    value.length in 3..255 && ANDROID_PACKAGE.matches(value)

fun looksLikeNsec(value: String): Boolean =
    value.trim().startsWith("nsec1", ignoreCase = true)

fun looksLikeAccountPassword(value: String): Boolean =
    value.contains('@') && !value.trim().startsWith("sk-") &&
        !value.trim().startsWith("xai-")

private val ANDROID_PACKAGE =
    Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
private const val MAX_BUNKER_URI_CHARS = 16 * 1024
private const val MAX_QUERY_PARTS = 64
private const val MAX_RELAYS = 16
private const val MAX_RELAY_CHARS = 2_048
private const val MAX_SECRET_CHARS = 4_096
private const val MAX_SIGNER_PAYLOAD_CHARS = 256 * 1024
private const val MAX_SIGNER_RESULT_CHARS = 512 * 1024
