package dev.clankyard.terminal.ssh

import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionBackend
import dev.clankyard.terminal.api.ExecutionCapabilities
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.terminal.api.ExecutionSessionRequest
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.PublicKey
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannel
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.digest.BuiltinDigests
import org.apache.sshd.common.keyprovider.KeyIdentityProvider

class SshExecutionBackend(
    private val load: suspend () -> SshConnectRequest?,
    private val onUnknownHost: (String) -> Unit = {},
) : ExecutionBackend {
    override val id: String = "ssh"
    override val displayName: String = "Remote SSH"
    override val capabilities = ExecutionCapabilities(
        pty = true,
        interactive = true,
        cwdRestrictedToAppFiles = false,
        canExecUserBinaries = true,
        honestLimitationMessage = HONEST,
    )

    private val sessions = ConcurrentHashMap<SessionId, Live>()

    override fun start(session: ExecutionSessionRequest): Flow<ExecutionEvent> = flow {
        val req = load()
        if (req == null || req.host.isBlank() || req.username.isBlank()) {
            emit(ExecutionEvent.Error("Configure SSH host, user, and password in Settings."))
            return@flow
        }
        val password = req.password
        if (password.isNullOrEmpty()) {
            emit(ExecutionEvent.Error("Save an SSH password in Settings."))
            return@flow
        }
        val client = SshClient.setUpDefaultClient()
        val seen = AtomicReference<String?>(null)
        client.keyIdentityProvider = KeyIdentityProvider.EMPTY_KEYS_PROVIDER
        client.serverKeyVerifier = ServerKeyVerifier { _, _, key ->
            val fp = fingerprint(key)
            seen.set(fp)
            fingerprintsMatch(fp, req.fingerprint)
        }
        var live: Live? = null
        try {
            client.start()
            val remote = connect(client, req, password)
            emit(ExecutionEvent.Output("$HONEST\nhost: ${req.username}@${req.host}:${req.port}\n"))
            if (session.command.isEmpty()) {
                live = openShell(remote, client, session, req)
                sessions[session.sessionId] = live
                pump(live.stdout).forEach { emit(ExecutionEvent.Output(it)) }
                emit(ExecutionEvent.Exit(live.channel.exitStatus ?: 0))
            } else {
                val (text, code) = exec(remote, session)
                if (text.isNotEmpty()) emit(ExecutionEvent.Output(text))
                emit(ExecutionEvent.Exit(code))
            }
        } catch (e: Exception) {
            val fp = seen.get()
            emit(hostKeyError(req, fp) ?: ExecutionEvent.Error(e.message ?: "SSH failed"))
        } finally {
            val held = sessions.remove(session.sessionId)
            if (held != null) held.close()
            else runCatching { client.stop() }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun writeStdin(sessionId: SessionId, bytes: ByteArray) {
        val live = sessions[sessionId] ?: return
        withContext(Dispatchers.IO) {
            live.stdin.write(bytes)
            live.stdin.flush()
        }
    }

    override suspend fun destroy(sessionId: SessionId) {
        sessions.remove(sessionId)?.close()
    }

    private fun connect(
        client: SshClient,
        req: SshConnectRequest,
        password: String,
    ): ClientSession {
        val remote = client.connect(req.username, req.host, req.port)
            .verify(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .session
        remote.addPasswordIdentity(password)
        remote.auth().verify(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return remote
    }

    private fun exec(remote: ClientSession, session: ExecutionSessionRequest): Pair<String, Int> {
        val stdout = ByteArrayOutputStream()
        val channel = remote.createExecChannel(session.command.joinToString(" "))
        channel.out = stdout
        channel.err = stdout
        channel.open().verify(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), TIMEOUT_MS)
        return stdout.toString(Charsets.UTF_8) to (channel.exitStatus ?: 0)
    }

    private fun openShell(
        remote: ClientSession,
        client: SshClient,
        session: ExecutionSessionRequest,
        req: SshConnectRequest,
    ): Live {
        val channel = remote.createShellChannel()
        channel.setPtyType("dumb")
        channel.setPtyColumns(session.cols)
        channel.setPtyLines(session.rows)
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut, PIPE)
        channel.out = pipeOut
        channel.err = pipeOut
        channel.open().verify(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        channel.addCloseFutureListener { runCatching { pipeOut.close() } }
        val stdin = channel.invertedIn
        val cwd = req.remoteCwd?.trim().orEmpty()
        if (cwd.isNotEmpty()) {
            stdin.write(("cd ${posixQuote(cwd)}\n").toByteArray())
            stdin.flush()
        }
        return Live(client, remote, channel, stdin, pipeIn)
    }

    private fun pump(stdout: PipedInputStream): Sequence<String> = sequence {
        stdout.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                yield(line + "\n")
            }
        }
    }

    private fun hostKeyError(req: SshConnectRequest, fp: String?): ExecutionEvent.Error? {
        if (fp.isNullOrBlank()) return null
        if (fingerprintsMatch(fp, req.fingerprint)) return null
        if (req.fingerprint.isNullOrBlank()) {
            onUnknownHost(fp)
            return ExecutionEvent.Error("Untrusted host $fp — Trust this host in Settings.")
        }
        return ExecutionEvent.Error("Host key mismatch. Expected ${req.fingerprint}, got $fp")
    }

    private class Live(
        val client: SshClient,
        val session: ClientSession,
        val channel: ClientChannel,
        val stdin: OutputStream,
        val stdout: PipedInputStream,
    ) {
        fun close() {
            runCatching { channel.close(true) }
            runCatching { session.close(true) }
            runCatching { client.stop() }
            runCatching { stdout.close() }
        }
    }

    companion object {
        const val HONEST =
            "Remote SSH session. Commands run on the host you configured, not on this tablet."
        const val TIMEOUT_MS = 15_000L
        private const val PIPE = 32 * 1024

        fun fingerprint(key: PublicKey): String =
            KeyUtils.getFingerPrint(BuiltinDigests.sha256, key)

        fun fingerprintsMatch(actual: String, expected: String?): Boolean {
            if (expected.isNullOrBlank()) return false
            return normalize(actual) == normalize(expected)
        }

        fun posixQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        private fun normalize(value: String): String =
            value.trim().removePrefix("SHA256:").lowercase()
    }
}
