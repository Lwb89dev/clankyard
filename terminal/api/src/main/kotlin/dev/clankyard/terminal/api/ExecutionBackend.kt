package dev.clankyard.terminal.api

import dev.clankyard.core.model.SessionId
import java.io.File
import kotlinx.coroutines.flow.Flow

data class ExecutionCapabilities(
    val pty: Boolean,
    val interactive: Boolean,
    val cwdRestrictedToAppFiles: Boolean,
    val canExecUserBinaries: Boolean,
    val honestLimitationMessage: String,
)

data class ExecutionSessionRequest(
    val sessionId: SessionId,
    val cwd: File,
    val command: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val cols: Int = 80,
    val rows: Int = 24,
    val pty: Boolean = false,
    val emitLimitationBanner: Boolean = true,
    val mergeErrorStream: Boolean = true,
)

sealed interface ExecutionEvent {
    data class Output(val text: String) : ExecutionEvent
    data class Exit(val code: Int) : ExecutionEvent
    data class Error(val message: String) : ExecutionEvent
}

interface ExecutionBackend {
    val id: String
    val displayName: String
    val capabilities: ExecutionCapabilities
    fun start(session: ExecutionSessionRequest): Flow<ExecutionEvent>
    suspend fun writeStdin(sessionId: SessionId, bytes: ByteArray)
    suspend fun destroy(sessionId: SessionId)
}
