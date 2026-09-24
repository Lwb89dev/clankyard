package dev.clankyard.terminal.ssh

import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.terminal.api.ExecutionSessionRequest
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SshExecutionBackendTest {
    private lateinit var server: SshServer
    private lateinit var fingerprint: String

    @Before
    fun startServer() {
        val keys = SimpleGeneratorHostKeyProvider(Files.createTempFile("clankyard-ssh", ".ser"))
        server = SshServer.setUpDefaultServer()
        server.port = 0
        server.keyPairProvider = keys
        server.passwordAuthenticator = PasswordAuthenticator { user, password, _ ->
            user == "tester" && password == "secret12"
        }
        server.commandFactory = CommandFactory { _, command -> OneShotCommand(command) }
        server.start()
        fingerprint = SshExecutionBackend.fingerprint(keys.loadKeys(null).first().public)
    }

    @After
    fun stopServer() {
        runCatching { server.stop() }
    }

    @Test
    fun echoThenExitWithTrustedHost() = runBlocking {
        val backend = backend(fingerprint = fingerprint)
        val events = withTimeout(20_000) {
            backend.start(
                ExecutionSessionRequest(
                    sessionId = SessionId("s1"),
                    cwd = java.io.File("."),
                    command = listOf("echo", "clankyard-ok"),
                ),
            ).toList()
        }
        val text = events.filterIsInstance<ExecutionEvent.Output>().joinToString("") { it.text }
        assertTrue(text.contains("clankyard-ok"))
        assertTrue(text.contains("Remote SSH session"))
        assertTrue(events.any { it is ExecutionEvent.Exit })
    }

    @Test
    fun unknownHostIsRejectedAndReported() = runBlocking {
        val pending = AtomicReference<String?>(null)
        val backend = backend(fingerprint = null, onUnknown = { pending.set(it) })
        val events = withTimeout(20_000) {
            backend.start(
                ExecutionSessionRequest(
                    sessionId = SessionId("s2"),
                    cwd = java.io.File("."),
                    command = listOf("echo", "nope"),
                ),
            ).toList()
        }
        val errors = events.filterIsInstance<ExecutionEvent.Error>()
        assertTrue(errors.any { it.message.contains("Untrusted host") })
        assertTrue(pending.get().orEmpty().isNotBlank())
        assertTrue(SshExecutionBackend.fingerprintsMatch(pending.get()!!, fingerprint))
    }

    @Test
    fun missingPasswordIsHonest() = runBlocking {
        val backend = SshExecutionBackend(
            load = {
                SshConnectRequest(
                    host = "127.0.0.1",
                    port = server.port,
                    username = "tester",
                    password = null,
                    fingerprint = fingerprint,
                )
            },
        )
        val events = backend.start(
            ExecutionSessionRequest(SessionId("s3"), java.io.File(".")),
        ).toList()
        assertTrue(events.filterIsInstance<ExecutionEvent.Error>().any { it.message.contains("password") })
    }

    private fun backend(
        fingerprint: String?,
        onUnknown: (String) -> Unit = {},
    ) = SshExecutionBackend(
        load = {
            SshConnectRequest(
                host = "127.0.0.1",
                port = server.port,
                username = "tester",
                password = "secret12",
                fingerprint = fingerprint,
            )
        },
        onUnknownHost = onUnknown,
    )
}

private class OneShotCommand(private val command: String) : Command {
    private var stdout: OutputStream? = null
    private var exit: ExitCallback? = null

    override fun setInputStream(input: InputStream?) = Unit
    override fun setOutputStream(out: OutputStream?) { stdout = out }
    override fun setErrorStream(err: OutputStream?) = Unit
    override fun setExitCallback(callback: ExitCallback?) { exit = callback }

    override fun start(channel: ChannelSession?, env: Environment?) {
        thread(name = "ssh-oneshot") {
            val body = if ("clankyard-ok" in command) "clankyard-ok\n" else "$command\n"
            runCatching { stdout?.write(body.toByteArray()) }
            runCatching { stdout?.flush() }
            exit?.onExit(0)
        }
    }

    override fun destroy(channel: ChannelSession?) = Unit
}
