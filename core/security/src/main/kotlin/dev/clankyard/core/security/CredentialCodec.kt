package dev.clankyard.core.security

import dev.clankyard.core.model.Credential
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal object CredentialCodec {
    private const val KIND_API_KEY: Byte = 1
    private const val KIND_OAUTH: Byte = 2

    fun encode(credential: Credential): ByteArray {
        val buf = ByteArrayOutputStream()
        DataOutputStream(buf).use { out ->
            when (credential) {
                is Credential.ApiKey -> {
                    out.writeByte(KIND_API_KEY.toInt())
                    out.writeUTF(credential.secret)
                }
                is Credential.OAuthToken -> {
                    out.writeByte(KIND_OAUTH.toInt())
                    out.writeUTF(credential.accessToken)
                    out.writeUTF(credential.refreshToken.orEmpty())
                    out.writeLong(credential.expiresAtEpochMs ?: -1L)
                }
            }
        }
        return buf.toByteArray()
    }

    fun decode(bytes: ByteArray): Credential {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return when (val kind = input.readByte()) {
                KIND_API_KEY -> Credential.ApiKey(input.readUTF())
                KIND_OAUTH -> oauth(input)
                else -> error("unknown credential kind")
            }
        }
    }

    private fun oauth(input: DataInputStream): Credential.OAuthToken {
        val access = input.readUTF()
        val refresh = input.readUTF().ifEmpty { null }
        val expires = input.readLong().let { if (it < 0) null else it }
        return Credential.OAuthToken(access, refresh, expires)
    }
}
