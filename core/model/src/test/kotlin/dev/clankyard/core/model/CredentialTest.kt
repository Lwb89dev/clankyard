package dev.clankyard.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialTest {
    @Test
    fun apiKeyToStringHidesSecret() {
        val key = Credential.ApiKey("sk-live-super-secret")
        assertEquals("ApiKey(****)", key.toString())
        assertFalse(key.toString().contains("sk-live"))
        assertFalse(key.toString().contains("super-secret"))
    }

    @Test
    fun oauthTokenToStringHidesSecret() {
        val token = Credential.OAuthToken("access-secret", "refresh-secret", 1L)
        assertEquals("OAuthToken(****)", token.toString())
        assertFalse(token.toString().contains("access-secret"))
    }

    @Test
    fun firstPartyLlmSlots() {
        listOf("llm.openai.default", "llm.anthropic.default", "llm.xai.default").forEach { id ->
            val slot = CredentialSlotId(id)
            assertTrue(slot.isLlm)
            assertFalse(slot.isGit)
            assertEquals(id, slot.value)
        }
    }

    @Test
    fun compatibleAndGitSlots() {
        val compatible = CredentialSlotId("llm.openai-compatible.api.example.com")
        assertTrue(compatible.isLlm)
        val git = CredentialSlotId("git.https.github.com")
        assertTrue(git.isGit)
        assertFalse(git.isLlm)
    }

    @Test
    fun rejectIllegalSlotIds() {
        listOf(
            "",
            "llm.openai.custom",
            "LLM.openai.default",
            "llm.OpenAI.default",
            "llm.openai-compatible.https://api.example.com",
            "llm.openai-compatible.api.example.com/v1",
            "git.ssh.github.com",
            "git.https.GitHub.com",
            "openai.default",
        ).forEach { id ->
            assertThrows("should reject $id", IllegalArgumentException::class.java) {
                CredentialSlotId(id)
            }
        }
    }
}
