package dev.clankyard.app

import dev.clankyard.feature.clanker.ClankerUiState
import dev.clankyard.terminal.local.LocalProcessBackend
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CLANK-038 — empty-AI dogfood contract.
 *
 * With no keys the workshop still edits, searches, commits, and runs the
 * sandbox shell. The Clanker pane explains it is unconfigured.
 */
class EmptyAiContractTest {
    @Test
    fun clankerStartsUnconfigured() {
        val state = ClankerUiState()
        assertTrue(state.needsKey)
        assertTrue(state.lines.isEmpty())
    }

    @Test
    fun localShellAdvertisesSandboxNotDistro() {
        val message = LocalProcessBackend().capabilities.honestLimitationMessage
        assertTrue(message.contains("sandbox shell"))
        assertTrue(message.contains("not a Linux distro"))
    }
}
