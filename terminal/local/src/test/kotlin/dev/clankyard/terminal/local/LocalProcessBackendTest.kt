package dev.clankyard.terminal.local

import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.terminal.api.ExecutionSessionRequest
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
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
        assertTrue(text.contains("Sandbox"))
    }

    @Test
    fun bannerSkippedWhenFlagFalse() = runBlocking {
        val cwd = tmp.newFolder("nobanner")
        val events = withTimeout(10_000) {
            LocalProcessBackend().start(
                ExecutionSessionRequest(
                    sessionId = SessionId("s-nb"),
                    cwd = cwd,
                    command = listOf(LocalProcessBackend.defaultShell(), "-c", "echo quiet"),
                    emitLimitationBanner = false,
                ),
            ).toList()
        }
        val text = events.filterIsInstance<ExecutionEvent.Output>().joinToString("") { it.text }
        assertTrue(text.contains("quiet"))
        assertFalse(text.contains("Sandbox"))
    }

    @Test
    fun oneShotLsListsWorkshopFiles() = runBlocking {
        val cwd = tmp.newFolder("cwd")
        File(cwd, "hello.txt").writeText("x")
        val backend = LocalProcessBackend(jail = cwd)
        val events = withTimeout(10_000) {
            backend.start(
                ExecutionSessionRequest(
                    sessionId = SessionId("s2"),
                    cwd = cwd,
                    command = listOf(LocalProcessBackend.defaultShell(), "-c", "ls"),
                ),
            ).toList()
        }
        val text = events.filterIsInstance<ExecutionEvent.Output>().joinToString("") { it.text }
        assertTrue(text.contains("hello.txt"))
        assertFalse(text.contains("bind: not found"))
    }

    @Test
    fun cwdOutsideJailIsRejected() = runBlocking {
        val jail = tmp.newFolder("jail")
        val outside = tmp.newFolder("outside")
        val backend = LocalProcessBackend(jail = jail)
        val events = backend.start(
            ExecutionSessionRequest(SessionId("s3"), cwd = outside, command = listOf("echo", "nope")),
        ).toList()
        assertTrue(events.any { it is ExecutionEvent.Error && it.message.contains("outside") })
    }

    @Test
    fun cwdIsTheWorkshopRoot() {
        val cwd = File("/tmp")
        val request = ExecutionSessionRequest(SessionId("s"), cwd)
        assertTrue(request.cwd == cwd)
        assertTrue(LocalProcessBackend().capabilities.honestLimitationMessage.contains("Linux distro"))
    }
}
