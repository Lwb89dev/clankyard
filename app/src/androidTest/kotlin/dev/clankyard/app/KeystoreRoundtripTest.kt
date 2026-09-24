package dev.clankyard.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId
import dev.clankyard.core.security.androidKeystoreCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** CLANK-034 — device Android Keystore AES-256-GCM roundtrip. */
@RunWith(AndroidJUnit4::class)
class KeystoreRoundtripTest {
    @Test
    fun putGetDeleteOnAndroidKeystore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = androidKeystoreCredentialStore(context)
        val slot = CredentialSlotId("llm.test.default")
        val secret = "sk-test-clank034-secret"
        try {
            store.put(slot, Credential.ApiKey(secret))
            val got = store.get(slot) as Credential.ApiKey
            assertEquals(secret, got.secret)
            assertFalse(got.toString().contains(secret))
        } finally {
            store.delete(slot)
            assertNull(store.get(slot))
        }
    }
}
