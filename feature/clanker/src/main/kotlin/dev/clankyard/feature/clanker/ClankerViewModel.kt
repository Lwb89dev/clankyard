package dev.clankyard.feature.clanker

import dev.clankyard.ai.agent.AgentEvent
import dev.clankyard.ai.agent.AgentOrchestrator
import dev.clankyard.ai.agent.AgentTurnRequest
import dev.clankyard.ai.agent.DefaultAgentOrchestrator
import dev.clankyard.ai.context.AgentMode
import dev.clankyard.ai.context.ContextRequest
import dev.clankyard.ai.context.WorkspaceContextEngine
import dev.clankyard.ai.patch.ApplyPatchUseCase
import dev.clankyard.ai.patch.ApplyResult
import dev.clankyard.ai.patch.FileDiff
import dev.clankyard.ai.patch.PatchEngineFactory
import dev.clankyard.ai.patch.PatchSet
import dev.clankyard.ai.provider.LlmProvider
import dev.clankyard.ai.secret.SecretFilter
import dev.clankyard.ai.tools.ToolRegistry
import dev.clankyard.core.model.RequestId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.security.SecureCredentialStore
import dev.clankyard.core.model.Credential
import dev.clankyard.feature.settings.AmberBridge
import dev.clankyard.feature.settings.SettingsProvider
import dev.clankyard.feature.settings.WorkshopSettingsStore
import dev.clankyard.git.GitRepository
import dev.clankyard.workspace.FileBackedWorkspace
import dev.clankyard.workspace.Workspace
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranscriptLine(val text: String, val fromUser: Boolean = false)

data class ClankerUiState(
    val mode: AgentMode = AgentMode.Ask,
    val draft: String = "",
    val lines: List<TranscriptLine> = emptyList(),
    val running: Boolean = false,
    val status: String = "Ask the Clanker.",
    val patch: PatchSet? = null,
    val accepted: Set<WorkspacePath> = emptySet(),
    val needsKey: Boolean = true,
    val pendingEnter: Boolean = false,
    val enterSend: String = WorkshopSettingsStore.ENTER_ASK,
)

sealed interface ClankerEvent {
    data class Draft(val value: String) : ClankerEvent
    data class Mode(val value: AgentMode) : ClankerEvent
    data object Send : ClankerEvent
    data object RequestSend : ClankerEvent
    data class ConfirmEnter(val remember: Boolean) : ClankerEvent
    data class CancelEnter(val remember: Boolean) : ClankerEvent
    data object Cancel : ClankerEvent
    data class ToggleFile(val path: WorkspacePath) : ClankerEvent
    data object AcceptAll : ClankerEvent
    data object RejectAll : ClankerEvent
}

class ClankerViewModel(
    private val workspace: () -> Workspace?,
    private val fileWorkspace: () -> FileBackedWorkspace?,
    private val git: GitRepository,
    private val patchFactory: PatchEngineFactory,
    private val tools: ToolRegistry,
    private val secrets: SecretFilter,
    private val providers: (SettingsProvider, String) -> LlmProvider,
    private val credentials: SecureCredentialStore,
    private val settings: WorkshopSettingsStore,
    private val amber: AmberBridge? = null,
    private val dirty: () -> Set<WorkspacePath>,
    private val currentFile: () -> WorkspacePath?,
    private val selection: () -> String?,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ClankerUiState())
    val state: StateFlow<ClankerUiState> = _state.asStateFlow()
    private var runJob: Job? = null
    private var requestId: RequestId? = null
    private var orchestrator: AgentOrchestrator? = null

    fun refreshKeyFlag() {
        scope.launch { refreshKeyFlagNow() }
    }

    fun onEvent(event: ClankerEvent) {
        when (event) {
            is ClankerEvent.Draft -> _state.update { it.copy(draft = event.value) }
            is ClankerEvent.Mode -> _state.update { it.copy(mode = event.value) }
            ClankerEvent.Send -> send()
            ClankerEvent.RequestSend -> requestSend()
            is ClankerEvent.ConfirmEnter -> {
                if (event.remember) persistEnter(WorkshopSettingsStore.ENTER_SEND)
                _state.update { it.copy(pendingEnter = false) }
                send()
            }
            is ClankerEvent.CancelEnter -> {
                if (event.remember) persistEnter(WorkshopSettingsStore.ENTER_NEWLINE)
                _state.update { it.copy(pendingEnter = false) }
            }
            ClankerEvent.Cancel -> cancel()
            is ClankerEvent.ToggleFile -> toggle(event.path)
            ClankerEvent.AcceptAll -> applyAccepted()
            ClankerEvent.RejectAll -> reject()
        }
    }

    private suspend fun unwrapCredential(raw: Credential): Credential? = when (raw) {
        is Credential.ApiKey -> raw
        is Credential.OAuthToken -> {
            _state.update { it.copy(status = "OAuth is not used. Paste an API key in Settings.") }
            null
        }
        is Credential.Nip44Wrap -> {
            val session = WorkshopSettingsStore.nostrSession(settings.read())
            val plain = session?.let { amber?.decryptViaResolver(raw.ciphertext, it) }
            if (plain == null) {
                _state.update {
                    it.copy(status = "Unlock Amber to unwrap the key (NIP-44). nsec stays in Amber.")
                }
                null
            } else {
                Credential.ApiKey(plain)
            }
        }
    }

    private fun persistEnter(value: String) {
        val snap = settings.read()
        settings.write(snap.copy(enterSend = value))
        _state.update { it.copy(enterSend = value) }
    }

    private fun requestSend() {
        when (settings.read().enterSend) {
            WorkshopSettingsStore.ENTER_SEND -> send()
            WorkshopSettingsStore.ENTER_NEWLINE -> Unit
            else -> _state.update { it.copy(pendingEnter = true) }
        }
    }

    private fun send() {
        runJob?.cancel()
        runJob = scope.launch {
            refreshKeyFlagNow()
            val prompt = _state.value.draft.trim()
            if (prompt.isEmpty() || _state.value.running) return@launch
            if (_state.value.needsKey) return@launch
            val ws = workspace() ?: return@launch
            runTurn(ws, prompt)
        }
    }

    private suspend fun refreshKeyFlagNow() {
        val snap = settings.read()
        val has = settings.loadKey(snap) || snap.provider == SettingsProvider.Ollama
        _state.update {
            it.copy(
                needsKey = !has,
                enterSend = snap.enterSend,
                status = if (has) {
                    if (it.status == "configure a key") "Ask the Clanker." else it.status
                } else {
                    "configure a key"
                },
            )
        }
    }

    private suspend fun runTurn(ws: Workspace, prompt: String) {
        val snap = settings.read()
        val slot = settings.slotFor(snap)
        val raw = credentials.get(slot)
            ?: if (snap.provider == SettingsProvider.Ollama) Credential.ApiKey("ollama") else null
        if (raw == null) {
            _state.update { it.copy(needsKey = true, status = "configure a key") }
            return
        }
        val credential = unwrapCredential(raw) ?: return
        val model = snap.model.ifBlank { snap.provider.defaultModel }
        if (model.isBlank()) {
            _state.update { it.copy(status = "Set a model in Settings.") }
            return
        }
        val id = RequestId(UUID.randomUUID().toString())
        requestId = id
        val provider = try {
            providers(snap.provider, snap.compatibleBaseUrl)
        } catch (e: Exception) {
            _state.update { it.copy(status = e.message ?: "Provider URL rejected.") }
            return
        }
        val agent = DefaultAgentOrchestrator(
            provider = provider,
            contextEngine = WorkspaceContextEngine(ws, secrets),
            tools = tools,
            secrets = secrets,
        )
        orchestrator = agent
        val files = fileWorkspace()
        val engine = files?.let { patchFactory.create(it) }
        val handle = try {
            files?.let { git.open(it) }
        } catch (_: Exception) {
            null
        }
        _state.update {
            it.copy(
                running = true,
                draft = "",
                patch = null,
                accepted = emptySet(),
                lines = it.lines + TranscriptLine(prompt, fromUser = true),
                status = "Clanker is reading…",
                needsKey = false,
            )
        }
        val request = AgentTurnRequest(
            requestId = id,
            mode = _state.value.mode,
            model = model,
            credential = credential,
            context = ContextRequest(
                mode = _state.value.mode,
                prompt = prompt,
                currentFile = currentFile(),
                selection = selection()?.ifBlank { null },
                pinned = emptyList(),
                mentions = emptyList(),
            ),
            workspace = ws,
            git = handle,
            patchEngine = engine,
        )
        try {
            agent.run(request).collect { event -> onAgent(event) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(status = e.message ?: "Clanker broke something.") }
        } finally {
            _state.update { it.copy(running = false) }
        }
    }

    private fun onAgent(event: AgentEvent) {
        when (event) {
            is AgentEvent.ContextReady -> {
                val names = event.packet.chunks.filterNot { it.omitted }.map { it.label }
                _state.update { it.copy(status = "Context: ${names.joinToString()}") }
            }
            is AgentEvent.Text -> _state.update { state ->
                val last = state.lines.lastOrNull()
                if (last != null && !last.fromUser) {
                    state.copy(lines = state.lines.dropLast(1) + TranscriptLine(last.text + event.delta))
                } else {
                    state.copy(lines = state.lines + TranscriptLine(event.delta))
                }
            }
            is AgentEvent.ToolStarted -> _state.update { it.copy(status = "Clanker is using ${event.name}…") }
            is AgentEvent.ToolFinished -> _state.update { it.copy(status = "Clanker finished ${event.name}") }
            is AgentEvent.PatchProposed -> _state.update {
                val ok = event.patch.diffs.filterNot { d -> d.conflict }.map { d -> d.path }.toSet()
                it.copy(
                    patch = event.patch,
                    accepted = ok,
                    status = "Clanker proposes ${ok.size} changes.",
                )
            }
            is AgentEvent.Usage -> _state.update {
                it.copy(status = "tokens in=${event.inputTokens ?: "?"} out=${event.outputTokens ?: "?"}")
            }
            is AgentEvent.Error -> _state.update { it.copy(status = event.message) }
            AgentEvent.Completed -> _state.update {
                if (it.patch == null) it.copy(status = "Ask the Clanker.") else it
            }
        }
    }

    private fun toggle(path: WorkspacePath) {
        _state.update { state ->
            val next = if (path in state.accepted) state.accepted - path else state.accepted + path
            state.copy(accepted = next)
        }
    }

    private fun applyAccepted() {
        val patch = _state.value.patch ?: return
        val files = fileWorkspace() ?: return
        val engine = patchFactory.create(files)
        scope.launch {
            val result = ApplyPatchUseCase(engine).invoke(patch.id, _state.value.accepted, dirty())
            val message = when (result) {
                is ApplyResult.Applied -> "Applied ${result.files.size} files. Undo is in the journal."
                is ApplyResult.Failed -> result.reason
            }
            _state.update {
                it.copy(patch = null, accepted = emptySet(), status = message)
            }
        }
    }

    private fun reject() {
        val patch = _state.value.patch ?: return
        val files = fileWorkspace() ?: return
        scope.launch {
            patchFactory.create(files).reject(patch.id)
            _state.update { it.copy(patch = null, accepted = emptySet(), status = "Rejected.") }
        }
    }

    private fun cancel() {
        val id = requestId ?: return
        runJob?.cancel()
        scope.launch { orchestrator?.cancel(id) }
        _state.update { it.copy(running = false, status = "Cancelled.") }
    }
}

fun FileDiff.summary(): String {
    val mark = if (conflict) "conflict" else "ok"
    return "${path.relative} ($mark)"
}
