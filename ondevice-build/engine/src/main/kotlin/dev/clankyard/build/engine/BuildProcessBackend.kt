package dev.clankyard.build.engine

import dev.clankyard.build.runtime.LinkerExec
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

class BuildProcessBackend(
    private val jail: File,
    private val interceptor: File? = null,
) : ExecutionBackend {
    override val id: String = "build"
    override val displayName: String = "Build process"
    override val capabilities = ExecutionCapabilities(
        pty = false,
        interactive = false,
        cwdRestrictedToAppFiles = true,
        canExecUserBinaries = true,
        honestLimitationMessage = "Build process (toolchain). Not the human sandbox shell.",
    )

    private val sessions = ConcurrentHashMap<SessionId, Process>()

    override fun start(session: ExecutionSessionRequest): Flow<ExecutionEvent> = flow {
        val cwd = session.cwd.canonicalFile
        val root = jail.canonicalFile
        if (cwd != root && !cwd.path.startsWith(root.path + File.separator)) {
            emit(ExecutionEvent.Error("cwd is outside the environment sandbox"))
            return@flow
        }
        val command = session.command.ifEmpty {
            emit(ExecutionEvent.Error("empty argv"))
            return@flow
        }
        val builder = ProcessBuilder(prepareCommand(command)).directory(cwd)
        if (session.mergeErrorStream) builder.redirectErrorStream(true)
        val env = builder.environment()
        session.env.forEach { (k, v) -> env[k] = v }
        interceptor?.let { so ->
            val merged = LinkerExec.preloadEnv(so, env.toMap())
            env.clear()
            env.putAll(merged)
        }
        val process = try {
            builder.start()
        } catch (e: Exception) {
            emit(ExecutionEvent.Error(e.message ?: "failed to start"))
            return@flow
        }
        sessions[session.sessionId] = process
        try {
            val buf = ByteArray(4096)
            val input = process.inputStream
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                emit(ExecutionEvent.Output(String(buf, 0, n, Charsets.UTF_8)))
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
        val process = sessions.remove(sessionId) ?: return
        process.destroy()
        withContext(Dispatchers.IO) {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    private fun prepareCommand(command: List<String>): List<String> {
        if (command.firstOrNull() == LinkerExec.LINKER64) return command
        val executable = command.firstOrNull()?.let { File(it) } ?: return command
        if (!executable.isAbsolute || !contains(jail, executable)) return command
        return LinkerExec.linkerArgv(executable, command.drop(1))
    }

    private fun contains(parent: File, child: File): Boolean {
        val parentPath = parent.canonicalPath
        val childPath = child.canonicalPath
        return childPath == parentPath || childPath.startsWith(parentPath + File.separator)
    }
}
