package dev.clankyard.core.security

import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM file store. Production uses an Android Keystore key (see
 * [dev.clankyard.core.security.di.SecurityModule]); JVM tests pass a software
 * AES-256 key. Ciphertext is one file per slot; AAD is the slot id.
 * Not EncryptedSharedPreferences. Device Keystore roundtrip is CLANK-034.
 */
internal class AesGcmCredentialStore(
    private val directory: File,
    private val key: SecretKey,
) : SecureCredentialStore {
    private val mutex = Mutex()

    override suspend fun get(slot: CredentialSlotId): Credential? =
        ioLocked { read(slot) }

    override suspend fun put(slot: CredentialSlotId, credential: Credential) {
        ioLocked { write(slot, credential) }
    }

    override suspend fun delete(slot: CredentialSlotId) {
        ioLocked { fileFor(slot).delete() }
    }

    override suspend fun listSlots(): List<CredentialSlot> = ioLocked {
        val files = directory.listFiles() ?: return@ioLocked emptyList()
        files.mapNotNull { slotIfReadable(it) }.sortedBy { it.id.value }
    }

    private suspend fun <T> ioLocked(block: () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock(action = block) }

    private fun fileFor(slot: CredentialSlotId): File =
        File(directory, slot.value + BLOB_SUFFIX)

    private fun slotIfReadable(file: File): CredentialSlot? {
        if (!file.isFile || !file.name.endsWith(BLOB_SUFFIX) || file.name.startsWith(".")) return null
        val slotId = runCatching { CredentialSlotId(file.name.removeSuffix(BLOB_SUFFIX)) }.getOrNull()
            ?: return null
        return try {
            read(slotId) ?: return null
            slotId.toCredentialSlot()
        } catch (_: SecurityException) {
            null
        }
    }

    private fun read(slot: CredentialSlotId): Credential? {
        val file = fileFor(slot)
        if (!file.isFile) return null
        try {
            return decrypt(slot, file.readBytes())
        } catch (e: Exception) {
            throw SecurityException("credential slot ${slot.value} failed integrity check", e)
        }
    }

    private fun decrypt(slot: CredentialSlotId, bytes: ByteArray): Credential {
        val (iv, ciphertext) = unpack(bytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(slot.value.toByteArray(Charsets.UTF_8))
        return decode(cipher.doFinal(ciphertext))
    }

    private fun write(slot: CredentialSlotId, credential: Credential) {
        directory.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(slot.value.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(encode(credential))
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "expected $IV_BYTES-byte GCM IV" }
        atomicWrite(fileFor(slot), pack(iv, ciphertext))
    }

    private fun encode(credential: Credential): ByteArray {
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

    private fun decode(bytes: ByteArray): Credential {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return when (input.readByte()) {
                KIND_API_KEY -> Credential.ApiKey(input.readUTF())
                KIND_OAUTH -> Credential.OAuthToken(
                    accessToken = input.readUTF(),
                    refreshToken = input.readUTF().ifEmpty { null },
                    expiresAtEpochMs = input.readLong().let { if (it < 0) null else it },
                )
                else -> error("unknown credential kind")
            }
        }
    }

    private fun pack(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val out = ByteArray(5 + iv.size + ciphertext.size)
        out[0] = MAGIC_0
        out[1] = MAGIC_1
        out[2] = MAGIC_2
        out[3] = MAGIC_3
        out[4] = BLOB_VERSION
        iv.copyInto(out, 5)
        ciphertext.copyInto(out, 5 + iv.size)
        return out
    }

    private fun unpack(bytes: ByteArray): Pair<ByteArray, ByteArray> {
        require(bytes.size > 5 + IV_BYTES) { "credential blob too short" }
        require(
            bytes[0] == MAGIC_0 && bytes[1] == MAGIC_1 &&
                bytes[2] == MAGIC_2 && bytes[3] == MAGIC_3,
        ) { "unrecognized credential blob" }
        require(bytes[4] == BLOB_VERSION) { "unsupported credential blob version" }
        val iv = bytes.copyOfRange(5, 5 + IV_BYTES)
        val ciphertext = bytes.copyOfRange(5 + IV_BYTES, bytes.size)
        return iv to ciphertext
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val parent = target.parentFile ?: error("credential path has no parent")
        parent.mkdirs()
        val tmp = File(parent, ".${target.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(bytes)
            fos.flush()
            fos.fd.sync()
        }
        if (tmp.renameTo(target)) return
        tmp.copyTo(target, overwrite = true)
        tmp.delete()
    }

    internal companion object {
        const val BLOB_SUFFIX = ".bin"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KIND_API_KEY: Byte = 1
        private const val KIND_OAUTH: Byte = 2
        private const val MAGIC_0 = 'C'.code.toByte()
        private const val MAGIC_1 = 'Y'.code.toByte()
        private const val MAGIC_2 = 'C'.code.toByte()
        private const val MAGIC_3 = '1'.code.toByte()
        private const val BLOB_VERSION: Byte = 1
    }
}
