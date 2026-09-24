package dev.clankyard.feature.terminal

import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionBackend
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.terminal.api.ExecutionSessionRequest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalUiState(
    val output: String = "",
    val input: String = "",
    val running: Boolean = false,
    val banner: String = "",
)

class TerminalSession(
    private val backend: ExecutionBackend,
    private val cwd: () -> File?,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        TerminalUiState(banner = backend.capabilities.honestLimitationMessage),
    )
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    private var sessionId: SessionId? = null
    private var collectJob: Job? = null

    fun onInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun start() {
        if (_state.value.running) return
        val dir = cwd() ?: return
        val banner = backend.capabilities.honestLimitationMessage
        if (!backend.capabilities.interactive) {
            _state.update {
                it.copy(running = false, banner = banner, output = banner + "\ncwd: ${dir.path}\n")
            }
            return
        }
        val id = SessionId(UUID.randomUUID().toString())
        sessionId = id
        collectJob?.cancel()
        _state.update { it.copy(running = true, banner = banner, output = banner + "\n") }
        collectJob = scope.launch { collect(id, dir, command = emptyList()) }
    }

    fun submit() {
        val text = _state.value.input
        _state.update { it.copy(input = "") }
        if (text.isEmpty()) return
        collectJob?.cancel()
        collectJob = scope.launch {
            if (!backend.capabilities.interactive) {
                runOneShot(text)
                return@launch
            }
            if (!_state.value.running) start()
            val id = sessionId ?: return@launch
            append("> $text\n")
            backend.writeStdin(id, (text + "\n").toByteArray(Charsets.UTF_8))
        }
    }

    private suspend fun runOneShot(text: String) {
        val dir = cwd() ?: return
        val id = SessionId(UUID.randomUUID().toString())
        sessionId = id
        collectJob?.cancel()
        _state.update { it.copy(running = true) }
        append("> $text\n")
        val shell = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "/bin/sh"
        collect(id, dir, listOf(shell, "-c", text))
    }

    private suspend fun collect(id: SessionId, dir: File, command: List<String>) {
        backend.start(ExecutionSessionRequest(sessionId = id, cwd = dir, command = command)).collect { event ->
            when (event) {
                is ExecutionEvent.Output -> append(event.text)
                is ExecutionEvent.Error -> append(event.message + "\n")
                is ExecutionEvent.Exit -> {
                    append("\n[exit ${event.code}]\n")
                    _state.update { it.copy(running = false) }
                }
            }
        }
        _state.update { it.copy(running = false) }
    }

    fun restart() {
        scope.launch {
            sessionId?.let { backend.destroy(it) }
            _state.update { it.copy(running = false, output = "") }
            start()
        }
    }

    fun close() {
        collectJob?.cancel()
        scope.launch { sessionId?.let { backend.destroy(it) } }
    }

    private fun append(chunk: String) {
        _state.update { state ->
            val next = state.output + chunk
            val clipped = if (next.length > MAX) next.takeLast(MAX) else next
            state.copy(output = clipped)
        }
    }

    companion object {
        private const val MAX = 200_000
    }
}
