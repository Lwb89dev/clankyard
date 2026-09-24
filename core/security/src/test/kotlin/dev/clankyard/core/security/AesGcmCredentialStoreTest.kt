package dev.clankyard.core.security

import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AesGcmCredentialStoreTest {
    private val openai = CredentialSlotId("llm.openai.default")
    private val anthropic = CredentialSlotId("llm.anthropic.default")

    @Test
    fun putGetDeleteRoundTrip() = runBlocking {
        withTempStore { store, _ ->
            store.put(openai, Credential.ApiKey("sk-live-super-secret"))
            assertEquals("sk-live-super-secret", (store.get(openai) as Credential.ApiKey).secret)
            store.delete(openai)
            assertNull(store.get(openai))
        }
    }

    @Test
    fun ciphertextIsNotPlaintext() = runBlocking {
        withTempStore { store, dir ->
            val secret = "sk-live-super-secret"
            store.put(openai, Credential.ApiKey(secret))
            val blob = File(dir, openai.value + AesGcmCredentialStore.BLOB_SUFFIX)
            assertTrue(blob.isFile)
            val bytes = blob.readBytes()
            assertEquals('C'.code.toByte(), bytes[0])
            assertEquals('Y'.code.toByte(), bytes[1])
            assertEquals('C'.code.toByte(), bytes[2])
            assertEquals('1'.code.toByte(), bytes[3])
            val asLatin1 = bytes.toString(Charsets.ISO_8859_1)
            assertFalse(asLatin1.contains(secret))
            assertFalse(blob.readText(Charsets.UTF_8).contains(secret))
        }
    }

    @Test
    fun aadBindsCiphertextToSlot() = runBlocking {
        withTempStore { store, dir ->
            store.put(openai, Credential.ApiKey("sk-live-super-secret"))
            File(dir, openai.value + AesGcmCredentialStore.BLOB_SUFFIX)
                .copyTo(File(dir, anthropic.value + AesGcmCredentialStore.BLOB_SUFFIX))
            assertThrows(SecurityException::class.java) {
                runBlocking { store.get(anthropic) }
            }
            assertEquals("sk-live-super-secret", (store.get(openai) as Credential.ApiKey).secret)
        }
    }

    @Test
    fun garbageBlobThrowsSecurityExceptionWithoutPlaintext() = runBlocking {
        withTempStore { store, dir ->
            val leak = "sk-live-super-secret"
            File(dir, openai.value + AesGcmCredentialStore.BLOB_SUFFIX)
                .writeBytes("not-a-blob-$leak".toByteArray())
            val error = assertThrows(SecurityException::class.java) {
                runBlocking { store.get(openai) }
            }
            assertTrue(error.message!!.contains(openai.value))
            assertFalse(error.message!!.contains(leak))
        }
    }

    @Test
    fun listSlotsSkipsUndecryptableBlobs() = runBlocking {
        withTempStore { store, dir ->
            store.put(openai, Credential.ApiKey("sk-live-super-secret"))
            File(dir, "llm.xai.default.bin").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            File(dir, openai.value + AesGcmCredentialStore.BLOB_SUFFIX)
                .copyTo(File(dir, anthropic.value + AesGcmCredentialStore.BLOB_SUFFIX))
            assertEquals(listOf(openai), store.listSlots().map { it.id })
        }
    }

    @Test
    fun overwriteAndListSlots() = runBlocking {
        withTempStore { store, _ ->
            store.put(openai, Credential.ApiKey("sk-old-secret-key"))
            store.put(openai, Credential.ApiKey("sk-new-secret-key"))
            store.put(anthropic, Credential.ApiKey("sk-ant-secret-key"))
            assertEquals("sk-new-secret-key", (store.get(openai) as Credential.ApiKey).secret)
            val ids = store.listSlots().map { it.id }
            assertEquals(listOf(anthropic, openai), ids)
        }
    }

    @Test
    fun nip44WrapRoundTrip() = runBlocking {
        withTempStore { store, _ ->
            store.put(openai, Credential.Nip44Wrap("cipher-from-amber"))
            val got = store.get(openai) as Credential.Nip44Wrap
            assertEquals("cipher-from-amber", got.ciphertext)
            assertEquals("Nip44Wrap(****)", got.toString())
        }
    }

    @Test
    fun oauthTokenRoundTripStillRedacted() = runBlocking {
        withTempStore { store, dir ->
            val token = Credential.OAuthToken("access-secret", "refresh-secret", 99L)
            store.put(openai, token)
            val got = store.get(openai) as Credential.OAuthToken
            assertEquals("access-secret", got.accessToken)
            assertEquals("OAuthToken(****)", got.toString())
            assertFalse(
                File(dir, openai.value + AesGcmCredentialStore.BLOB_SUFFIX)
                    .readBytes()
                    .toString(Charsets.ISO_8859_1)
                    .contains("access-secret"),
            )
        }
    }

    private suspend fun withTempStore(block: suspend (AesGcmCredentialStore, File) -> Unit) {
        val dir = File(System.getProperty("java.io.tmpdir"), "clankyard-creds-${UUID.randomUUID()}")
        check(dir.mkdirs()) { "could not create $dir" }
        try {
            block(AesGcmCredentialStore(dir, softwareAes256()), dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun softwareAes256(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        return generator.generateKey()
    }
}
