package dev.clankyard.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri

data class BunkerUri(
    val pubkeyHex: String,
    val relays: List<String>,
    val secret: String?,
) {
    companion object {
        fun parse(raw: String): BunkerUri? {
            val trimmed = raw.trim()
            if (looksLikeNsec(trimmed)) return null
            if (!trimmed.startsWith("bunker://", ignoreCase = true)) return null
            val rest = trimmed.substringAfter("://")
            val body = rest.substringBefore("?")
            val query = rest.substringAfter("?", missingDelimiterValue = "")
            val pubkey = body.substringBefore("/").lowercase().filter { it in 'a'..'f' || it in '0'..'9' }
            if (pubkey.length != 64) return null
            val pairs = query.split("&").mapNotNull { part ->
                val key = part.substringBefore("=")
                val value = part.substringAfter("=", missingDelimiterValue = "")
                if (key.isEmpty()) null else key to decodeQuery(value)
            }
            val relays = pairs.filter { it.first == "relay" }.map { it.second }.filter { it.startsWith("wss://") }
            val secret = pairs.firstOrNull { it.first == "secret" }?.second
            return BunkerUri(pubkey, relays, secret)
        }

        private fun decodeQuery(value: String): String =
            value.replace("%3A", ":", ignoreCase = true)
                .replace("%2F", "/", ignoreCase = true)
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
        return Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$plaintext")).apply {
            `package` = session.signerPackage.ifBlank { null }
            putExtra("type", "nip44_encrypt")
            putExtra("pubkey", session.pubkeyHex)
            putExtra("current_user", session.pubkeyHex)
        }
    }

    fun nip44DecryptIntent(ciphertext: String, session: NostrSession): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$ciphertext")).apply {
            `package` = session.signerPackage.ifBlank { null }
            putExtra("type", "nip44_decrypt")
            putExtra("pubkey", session.pubkeyHex)
            putExtra("current_user", session.pubkeyHex)
        }
    }

    fun parsePubkeyResult(data: Intent?): Pair<String, String>? {
        if (data == null) return null
        if (data.getBooleanExtra("rejected", false)) return null
        val pubkey = data.getStringExtra("result")?.lowercase()?.trim().orEmpty()
        val pkg = data.getStringExtra("package").orEmpty()
        if (pubkey.length != 64) return null
        if (pubkey.any { it !in 'a'..'f' && it !in '0'..'9' }) return null
        return pubkey to pkg
    }

    fun parseCipherResult(data: Intent?): String? {
        if (data == null) return null
        if (data.getBooleanExtra("rejected", false)) return null
        return data.getStringExtra("result")?.ifBlank { null }
    }

    fun decryptViaResolver(ciphertext: String, session: NostrSession): String? {
        val pkg = session.signerPackage.ifBlank { return null }
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
            return rows.getString(idx)?.ifBlank { null }
        }
    }
}

fun looksLikeNsec(value: String): Boolean =
    value.trim().startsWith("nsec1", ignoreCase = true)

fun looksLikeAccountPassword(value: String): Boolean =
    value.contains('@') && !value.trim().startsWith("sk-") &&
        !value.trim().startsWith("xai-")
