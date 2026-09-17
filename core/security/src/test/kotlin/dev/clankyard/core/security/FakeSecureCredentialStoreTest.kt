package dev.clankyard.core.security

import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSecureCredentialStoreTest {
    private val openai = CredentialSlotId("llm.openai.default")
    private val anthropic = CredentialSlotId("llm.anthropic.default")
    private val compatible = CredentialSlotId("llm.openai-compatible.api.example.com")
    private val git = CredentialSlotId("git.https.github.com")

    @Test
    fun putGetDelete() = runBlocking {
        val store = FakeSecureCredentialStore()
        val secret = Credential.ApiKey("sk-live-super-secret")
        assertNull(store.get(openai))
        store.put(openai, secret)
        val got = store.get(openai) as Credential.ApiKey
        assertEquals("sk-live-super-secret", got.secret)
        store.delete(openai)
        assertNull(store.get(openai))
        store.delete(openai)
    }

    @Test
    fun slotNamespacesAreIndependent() = runBlocking {
        val store = FakeSecureCredentialStore()
        store.put(openai, Credential.ApiKey("sk-openai-secret-key"))
        store.put(anthropic, Credential.ApiKey("sk-ant-secret-key"))
        store.put(compatible, Credential.ApiKey("compat-secret-key"))
        store.put(git, Credential.ApiKey("ghp_git_secret_token"))

        val slots = store.listSlots()
        assertEquals(4, slots.size)
        assertEquals("openai", slots.single { it.id == openai }.providerHint)
        assertEquals("openai-compatible", slots.single { it.id == compatible }.providerHint)
        assertEquals("api.example.com", slots.single { it.id == compatible }.label)
        assertEquals("git", slots.single { it.id == git }.providerHint)
        assertTrue(openai.isLlm)
        assertTrue(git.isGit)
        assertFalse(git.isLlm)

        store.delete(openai)
        assertNull(store.get(openai))
        assertEquals("sk-ant-secret-key", (store.get(anthropic) as Credential.ApiKey).secret)
        assertEquals("ghp_git_secret_token", (store.get(git) as Credential.ApiKey).secret)
    }

    @Test
    fun secretNeverAppearsInToString() = runBlocking {
        val store = FakeSecureCredentialStore()
        val secret = "sk-live-super-secret"
        val cred = Credential.ApiKey(secret)
        assertEquals("ApiKey(****)", cred.toString())
        assertFalse(cred.toString().contains(secret))
        store.put(openai, cred)
        assertFalse(store.get(openai).toString().contains(secret))
        assertFalse(store.listSlots().toString().contains(secret))
        val oauth = Credential.OAuthToken("access-secret", "refresh-secret", 1L)
        store.put(git, oauth)
        assertEquals("OAuthToken(****)", store.get(git).toString())
        assertFalse(store.get(git).toString().contains("access-secret"))
    }
}
