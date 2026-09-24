package dev.clankyard.feature.settings

import android.content.Context
import android.content.SharedPreferences
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId
import dev.clankyard.core.security.SecureCredentialStore
import dev.clankyard.core.ui.theme.WorkshopTheme

enum class SettingsProvider(
    val id: String,
    val label: String,
    val defaultModel: String,
    val docsUrl: String,
    val docsLabel: String,
    val blurb: String,
    val keyRequired: Boolean,
) {
    OpenAI(
        "openai",
        "OpenAI",
        "gpt-4o-mini",
        "https://platform.openai.com/api-keys",
        "Get an OpenAI API key",
        "Chat Completions. Paste a project API key from the OpenAI dashboard.",
        true,
    ),
    Anthropic(
        "anthropic",
        "Anthropic",
        "claude-3-5-sonnet-latest",
        "https://console.anthropic.com/settings/keys",
        "Get an Anthropic API key",
        "Messages API. Paste a key from the Anthropic console.",
        true,
    ),
    Xai(
        "xai",
        "xAI",
        "grok-2",
        "https://console.x.ai",
        "Get an xAI API key",
        "xAI Completions. Create a key in the xAI console.",
        true,
    ),
    Compatible(
        "openai-compatible",
        "Compatible",
        "",
        "",
        "",
        "Any OpenAI-compatible HTTPS host. http:// is rejected.",
        true,
    ),
    Ollama(
        "ollama",
        "Ollama",
        "llama3.2",
        "https://ollama.com/download",
        "Install Ollama",
        "Local or SSH-tunneled OpenAI-compatible server. Default http://127.0.0.1:11434. " +
            "For a private box: ssh -L 11434:127.0.0.1:11434 user@host then keep localhost.",
        false,
    ),
}

enum class ExecutionKind(val id: String, val label: String) {
    Local("local", "Local sandbox"),
    Ssh("ssh", "Remote SSH"),
}

data class WorkshopSettings(
    val provider: SettingsProvider = SettingsProvider.OpenAI,
    val theme: WorkshopTheme = WorkshopTheme.Rust,
    val model: String = "",
    val compatibleBaseUrl: String = "",
    val byokAcknowledged: Boolean = false,
    val hasKey: Boolean = false,
    val execution: ExecutionKind = ExecutionKind.Local,
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "",
    val sshRemoteCwd: String = "",
    val sshHostFingerprint: String = "",
    val sshPendingFingerprint: String = "",
    val hasSshPassword: Boolean = false,
    val nostrPubkeyHex: String = "",
    val nostrSignerPackage: String = "",
    val bunkerUri: String = "",
    val wrapSecretsWithNostr: Boolean = false,
    val enterSend: String = "ask",
    /** Explicit opt-in acknowledgement for optional executable build runtimes. */
    val buildRuntimeAck: Boolean = false,
    /** Workspaces whose build scripts the user has explicitly trusted. */
    val buildTrustedIds: Set<String> = emptySet(),
    val gradleXmxMb: Int = 512,
)

class WorkshopSettingsStore(
    private val prefs: SharedPreferences,
    private val credentials: SecureCredentialStore,
) {
    constructor(context: Context, credentials: SecureCredentialStore) : this(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        credentials,
    )

    fun read(): WorkshopSettings {
        val id = prefs.getString(KEY_PROVIDER, SettingsProvider.OpenAI.id)
        val provider = SettingsProvider.entries.firstOrNull { it.id == id } ?: SettingsProvider.OpenAI
        val executionId = prefs.getString(KEY_EXECUTION, ExecutionKind.Local.id)
        val execution = ExecutionKind.entries.firstOrNull { it.id == executionId } ?: ExecutionKind.Local
        return WorkshopSettings(
            provider = provider,
            theme = WorkshopTheme.fromId(prefs.getString(KEY_THEME, WorkshopTheme.Rust.id)),
            model = prefs.getString(KEY_MODEL, "").orEmpty(),
            compatibleBaseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            byokAcknowledged = prefs.getBoolean(KEY_ACK, false),
            execution = execution,
            sshHost = prefs.getString(KEY_SSH_HOST, "").orEmpty(),
            sshPort = prefs.getInt(KEY_SSH_PORT, 22).coerceIn(1, 65535),
            sshUser = prefs.getString(KEY_SSH_USER, "").orEmpty(),
            sshRemoteCwd = prefs.getString(KEY_SSH_CWD, "").orEmpty(),
            sshHostFingerprint = prefs.getString(KEY_SSH_FP, "").orEmpty(),
            sshPendingFingerprint = prefs.getString(KEY_SSH_PENDING_FP, "").orEmpty(),
            nostrPubkeyHex = prefs.getString(KEY_NOSTR_PUB, "").orEmpty(),
            nostrSignerPackage = prefs.getString(KEY_NOSTR_PKG, "").orEmpty(),
            bunkerUri = prefs.getString(KEY_BUNKER, "").orEmpty(),
            wrapSecretsWithNostr = prefs.getBoolean(KEY_NOSTR_WRAP, false),
            enterSend = prefs.getString(KEY_ENTER_SEND, ENTER_ASK) ?: ENTER_ASK,
            buildRuntimeAck = prefs.getBoolean(KEY_BUILD_RUNTIME_ACK, false),
            buildTrustedIds = prefs.getStringSet(KEY_BUILD_TRUSTED_IDS, emptySet()).orEmpty().toSet(),
            gradleXmxMb = prefs.getInt(KEY_GRADLE_XMX_MB, DEFAULT_GRADLE_XMX_MB)
                .coerceIn(MIN_GRADLE_XMX_MB, MAX_GRADLE_XMX_MB),
        )
    }

    fun write(settings: WorkshopSettings) {
        prefs.edit()
            .putString(KEY_PROVIDER, settings.provider.id)
            .putString(KEY_THEME, settings.theme.id)
            .putString(KEY_MODEL, settings.model)
            .putString(KEY_BASE_URL, settings.compatibleBaseUrl)
            .putBoolean(KEY_ACK, settings.byokAcknowledged)
            .putString(KEY_EXECUTION, settings.execution.id)
            .putString(KEY_SSH_HOST, settings.sshHost)
            .putInt(KEY_SSH_PORT, settings.sshPort.coerceIn(1, 65535))
            .putString(KEY_SSH_USER, settings.sshUser)
            .putString(KEY_SSH_CWD, settings.sshRemoteCwd)
            .putString(KEY_SSH_FP, settings.sshHostFingerprint)
            .putString(KEY_SSH_PENDING_FP, settings.sshPendingFingerprint)
            .putString(KEY_NOSTR_PUB, settings.nostrPubkeyHex)
            .putString(KEY_NOSTR_PKG, settings.nostrSignerPackage)
            .putString(KEY_BUNKER, settings.bunkerUri)
            .putBoolean(KEY_NOSTR_WRAP, settings.wrapSecretsWithNostr)
            .putString(KEY_ENTER_SEND, settings.enterSend)
            .putBoolean(KEY_BUILD_RUNTIME_ACK, settings.buildRuntimeAck)
            .putStringSet(KEY_BUILD_TRUSTED_IDS, settings.buildTrustedIds.toSet())
            .putInt(
                KEY_GRADLE_XMX_MB,
                settings.gradleXmxMb.coerceIn(MIN_GRADLE_XMX_MB, MAX_GRADLE_XMX_MB),
            )
            .apply()
    }

    fun readModels(provider: SettingsProvider): List<String> {
        val raw = prefs.getString(modelsKey(provider), "") ?: return emptyList()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun writeModels(provider: SettingsProvider, ids: List<String>) {
        prefs.edit().putString(modelsKey(provider), ids.joinToString("\n")).apply()
    }

    suspend fun slotFor(settings: WorkshopSettings): CredentialSlotId = slotId(settings)

    suspend fun loadKey(settings: WorkshopSettings): Boolean {
        return credentials.get(slotFor(settings)) != null
    }

    suspend fun credential(settings: WorkshopSettings): Credential? =
        credentials.get(slotFor(settings))

    suspend fun saveKey(settings: WorkshopSettings, secret: String) {
        credentials.put(slotFor(settings), Credential.ApiKey(secret))
    }

    suspend fun saveWrappedKey(settings: WorkshopSettings, ciphertext: String) {
        credentials.put(slotFor(settings), Credential.Nip44Wrap(ciphertext))
    }

    suspend fun deleteKey(settings: WorkshopSettings) {
        credentials.delete(slotFor(settings))
    }

    suspend fun loadSshPassword(settings: WorkshopSettings): Boolean {
        if (settings.sshHost.isBlank()) return false
        return credentials.get(sshSlot(settings.sshHost)) != null
    }

    suspend fun saveSshPassword(settings: WorkshopSettings, secret: String) {
        credentials.put(sshSlot(settings.sshHost), Credential.ApiKey(secret))
    }

    suspend fun deleteSshPassword(settings: WorkshopSettings) {
        if (settings.sshHost.isBlank()) return
        credentials.delete(sshSlot(settings.sshHost))
    }

    companion object {
        const val PREFS = "workshop_prefs"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_THEME = "theme"
        private const val KEY_MODEL = "model"
        private const val KEY_BASE_URL = "compatible_base_url"
        private const val KEY_ACK = "byok_ack"
        private const val KEY_EXECUTION = "execution"
        private const val KEY_SSH_HOST = "ssh_host"
        private const val KEY_SSH_PORT = "ssh_port"
        private const val KEY_SSH_USER = "ssh_user"
        private const val KEY_SSH_CWD = "ssh_cwd"
        private const val KEY_SSH_FP = "ssh_host_fingerprint"
        private const val KEY_SSH_PENDING_FP = "ssh_pending_fingerprint"
        private const val KEY_NOSTR_PUB = "nostr_pubkey_hex"
        private const val KEY_NOSTR_PKG = "nostr_signer_package"
        private const val KEY_BUNKER = "nostr_bunker_uri"
        private const val KEY_NOSTR_WRAP = "nostr_wrap_secrets"
        private const val KEY_ENTER_SEND = "enter_send"
        private const val KEY_BUILD_RUNTIME_ACK = "build_runtime_ack"
        private const val KEY_BUILD_TRUSTED_IDS = "build_trusted_ids"
        private const val KEY_GRADLE_XMX_MB = "gradle_xmx_mb"
        private const val MIN_GRADLE_XMX_MB = 128
        private const val MAX_GRADLE_XMX_MB = 2048
        private const val DEFAULT_GRADLE_XMX_MB = 512
        const val ENTER_ASK = "ask"
        const val ENTER_SEND = "send"
        const val ENTER_NEWLINE = "newline"

        fun modelsKey(provider: SettingsProvider): String = "models_${provider.id}"

        fun nostrSession(settings: WorkshopSettings): NostrSession? {
            if (settings.nostrPubkeyHex.length != 64) return null
            return NostrSession(
                pubkeyHex = settings.nostrPubkeyHex,
                signerPackage = settings.nostrSignerPackage,
                bunkerUri = settings.bunkerUri,
                wrapSecrets = settings.wrapSecretsWithNostr,
            )
        }

        fun slotId(settings: WorkshopSettings): CredentialSlotId {
            if (settings.provider != SettingsProvider.Compatible) {
                return CredentialSlotId("llm.${settings.provider.id}.default")
            }
            return CredentialSlotId("llm.openai-compatible.${hostToken(settings.compatibleBaseUrl)}")
        }

        fun sshSlot(host: String): CredentialSlotId =
            CredentialSlotId("ssh.${hostToken(host)}")

        fun hostToken(baseUrl: String): String {
            val host = baseUrl.substringAfter("://").substringBefore("/").substringBefore(":")
            val cleaned = host.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '-' }
            return cleaned.ifEmpty { "custom" }
        }
    }
}
