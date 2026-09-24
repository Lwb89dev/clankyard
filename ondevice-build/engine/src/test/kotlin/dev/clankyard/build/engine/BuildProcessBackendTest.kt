package dev.clankyard.build.engine

import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.terminal.api.ExecutionSessionRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildProcessBackendTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun streamsRawProgressAndBuildEnvironment() = runBlocking {
        val root = tmp.newFolder("environment")
        val backend = BuildProcessBackend(root)
        val events = backend.start(
            ExecutionSessionRequest(
                sessionId = SessionId("progress"),
                cwd = root,
                command = listOf("/bin/sh", "-c", "printf 'x\\ry'; printf '%s' \"\$GRADLE_USER_HOME\""),
                env = mapOf("GRADLE_USER_HOME" to File(root, "cache").absolutePath),
                emitLimitationBanner = false,
            ),
        ).toList()

        val output = events.filterIsInstance<ExecutionEvent.Output>().joinToString("") { it.text }
        assertTrue(output.contains("x\ry"))
        assertTrue(output.contains(File(root, "cache").absolutePath))
    }

    @Test
    fun rejectsWorkingDirectoryOutsideEnvironment() = runBlocking {
        val root = tmp.newFolder("environment")
        val events = BuildProcessBackend(root).start(
            ExecutionSessionRequest(
                sessionId = SessionId("outside"),
                cwd = tmp.newFolder("outside"),
                command = listOf("/bin/sh", "-c", "echo should-not-run"),
                emitLimitationBanner = false,
            ),
        ).toList()

        assertTrue(events.any { it is ExecutionEvent.Error })
        assertTrue(events.none { it is ExecutionEvent.Output })
    }
}
