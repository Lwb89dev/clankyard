package dev.clankyard.terminal.local

import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.terminal.api.ExecutionSessionRequest
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalProcessBackendTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun echoThenExit() = runBlocking {
        val backend = LocalProcessBackend()
        val cwd = tmp.newFolder("cwd")
        val events = withTimeout(10_000) {
            backend.start(
                ExecutionSessionRequest(
                    sessionId = SessionId("s1"),
                    cwd = cwd,
                    command = listOf(LocalProcessBackend.defaultShell(), "-c", "echo clankyard-ok"),
                ),
            ).toList()
        }
        val text = events.filterIsInstance<ExecutionEvent.Output>().joinToString("") { it.text }
        assertTrue(text.contains("clankyard-ok"))
        assertTrue(events.any { it is ExecutionEvent.Exit })
        assertTrue(text.contains("sandbox shell"))
    }

    @Test
    fun cwdIsTheWorkshopRoot() {
        val cwd = File("/tmp")
        val request = ExecutionSessionRequest(SessionId("s"), cwd)
        assertTrue(request.cwd == cwd)
        assertTrue(LocalProcessBackend().capabilities.honestLimitationMessage.contains("not a Linux distro"))
    }
}
