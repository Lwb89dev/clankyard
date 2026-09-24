package dev.clankyard.terminal.api

import dev.clankyard.core.model.SessionId
import kotlinx.coroutines.flow.Flow

/** Routes start() to [secondary] when [useSecondary] is true. Stdin/destroy hit both. */
class SwitchingExecutionBackend(
    private val primary: ExecutionBackend,
    private val secondary: ExecutionBackend,
    private val useSecondary: () -> Boolean,
) : ExecutionBackend {
    private fun active(): ExecutionBackend = if (useSecondary()) secondary else primary

    override val id: String get() = active().id
    override val displayName: String get() = active().displayName
    override val capabilities: ExecutionCapabilities get() = active().capabilities

    override fun start(session: ExecutionSessionRequest): Flow<ExecutionEvent> =
        active().start(session)

    override suspend fun writeStdin(sessionId: SessionId, bytes: ByteArray) {
        primary.writeStdin(sessionId, bytes)
        secondary.writeStdin(sessionId, bytes)
    }

    override suspend fun destroy(sessionId: SessionId) {
        primary.destroy(sessionId)
        secondary.destroy(sessionId)
    }
}
