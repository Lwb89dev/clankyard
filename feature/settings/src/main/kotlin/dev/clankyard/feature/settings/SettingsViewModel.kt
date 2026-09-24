package dev.clankyard.feature.settings

import dev.clankyard.core.model.Credential
import dev.clankyard.core.ui.theme.WorkshopTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface LlmProbe {
    suspend fun modelIds(settings: WorkshopSettings, credential: Credential): List<String>
    suspend fun ping(settings: WorkshopSettings, credential: Credential, model: String) {}
}

enum class AmberPending { None, EncryptKey, DecryptForTest }

data class SettingsUiState(
    val settings: WorkshopSettings = WorkshopSettings(),
    val keyDraft: String = "",
    val keyRevealed: Boolean = false,
    val sshPasswordDraft: String = "",
    val bunkerDraft: String = "",
    val pendingAck: Boolean = false,
    val pendingSshAck: Boolean = false,
    val amberPending: AmberPending = AmberPending.None,
    val pendingCiphertext: String = "",
    val amberInstalled: Boolean = false,
    val testing: Boolean = false,
    val availableModels: List<String> = emptyList(),
    val status: String? = null,
)

sealed interface SettingsEvent {
    data class Provider(val value: SettingsProvider) : SettingsEvent
    data class Theme(val value: WorkshopTheme) : SettingsEvent
    data class Model(val value: String) : SettingsEvent
    data class BaseUrl(val value: String) : SettingsEvent
    data class KeyDraft(val value: String) : SettingsEvent
    data class Reveal(val value: Boolean) : SettingsEvent
    data object RequestSaveKey : SettingsEvent
    data object ConfirmAck : SettingsEvent
    data object CancelAck : SettingsEvent
    data object DeleteKey : SettingsEvent
    data object TestConnection : SettingsEvent
    data class Execution(val value: ExecutionKind) : SettingsEvent
    data class SshHost(val value: String) : SettingsEvent
    data class SshPort(val value: String) : SettingsEvent
    data class SshUser(val value: String) : SettingsEvent
    data class SshRemoteCwd(val value: String) : SettingsEvent
    data class SshPasswordDraft(val value: String) : SettingsEvent
    data object RequestSaveSshPassword : SettingsEvent
    data object ConfirmSshAck : SettingsEvent
    data object DeleteSshPassword : SettingsEvent
    data object TrustHostKey : SettingsEvent
    data object ClearHostKey : SettingsEvent
    data class BunkerDraft(val value: String) : SettingsEvent
    data object SaveBunker : SettingsEvent
    data class WrapSecrets(val value: Boolean) : SettingsEvent
    data object LogoutNostr : SettingsEvent
    data class AmberLogin(val pubkeyHex: String, val signerPackage: String) : SettingsEvent
    data class AmberCiphertext(val value: String) : SettingsEvent
    data class AmberPlaintext(val value: String) : SettingsEvent
    data object AmberCancelled : SettingsEvent
}

class SettingsViewModel(
    private val store: WorkshopSettingsStore,
    private val scope: CoroutineScope,
    private val probe: LlmProbe? = null,
    private val amber: AmberBridge? = null,
) {
    private val _state = MutableStateFlow(SettingsUiState(settings = store.read()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                amberInstalled = amber?.isInstalled() == true,
                bunkerDraft = it.settings.bunkerUri,
                availableModels = cachedModels(it.settings.provider),
            )
        }
        scope.launch { refreshHasKey() }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.Provider -> {
                val defaults = SettingsProvider.entries.map { it.defaultModel }.filter { it.isNotEmpty() }.toSet()
                update { snap ->
                    val model = if (
                        snap.model.isBlank() ||
                        snap.model in defaults ||
                        !ModelCatalog.isChatModel(event.value, snap.model)
                    ) {
                        event.value.defaultModel
                    } else {
                        snap.model
                    }
                    val url = when (event.value) {
                        SettingsProvider.Ollama ->
                            snap.compatibleBaseUrl.ifBlank { "http://127.0.0.1:11434" }
                        else -> snap.compatibleBaseUrl
                    }
                    snap.copy(provider = event.value, model = model, compatibleBaseUrl = url)
                }
                _state.update { it.copy(availableModels = cachedModels(event.value)) }
            }
            is SettingsEvent.Theme -> update { it.copy(theme = event.value) }
            is SettingsEvent.Model -> update { it.copy(model = event.value) }
            is SettingsEvent.BaseUrl -> onBaseUrl(event.value)
            is SettingsEvent.KeyDraft -> _state.update { it.copy(keyDraft = event.value) }
            is SettingsEvent.Reveal -> _state.update { it.copy(keyRevealed = event.value) }
            SettingsEvent.RequestSaveKey -> requestSave()
            SettingsEvent.ConfirmAck -> confirmSave()
            SettingsEvent.CancelAck -> _state.update { it.copy(pendingAck = false, pendingSshAck = false) }
            SettingsEvent.DeleteKey -> scope.launch { deleteKey() }
            is SettingsEvent.Execution -> update { it.copy(execution = event.value) }
            is SettingsEvent.SshHost -> update { it.copy(sshHost = event.value.trim()) }
            is SettingsEvent.SshPort -> {
                val port = event.value.toIntOrNull() ?: return
                update { it.copy(sshPort = port.coerceIn(1, 65535)) }
            }
            is SettingsEvent.SshUser -> update { it.copy(sshUser = event.value) }
            is SettingsEvent.SshRemoteCwd -> update { it.copy(sshRemoteCwd = event.value) }
            is SettingsEvent.SshPasswordDraft -> _state.update { it.copy(sshPasswordDraft = event.value) }
            SettingsEvent.RequestSaveSshPassword -> requestSaveSsh()
            SettingsEvent.ConfirmSshAck -> confirmSshSave()
            SettingsEvent.DeleteSshPassword -> scope.launch { deleteSshPassword() }
            SettingsEvent.TrustHostKey -> trustHost()
            SettingsEvent.ClearHostKey -> update {
                it.copy(sshHostFingerprint = "", sshPendingFingerprint = "")
            }
            SettingsEvent.TestConnection -> scope.launch { testConnection() }
            is SettingsEvent.BunkerDraft -> _state.update { it.copy(bunkerDraft = event.value) }
            SettingsEvent.SaveBunker -> saveBunker()
            is SettingsEvent.WrapSecrets -> update { it.copy(wrapSecretsWithNostr = event.value) }
            SettingsEvent.LogoutNostr -> update {
                it.copy(
                    nostrPubkeyHex = "",
                    nostrSignerPackage = "",
                    bunkerUri = "",
                    wrapSecretsWithNostr = false,
                )
            }
            is SettingsEvent.AmberLogin -> update {
                it.copy(nostrPubkeyHex = event.pubkeyHex, nostrSignerPackage = event.signerPackage)
            }.also {
                _state.update { it.copy(status = "Amber linked. nsec stays in Amber.") }
            }
            is SettingsEvent.AmberCiphertext -> scope.launch { saveWrapped(event.value) }
            is SettingsEvent.AmberPlaintext -> scope.launch {
                probeWith(Credential.ApiKey(event.value))
            }
            SettingsEvent.AmberCancelled -> _state.update {
                it.copy(amberPending = AmberPending.None, testing = false, status = "Amber cancelled.")
            }
        }
    }

    private fun onBaseUrl(value: String) {
        val provider = _state.value.settings.provider
        val trimmed = value.trim()
        if (provider == SettingsProvider.Ollama) {
            val host = trimmed.substringAfter("://").substringBefore("/").substringBefore(":")
            val loopback = host == "127.0.0.1" || host.equals("localhost", true) || host == "10.0.2.2"
            if (trimmed.startsWith("http://", ignoreCase = true) && !loopback) {
                _state.update {
                    it.copy(
                        settings = it.settings.copy(compatibleBaseUrl = value),
                        status = "http:// only on localhost. SSH-tunnel a private Ollama: ssh -L 11434:127.0.0.1:11434 user@host",
                    )
                }
                return
            }
            _state.update { it.copy(status = null) }
            update { it.copy(compatibleBaseUrl = value) }
            return
        }
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            _state.update {
                it.copy(
                    settings = it.settings.copy(compatibleBaseUrl = value),
                    status = "http:// is rejected here. Use the Ollama tab for localhost.",
                )
            }
        } else {
            _state.update { it.copy(status = null) }
            update { it.copy(compatibleBaseUrl = value) }
        }
    }

    private fun update(transform: (WorkshopSettings) -> WorkshopSettings) {
        val next = transform(_state.value.settings)
        store.write(next)
        _state.update { it.copy(settings = next) }
        scope.launch { refreshHasKey() }
    }

    private fun requestSave() {
        val secret = _state.value.keyDraft.trim()
        if (looksLikeNsec(secret)) {
            _state.update { it.copy(status = "Never paste nsec into Clankyard. Use Amber.") }
            return
        }
        if (looksLikeAccountPassword(secret)) {
            _state.update {
                it.copy(status = "Providers use API keys, not ChatGPT/Claude email+password. Paste a key from the dashboard.")
            }
            return
        }
        val settings = _state.value.settings
        if (settings.provider.keyRequired && secret.length < 8) {
            _state.update { it.copy(status = "API key missing or too short") }
            return
        }
        if (settings.provider == SettingsProvider.Compatible) {
            val url = settings.compatibleBaseUrl.trim()
            if (url.isEmpty() || url.startsWith("http://", ignoreCase = true)) {
                _state.update { it.copy(status = "http:// is rejected. Use Ollama for localhost.") }
                return
            }
        }
        if (settings.provider == SettingsProvider.Ollama && secret.isEmpty()) {
            _state.update { it.copy(status = "Ollama does not need a key. Test connection when the daemon is up.") }
            return
        }
        if (!_state.value.settings.byokAcknowledged) {
            _state.update { it.copy(pendingAck = true) }
            return
        }
        beginSave(secret)
    }

    private fun confirmSave() {
        val secret = _state.value.keyDraft.trim()
        update { it.copy(byokAcknowledged = true) }
        _state.update { it.copy(pendingAck = false) }
        beginSave(secret)
    }

    private fun beginSave(secret: String) {
        val snap = _state.value.settings
        val session = WorkshopSettingsStore.nostrSession(snap)
        if (snap.wrapSecretsWithNostr && session != null) {
            _state.update { it.copy(amberPending = AmberPending.EncryptKey) }
            return
        }
        scope.launch { saveKey(secret) }
    }

    private suspend fun saveKey(secret: String) {
        val snap = _state.value.settings
        if (snap.model.isBlank() && snap.provider.defaultModel.isNotEmpty()) {
            update { it.copy(model = it.provider.defaultModel) }
        }
        store.saveKey(_state.value.settings, secret)
        _state.update {
            it.copy(keyDraft = "", keyRevealed = false, status = "Key saved (masked). Tap Test connection.")
        }
        refreshHasKey()
    }

    private suspend fun deleteKey() {
        store.deleteKey(_state.value.settings)
        _state.update { it.copy(status = "Key deleted", keyDraft = "") }
        refreshHasKey()
    }

    private fun requestSaveSsh() {
        val secret = _state.value.sshPasswordDraft.trim()
        if (secret.isEmpty()) {
            _state.update { it.copy(status = "SSH password missing") }
            return
        }
        if (_state.value.settings.sshHost.isBlank() || _state.value.settings.sshUser.isBlank()) {
            _state.update { it.copy(status = "Set SSH host and user first") }
            return
        }
        if (!_state.value.settings.byokAcknowledged) {
            _state.update { it.copy(pendingSshAck = true) }
            return
        }
        scope.launch { saveSshPassword(secret) }
    }

    private fun confirmSshSave() {
        val secret = _state.value.sshPasswordDraft.trim()
        update { it.copy(byokAcknowledged = true) }
        _state.update { it.copy(pendingSshAck = false) }
        scope.launch { saveSshPassword(secret) }
    }

    private suspend fun saveSshPassword(secret: String) {
        store.saveSshPassword(_state.value.settings, secret)
        _state.update {
            it.copy(sshPasswordDraft = "", status = "SSH password saved (masked).")
        }
        refreshHasKey()
    }

    private suspend fun deleteSshPassword() {
        store.deleteSshPassword(_state.value.settings)
        _state.update { it.copy(status = "SSH password deleted", sshPasswordDraft = "") }
        refreshHasKey()
    }

    private fun trustHost() {
        val pending = _state.value.settings.sshPendingFingerprint
        if (pending.isBlank()) {
            _state.update { it.copy(status = "No pending host key. Connect once from the terminal.") }
            return
        }
        update { it.copy(sshHostFingerprint = pending, sshPendingFingerprint = "") }
        _state.update { it.copy(status = "Host key trusted.") }
    }

    private suspend fun refreshHasKey() {
        val snap = _state.value.settings
        val has = store.loadKey(snap)
        val ssh = store.loadSshPassword(snap)
        _state.update {
            it.copy(
                settings = it.settings.copy(hasKey = has, hasSshPassword = ssh),
                availableModels = cachedModels(snap.provider),
            )
        }
    }

    private fun cachedModels(provider: SettingsProvider): List<String> {
        val stored = store.readModels(provider)
        return stored.ifEmpty { ModelCatalog.seeds(provider) }
    }

    private fun saveBunker() {
        val raw = _state.value.bunkerDraft.trim()
        if (looksLikeNsec(raw)) {
            _state.update { it.copy(status = "Never paste nsec into Clankyard. Use Amber or a bunker:// URI.") }
            return
        }
        val parsed = BunkerUri.parse(raw)
        if (parsed == null) {
            _state.update { it.copy(status = "Need a bunker:// URI (NIP-46), not an nsec.") }
            return
        }
        update {
            it.copy(
                bunkerUri = raw,
                nostrPubkeyHex = it.nostrPubkeyHex.ifBlank { parsed.pubkeyHex },
            )
        }
        _state.update { it.copy(status = "Bunker URI stored locally. nsec stays in the bunker/Amber.") }
    }

    private suspend fun saveWrapped(ciphertext: String) {
        store.saveWrappedKey(_state.value.settings, ciphertext)
        _state.update {
            it.copy(
                keyDraft = "",
                keyRevealed = false,
                amberPending = AmberPending.None,
                status = "Key wrapped with Amber NIP-44 and stored on this device.",
            )
        }
        refreshHasKey()
    }

    private suspend fun testConnection() {
        val probe = this.probe
        if (probe == null) {
            _state.update { it.copy(status = "Connection test unavailable.") }
            return
        }
        val snap = _state.value.settings
        val cred = store.credential(snap)
            ?: if (snap.provider == SettingsProvider.Ollama) Credential.ApiKey("ollama") else null
        if (cred == null) {
            _state.update { it.copy(status = "Save an API key first.") }
            return
        }
        _state.update { it.copy(testing = true, status = "Testing…") }
        when (cred) {
            is Credential.ApiKey -> probeWith(cred)
            is Credential.Nip44Wrap -> {
                val session = WorkshopSettingsStore.nostrSession(snap)
                val plain = session?.let { amber?.decryptViaResolver(cred.ciphertext, it) }
                if (plain != null) {
                    probeWith(Credential.ApiKey(plain))
                } else {
                    _state.update {
                        it.copy(
                            amberPending = AmberPending.DecryptForTest,
                            pendingCiphertext = cred.ciphertext,
                            status = "Approve decrypt in Amber, then the test continues.",
                        )
                    }
                }
            }
            is Credential.OAuthToken -> {
                _state.update { it.copy(testing = false, status = "OAuth is not used. Paste an API key.") }
            }
        }
    }

    private suspend fun probeWith(credential: Credential) {
        val probe = this.probe ?: return
        val snap = _state.value.settings
        val result = runCatching { probe.modelIds(snap, credential) }
        result.fold(
            onSuccess = { ids ->
                val merged = ModelCatalog.merge(snap.provider, ids)
                store.writeModels(snap.provider, merged)
                if (snap.model.isBlank() && merged.isNotEmpty()) {
                    update { it.copy(model = merged.first()) }
                }
                val pingModel = ModelCatalog.pingModel(snap.provider, snap.model, merged)
                if (pingModel.isNotEmpty()) {
                    val ping = runCatching { probe.ping(_state.value.settings, credential, pingModel) }
                    ping.exceptionOrNull()?.let { err ->
                        _state.update {
                            it.copy(
                                testing = false,
                                amberPending = AmberPending.None,
                                availableModels = merged,
                                status = err.message ?: "Chat ping failed.",
                            )
                        }
                        return
                    }
                }
                _state.update {
                    it.copy(
                        testing = false,
                        amberPending = AmberPending.None,
                        availableModels = merged,
                        status = "Connected. ${merged.size} chat models. Chat ping ok on $pingModel.",
                    )
                }
            },
            onFailure = { err ->
                _state.update {
                    it.copy(
                        testing = false,
                        amberPending = AmberPending.None,
                        status = err.message ?: "Connection failed.",
                    )
                }
            },
        )
    }
}
