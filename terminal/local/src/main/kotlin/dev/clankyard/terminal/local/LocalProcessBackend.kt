package dev.clankyard.terminal.local

import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionBackend
import dev.clankyard.terminal.api.ExecutionCapabilities
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.terminal.api.ExecutionSessionRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class LocalProcessBackend : ExecutionBackend {
    override val id: String = "local"
    override val displayName: String = "Local sandbox"
    override val capabilities = ExecutionCapabilities(
        pty = false,
        interactive = true,
        cwdRestrictedToAppFiles = true,
        canExecUserBinaries = false,
        honestLimitationMessage = HONEST,
    )

    private val sessions = ConcurrentHashMap<SessionId, Process>()

    override fun start(session: ExecutionSessionRequest): Flow<ExecutionEvent> = flow {
        val command = session.command.ifEmpty { listOf(defaultShell(), "-i") }
        val builder = ProcessBuilder(command)
            .directory(session.cwd)
            .redirectErrorStream(true)
        session.env.forEach { (key, value) -> builder.environment()[key] = value }
        val process = try {
            builder.start()
        } catch (e: Exception) {
            emit(ExecutionEvent.Error(e.message ?: "failed to start shell"))
            return@flow
        }
        sessions[session.sessionId] = process
        emit(ExecutionEvent.Output("$HONEST\ncwd: ${session.cwd.path}\n"))
        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    emit(ExecutionEvent.Output(line + "\n"))
                }
            }
            emit(ExecutionEvent.Exit(process.waitFor()))
        } finally {
            process.destroy()
            sessions.remove(session.sessionId, process)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun writeStdin(sessionId: SessionId, bytes: ByteArray) {
        val process = sessions[sessionId] ?: return
        withContext(Dispatchers.IO) {
            process.outputStream.write(bytes)
            process.outputStream.flush()
        }
    }

    override suspend fun destroy(sessionId: SessionId) {
        sessions.remove(sessionId)?.destroy()
    }

    companion object {
        const val HONEST = "This is a sandbox shell, not a Linux distro."

        fun defaultShell(): String {
            val android = File("/system/bin/sh")
            if (android.canExecute()) return android.path
            return "/bin/sh"
        }
    }
}
