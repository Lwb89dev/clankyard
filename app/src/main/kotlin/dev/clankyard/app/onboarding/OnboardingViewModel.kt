package dev.clankyard.app.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.clankyard.feature.settings.AmberBridge
import dev.clankyard.feature.settings.ModelCatalog
import dev.clankyard.feature.settings.SettingsProvider
import dev.clankyard.feature.settings.WorkshopSettingsStore
import dev.clankyard.feature.settings.looksLikeAccountPassword
import dev.clankyard.feature.settings.looksLikeNsec
import dev.clankyard.feature.workspacepicker.AndroidWorkspaceIo
import dev.clankyard.workspace.FileWorkspaceRegistry
import dev.clankyard.core.model.WorkspaceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingPage(val step: Int, val title: String) {
    Intro(1, "Welcome to Clankyard"),
    Directory(2, "Choose a safe workspace"),
    Nostr(3, "Connect Nostr"),
    Ai(4, "Configure AI"),
}

data class OnboardingUiState(
    val visible: Boolean = true,
    val page: OnboardingPage = OnboardingPage.Intro,
    val selectedWorkspaceId: WorkspaceId? = null,
    val directoryLabel: String = "",
    val busy: Boolean = false,
    val copiedBytes: Long = 0,
    val totalBytes: Long = 0,
    val nostrLinked: Boolean = false,
    val nostrPubkey: String = "",
    val nostrPackage: String = "",
    val amberInstalled: Boolean = false,
    val provider: SettingsProvider = SettingsProvider.OpenAI,
    val model: String = SettingsProvider.OpenAI.defaultModel,
    val baseUrl: String = "",
    val keyDraft: String = "",
    val aiAcknowledged: Boolean = false,
    val status: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val workspaceIo: AndroidWorkspaceIo,
    private val registry: FileWorkspaceRegistry,
    private val settingsStore: WorkshopSettingsStore,
) : ViewModel() {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val amber = AmberBridge(context.applicationContext)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun continueFromIntro() {
        if (_state.value.page == OnboardingPage.Intro) setPage(OnboardingPage.Directory)
    }

    fun continueFromDirectory() {
        if (_state.value.selectedWorkspaceId != null) setPage(OnboardingPage.Nostr)
    }

    fun continueFromNostr() {
        if (_state.value.page == OnboardingPage.Nostr) setPage(OnboardingPage.Ai)
    }

    fun back() {
        val previous = when (_state.value.page) {
            OnboardingPage.Intro -> return
            OnboardingPage.Directory -> OnboardingPage.Intro
            OnboardingPage.Nostr -> OnboardingPage.Directory
            OnboardingPage.Ai -> OnboardingPage.Nostr
        }
        setPage(previous)
    }

    fun importDirectory(uri: Uri) {
        if (_state.value.busy) return
        _state.update {
            it.copy(
                busy = true,
                copiedBytes = 0,
                totalBytes = 0,
                status = "Copying the directory into Clankyard's private environment…",
            )
        }
        viewModelScope.launch {
            runCatching {
                workspaceIo.copyInFromTree(uri) { copied, total ->
                    _state.update { it.copy(copiedBytes = copied, totalBytes = total) }
                }
            }.onSuccess { id ->
                selectWorkspace(id, directoryLabel(uri))
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        status = "Directory import failed: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun useEmptyWorkspace() {
        if (_state.value.busy) return
        runCatching { registry.create("workshop").id }
            .onSuccess { selectWorkspace(it, "Empty secure workshop") }
            .onFailure { error ->
                _state.update { it.copy(status = "Could not create workspace: ${error.message ?: "unknown error"}") }
            }
    }

    fun linkNostr(pubkey: String, signerPackage: String) {
        val current = settingsStore.read()
        val next = current.copy(nostrPubkeyHex = pubkey, nostrSignerPackage = signerPackage)
        if (WorkshopSettingsStore.nostrSession(next) == null) {
            _state.update { it.copy(status = "Amber returned an invalid Nostr identity.") }
            return
        }
        settingsStore.write(next)
        _state.update {
            it.copy(
                nostrLinked = true,
                nostrPubkey = pubkey,
                nostrPackage = signerPackage,
                status = "Nostr linked. Your nsec stays in Amber.",
            )
        }
    }

    fun amberCancelled() {
        _state.update { it.copy(status = "Nostr login cancelled. You can continue without it.") }
    }

    fun setStatus(message: String?) {
        _state.update { it.copy(status = message) }
    }

    fun selectProvider(provider: SettingsProvider) {
        val old = _state.value
        val knownDefaults = SettingsProvider.entries.map { it.defaultModel }.filter(String::isNotBlank).toSet()
        val model = if (old.model.isBlank() || old.model in knownDefaults ||
            !ModelCatalog.isChatModel(old.provider, old.model)
        ) {
            provider.defaultModel
        } else {
            old.model
        }
        val baseUrl = if (provider == SettingsProvider.Ollama) {
            old.baseUrl.ifBlank { "http://127.0.0.1:11434" }
        } else {
            old.baseUrl
        }
        _state.update {
            it.copy(
                provider = provider,
                model = model,
                baseUrl = baseUrl,
                status = null,
            )
        }
    }

    fun setModel(value: String) = _state.update { it.copy(model = value) }

    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value) }

    fun setKey(value: String) = _state.update { it.copy(keyDraft = value) }

    fun setAiAcknowledged(value: Boolean) = _state.update { it.copy(aiAcknowledged = value) }

    fun saveAiConfiguration() {
        val snap = _state.value
        val provider = snap.provider
        val key = snap.keyDraft.trim()
        val model = snap.model.trim().ifBlank { provider.defaultModel }
        val baseUrl = snap.baseUrl.trim()
        val error = validateAi(provider, model, baseUrl, key, snap.aiAcknowledged)
        if (error != null) {
            _state.update { it.copy(status = error) }
            return
        }
        val next = settingsStore.read().copy(
            provider = provider,
            model = model,
            compatibleBaseUrl = baseUrl,
            byokAcknowledged = settingsStore.read().byokAcknowledged || snap.aiAcknowledged,
        )
        settingsStore.write(next)
        viewModelScope.launch {
            runCatching {
                if (provider.keyRequired) settingsStore.saveKey(next, key)
            }.onSuccess {
                complete()
            }.onFailure { error ->
                _state.update { it.copy(status = "Could not save AI configuration: ${error.message ?: "unknown error"}") }
            }
        }
    }

    fun skipAi() {
        complete()
    }

    private fun selectWorkspace(id: WorkspaceId, label: String) {
        prefs.edit()
            .putString(KEY_WORKSPACE, id.value)
            .putString(KEY_DIRECTORY, label)
            .apply()
        _state.update {
            it.copy(
                selectedWorkspaceId = id,
                directoryLabel = label,
                busy = false,
                page = OnboardingPage.Nostr,
                status = "Workspace ready in Clankyard's private environment.",
            )
        }
    }

    private fun setPage(page: OnboardingPage) {
        prefs.edit().putInt(KEY_PAGE, page.ordinal).apply()
        _state.update { it.copy(page = page, status = null) }
    }

    private fun complete() {
        prefs.edit().putBoolean(KEY_COMPLETE, true).apply()
        _state.update { it.copy(visible = false, busy = false, keyDraft = "", status = null) }
    }

    private fun initialState(): OnboardingUiState {
        if (prefs.getBoolean(KEY_COMPLETE, false)) {
            return configuredState(visible = false)
        }
        // Existing installations predate onboarding. Do not interrupt a user who already
        // has a workspace; new installations still start at the first screen.
        if (registry.list().isNotEmpty() && prefs.getString(KEY_WORKSPACE, null) == null) {
            prefs.edit().putBoolean(KEY_COMPLETE, true).apply()
            return configuredState(visible = false)
        }
        val page = OnboardingPage.entries.getOrElse(prefs.getInt(KEY_PAGE, 0)) { OnboardingPage.Intro }
        val id = prefs.getString(KEY_WORKSPACE, null)?.let { runCatching { WorkspaceId(it) }.getOrNull() }
        return configuredState(visible = true).copy(
            page = if (id == null && page != OnboardingPage.Intro) OnboardingPage.Directory else page,
            selectedWorkspaceId = id,
            directoryLabel = prefs.getString(KEY_DIRECTORY, "").orEmpty(),
        )
    }

    private fun configuredState(visible: Boolean): OnboardingUiState {
        val settings = settingsStore.read()
        val linked = WorkshopSettingsStore.nostrSession(settings) != null
        return OnboardingUiState(
            visible = visible,
            nostrLinked = linked,
            nostrPubkey = settings.nostrPubkeyHex,
            nostrPackage = settings.nostrSignerPackage,
            amberInstalled = amber.isInstalled(),
            provider = settings.provider,
            model = settings.model.ifBlank { settings.provider.defaultModel },
            baseUrl = settings.compatibleBaseUrl,
            aiAcknowledged = settings.byokAcknowledged,
        )
    }

    private fun directoryLabel(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Selected directory"

    companion object {
        private const val PREFS = "clankyard_onboarding"
        private const val KEY_COMPLETE = "complete"
        private const val KEY_PAGE = "page"
        private const val KEY_WORKSPACE = "workspace_id"
        private const val KEY_DIRECTORY = "directory_label"

        internal fun validateAi(
            provider: SettingsProvider,
            model: String,
            baseUrl: String,
            key: String,
            acknowledged: Boolean,
        ): String? {
            if (model.isBlank()) return "Choose a model before continuing."
            if (provider.keyRequired) {
                if (key.length < 8) return "API key missing or too short."
                if (looksLikeNsec(key)) return "Never paste an nsec into Clankyard. Use Amber."
                if (looksLikeAccountPassword(key)) {
                    return "Use an API key from the provider dashboard, not an account password."
                }
                if (!acknowledged) return "Confirm that you understand how the API key is stored."
            }
            if (provider == SettingsProvider.Compatible) {
                if (baseUrl.isBlank() || baseUrl.startsWith("http://", ignoreCase = true)) {
                    return "Compatible providers require an HTTPS URL. Use Ollama for localhost."
                }
            }
            if (provider == SettingsProvider.Ollama && baseUrl.isBlank()) {
                return "Enter the Ollama URL or keep the localhost default."
            }
            return null
        }
    }
}
