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

class LocalProcessBackend(
    private val jail: File? = null,
) : ExecutionBackend {
    override val id: String = "local"
    override val displayName: String = "Local sandbox"
    override val capabilities = ExecutionCapabilities(
        pty = false,
        interactive = false,
        cwdRestrictedToAppFiles = true,
        canExecUserBinaries = false,
        honestLimitationMessage = HONEST,
    )

    private val sessions = ConcurrentHashMap<SessionId, Process>()

    override fun start(session: ExecutionSessionRequest): Flow<ExecutionEvent> = flow {
        val cwd = resolveCwd(session.cwd)
        if (cwd == null) {
            emit(ExecutionEvent.Error("cwd is outside the environment sandbox"))
            return@flow
        }
        val command = if (session.command.isEmpty()) {
            listOf(defaultShell(), "-c", "pwd")
        } else {
            session.command
        }
        val builder = ProcessBuilder(command).directory(cwd)
        if (session.mergeErrorStream) builder.redirectErrorStream(true)
        val env = builder.environment()
        if (File("/system/bin/sh").canExecute()) {
            env["PATH"] = ANDROID_PATH
            env.remove("ENV")
        }
        env["HOME"] = cwd.absolutePath
        env["TMPDIR"] = cwd.absolutePath
        env["PWD"] = cwd.absolutePath
        session.env.forEach { (key, value) -> env[key] = value }
        val process = try {
            builder.start()
        } catch (e: Exception) {
            emit(ExecutionEvent.Error(e.message ?: "failed to start shell"))
            return@flow
        }
        sessions[session.sessionId] = process
        if (session.emitLimitationBanner) {
            emit(ExecutionEvent.Output("$HONEST\ncwd: ${cwd.path}\n"))
        }
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
        const val HONEST =
            "Local Sandbox shell: commands run in the app environment folder only (cwd reset each line). " +
                "This is a sandbox shell, not a Linux distro; no PTY, no chroot."
        private const val ANDROID_PATH =
            "/system/bin:/system/xbin:/vendor/bin:/vendor/xbin:/product/bin"

        fun defaultShell(): String {
            val android = File("/system/bin/sh")
            if (android.canExecute()) return android.path
            return "/bin/sh"
        }
    }

    private fun resolveCwd(requested: File): File? {
        val cwd = runCatching { requested.canonicalFile }.getOrNull() ?: return null
        val root = runCatching { (jail ?: cwd).canonicalFile }.getOrNull() ?: return null
        if (cwd == root) return cwd
        val prefix = root.path + File.separator
        if (!cwd.path.startsWith(prefix)) return null
        return cwd
    }
}
