package dev.clankyard.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopSettingsStoreTest {
    @Test
    fun hostTokenStripsSchemeAndPath() {
        assertEquals("api.example.com", WorkshopSettingsStore.hostToken("https://api.example.com/v1"))
        assertEquals("custom", WorkshopSettingsStore.hostToken(""))
        assertEquals("localhost", WorkshopSettingsStore.hostToken("https://localhost:11434"))
    }

    @Test
    fun slotIdMatchesByokLayout() {
        assertEquals(
            "llm.openai.default",
            WorkshopSettingsStore.slotId(WorkshopSettings(provider = SettingsProvider.OpenAI)).value,
        )
        assertEquals(
            "llm.anthropic.default",
            WorkshopSettingsStore.slotId(WorkshopSettings(provider = SettingsProvider.Anthropic)).value,
        )
        assertEquals(
            "llm.xai.default",
            WorkshopSettingsStore.slotId(WorkshopSettings(provider = SettingsProvider.Xai)).value,
        )
        assertEquals(
            "llm.openai-compatible.api.example.com",
            WorkshopSettingsStore.slotId(
                WorkshopSettings(
                    provider = SettingsProvider.Compatible,
                    compatibleBaseUrl = "https://api.example.com/v1",
                ),
            ).value,
        )
        assertEquals(
            "ssh.build.example.com",
            WorkshopSettingsStore.sshSlot("build.example.com").value,
        )
        assertEquals(
            "ssh.127.0.0.1",
            WorkshopSettingsStore.sshSlot("127.0.0.1").value,
        )
    }

    @Test
    fun bunkerUriParsesAndRejectsNsec() {
        val uri = BunkerUri.parse(
            "bunker://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa?relay=wss://relay.example&secret=pair",
        )
        assertEquals(64, uri!!.pubkeyHex.length)
        assertEquals(listOf("wss://relay.example"), uri.relays)
        assertEquals("pair", uri.secret)
        assertEquals(null, BunkerUri.parse("nsec1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"))
        assertTrue(looksLikeNsec("nsec1abc"))
        assertTrue(looksLikeAccountPassword("user@example.com"))
        assertTrue(!looksLikeAccountPassword("sk-live-super-secret-key"))
    }
}
