package dev.clankyard.feature.terminal

import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionBackend
import dev.clankyard.terminal.api.ExecutionCapabilities
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.terminal.api.ExecutionSessionRequest
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TerminalSessionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun submittedCommandRunsInSelectedWorkspaceDirectory() = runTest {
        val workspace = tmp.newFolder("selected-workspace")
        val backend = RecordingBackend()
        val session = TerminalSession(backend, { workspace }, this)

        session.start()
        advanceUntilIdle()
        session.onInput("pwd")
        session.submit()
        advanceUntilIdle()

        val request = backend.requests.last()
        assertEquals(workspace.canonicalFile, request.cwd.canonicalFile)
        assertEquals("pwd", request.command.last())
        assertTrue(session.state.value.output.contains("cwd=${workspace.canonicalPath}"))
        assertTrue(session.state.value.output.contains("[exit 0]"))
        assertFalse(session.state.value.running)
    }

    private class RecordingBackend : ExecutionBackend {
        val requests = mutableListOf<ExecutionSessionRequest>()

        override val id = "recording"
        override val displayName = "Recording"
        override val capabilities = ExecutionCapabilities(
            pty = false,
            interactive = false,
            cwdRestrictedToAppFiles = true,
            canExecUserBinaries = false,
            honestLimitationMessage = "test backend",
        )

        override fun start(session: ExecutionSessionRequest): Flow<ExecutionEvent> = flow {
            requests += session
            emit(ExecutionEvent.Output("cwd=${session.cwd.canonicalPath}\n"))
            emit(ExecutionEvent.Exit(0))
        }

        override suspend fun writeStdin(sessionId: SessionId, bytes: ByteArray) = Unit

        override suspend fun destroy(sessionId: SessionId) = Unit
    }
}
