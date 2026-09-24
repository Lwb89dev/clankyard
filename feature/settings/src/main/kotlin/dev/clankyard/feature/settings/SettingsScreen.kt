package dev.clankyard.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.clankyard.core.ui.theme.WorkshopTheme
import dev.clankyard.core.ui.theme.WorkshopWindowSurface

private const val BYOK_COPY =
    "API keys live on this device (Android Keystore). A compromised or rooted " +
        "device can still spend them. This is advanced BYOK, not a server secret. " +
        "OpenAI, Anthropic, and xAI document API keys for programmatic use. " +
        "Do not paste keys into logs or backups."

private const val NOSTR_COPY =
    "Amber never gives Clankyard your nsec. Login uses NIP-55 (Amber) or a " +
        "bunker:// URI (NIP-46). Optional wrap uses NIP-44 inside Amber so " +
        "secrets are ciphertext on this tablet."

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    amber: AmberBridge? = null,
    onThemeChanged: (WorkshopTheme) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val bridge = amber ?: remember(context) { AmberBridge(context.applicationContext) }
    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.onEvent(SettingsEvent.AmberCancelled)
            return@rememberLauncherForActivityResult
        }
        val parsed = bridge.parsePubkeyResult(result.data)
        if (parsed == null) viewModel.onEvent(SettingsEvent.AmberCancelled)
        else viewModel.onEvent(SettingsEvent.AmberLogin(parsed.first, parsed.second))
    }
    val cryptoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.onEvent(SettingsEvent.AmberCancelled)
            return@rememberLauncherForActivityResult
        }
        val value = bridge.parseCipherResult(result.data)
        if (value == null) {
            viewModel.onEvent(SettingsEvent.AmberCancelled)
            return@rememberLauncherForActivityResult
        }
        when (state.amberPending) {
            AmberPending.EncryptKey -> viewModel.onEvent(SettingsEvent.AmberCiphertext(value))
            AmberPending.DecryptForTest -> viewModel.onEvent(SettingsEvent.AmberPlaintext(value))
            AmberPending.None -> Unit
        }
    }
    LaunchedEffect(state.amberPending) {
        val session = WorkshopSettingsStore.nostrSession(state.settings) ?: return@LaunchedEffect
        when (state.amberPending) {
            AmberPending.EncryptKey -> cryptoLauncher.launch(
                bridge.nip44EncryptIntent(state.keyDraft.trim(), session),
            )
            AmberPending.DecryptForTest -> cryptoLauncher.launch(
                bridge.nip44DecryptIntent(state.pendingCiphertext, session),
            )
            AmberPending.None -> Unit
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        WorkshopWindowSurface(
            modifier = modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .imePadding(),
            shape = MaterialTheme.shapes.large,
        ) {
            SettingsScreenContent(
                state = state,
                onEvent = viewModel::onEvent,
                onDismiss = onDismiss,
                amber = bridge,
                launchAmber = loginLauncher::launch,
                onThemeChanged = onThemeChanged,
            )
        }
    }
}

@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onDismiss: () -> Unit,
    amber: AmberBridge? = null,
    launchAmber: ((android.content.Intent) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onThemeChanged: (WorkshopTheme) -> Unit = {},
) {
    val view = LocalView.current
    val lockScreen = state.keyRevealed || state.keyDraft.isNotEmpty() || state.sshPasswordDraft.isNotEmpty()
    DisposableEffect(lockScreen) {
        val window = view.context.findActivity()?.window
        if (lockScreen) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text(
            "The workshop works with no AI key. Secrets never leave this device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SectionTitle("Theme")
        Text("Choose the accent color for the workshop.", style = MaterialTheme.typography.bodySmall)
        ChipRow {
            WorkshopTheme.entries.forEach { theme ->
                FilterChip(
                    selected = state.settings.theme == theme,
                    onClick = {
                        onEvent(SettingsEvent.Theme(theme))
                        onThemeChanged(theme)
                    },
                    label = { Text(theme.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        SectionTitle("AI provider")
        ChipRow {
            SettingsProvider.entries.forEach { provider ->
                FilterChip(
                    selected = state.settings.provider == provider,
                    onClick = { onEvent(SettingsEvent.Provider(provider)) },
                    label = { Text(provider.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        AnimatedContent(
            targetState = state.settings.provider,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                slideInHorizontally { full -> dir * full } togetherWith
                    slideOutHorizontally { full -> -dir * full }
            },
            label = "provider-pane",
        ) { provider ->
            ProviderPane(provider, state, onEvent)
        }
        Text(BYOK_COPY, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        SectionTitle("Nostr (Amber / bunker)")
        Text(NOSTR_COPY, style = MaterialTheme.typography.bodySmall)
        val npub = state.settings.nostrPubkeyHex
        Text(
            if (npub.isNotBlank()) "Linked npub: ${npub.take(16)}…" else "Not linked",
            style = MaterialTheme.typography.bodySmall,
        )
        ChipRow {
            Button(
                onClick = {
                    val intent = amber?.getPublicKeyIntent()
                    if (intent != null) launchAmber?.invoke(intent)
                    else onEvent(SettingsEvent.AmberCancelled)
                },
                enabled = state.amberInstalled,
            ) { Text("Login with Amber") }
            TextButton(onClick = { onEvent(SettingsEvent.LogoutNostr) }) { Text("Logout") }
        }
        if (!state.amberInstalled) {
            Text("Amber not installed. F-Droid: com.greenart7c3.nostrsigner", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.settings.wrapSecretsWithNostr,
                onCheckedChange = { onEvent(SettingsEvent.WrapSecrets(it)) },
                enabled = npub.length == 64,
            )
            Text("Wrap API keys with Amber NIP-44 (nsec never imported)", modifier = Modifier.padding(start = 4.dp))
        }
        OutlinedTextField(
            value = state.bunkerDraft,
            onValueChange = { onEvent(SettingsEvent.BunkerDraft(it)) },
            label = { Text("bunker:// URI (NIP-46)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { onEvent(SettingsEvent.SaveBunker) }) { Text("Save bunker URI") }
        HorizontalDivider()
        SectionTitle("Execution")
        ChipRow {
            ExecutionKind.entries.forEach { kind ->
                FilterChip(
                    selected = state.settings.execution == kind,
                    onClick = { onEvent(SettingsEvent.Execution(kind)) },
                    label = { Text(kind.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        if (state.settings.execution == ExecutionKind.Ssh) {
            SshSettings(state, onEvent)
        }
        state.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        TextButton(onClick = onDismiss) { Text("Close") }
    }
    if (state.pendingAck) {
        AckDialog(onEvent, confirm = SettingsEvent.ConfirmAck)
    }
    if (state.pendingSshAck) {
        AckDialog(onEvent, confirm = SettingsEvent.ConfirmSshAck)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelMenu(
    provider: SettingsProvider,
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    var expanded by remember(provider) { mutableStateOf(false) }
    val options = state.availableModels.ifEmpty { ModelCatalog.seeds(provider) }
    val query = state.settings.model
    val shown = options.filter { it.contains(query, ignoreCase = true) }.ifEmpty { options }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onEvent(SettingsEvent.Model(it))
                expanded = true
            },
            label = { Text("Model") },
            placeholder = {
                if (provider.defaultModel.isNotEmpty()) Text(provider.defaultModel)
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            shown.forEach { id ->
                DropdownMenuItem(
                    text = { Text(id) },
                    onClick = {
                        onEvent(SettingsEvent.Model(id))
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderPane(
    provider: SettingsProvider,
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(provider.label, style = MaterialTheme.typography.titleSmall)
        Text(provider.blurb, style = MaterialTheme.typography.bodySmall)
        if (provider.docsUrl.isNotEmpty()) {
            TextButton(onClick = { uriHandler.openUri(provider.docsUrl) }) {
                Text(provider.docsLabel)
            }
        }
        ModelMenu(provider, state, onEvent)
        state.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (provider == SettingsProvider.Compatible || provider == SettingsProvider.Ollama) {
            OutlinedTextField(
                value = state.settings.compatibleBaseUrl,
                onValueChange = { onEvent(SettingsEvent.BaseUrl(it)) },
                label = {
                    Text(if (provider == SettingsProvider.Ollama) "Ollama URL" else "Base URL (https)")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (provider.keyRequired) {
            Text(
                if (state.settings.hasKey) "Key on file: •••• (masked)" else "No key stored",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.keyDraft,
                onValueChange = { onEvent(SettingsEvent.KeyDraft(it)) },
                label = { Text("API key") },
                visualTransformation = if (state.keyRevealed) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ChipRow {
                TextButton(onClick = { onEvent(SettingsEvent.Reveal(!state.keyRevealed)) }) {
                    Text(if (state.keyRevealed) "Hide" else "Reveal")
                }
                Button(onClick = { onEvent(SettingsEvent.RequestSaveKey) }) { Text("Save key") }
                TextButton(onClick = { onEvent(SettingsEvent.DeleteKey) }) { Text("Delete key") }
            }
        } else {
            Text("No API key required. The daemon must be reachable at the URL.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { onEvent(SettingsEvent.TestConnection) },
            enabled = (state.settings.hasKey || !provider.keyRequired) && !state.testing,
        ) { Text(if (state.testing) "Testing…" else "Test connection") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SshSettings(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    Text(
        "Commands run on the remote host, not this tablet. First connect reports the host key; Trust it here.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = state.settings.sshHost,
        onValueChange = { onEvent(SettingsEvent.SshHost(it)) },
        label = { Text("SSH host") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.settings.sshPort.toString(),
        onValueChange = { onEvent(SettingsEvent.SshPort(it)) },
        label = { Text("Port") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.settings.sshUser,
        onValueChange = { onEvent(SettingsEvent.SshUser(it)) },
        label = { Text("User") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.settings.sshRemoteCwd,
        onValueChange = { onEvent(SettingsEvent.SshRemoteCwd(it)) },
        label = { Text("Remote cwd (optional)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Text(
        if (state.settings.hasSshPassword) "SSH password on file: •••• (masked)" else "No SSH password stored",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = state.sshPasswordDraft,
        onValueChange = { onEvent(SettingsEvent.SshPasswordDraft(it)) },
        label = { Text("SSH password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    ChipRow {
        Button(onClick = { onEvent(SettingsEvent.RequestSaveSshPassword) }) { Text("Save password") }
        TextButton(onClick = { onEvent(SettingsEvent.DeleteSshPassword) }) { Text("Delete password") }
    }
    val trusted = state.settings.sshHostFingerprint
    val pending = state.settings.sshPendingFingerprint
    Text(
        if (trusted.isNotBlank()) "Trusted host key: $trusted" else "No trusted host key",
        style = MaterialTheme.typography.bodySmall,
    )
    if (pending.isNotBlank() && pending != trusted) {
        Text("Pending: $pending", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { onEvent(SettingsEvent.TrustHostKey) }) { Text("Trust this host") }
    }
    TextButton(onClick = { onEvent(SettingsEvent.ClearHostKey) }) { Text("Forget host key") }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun AckDialog(onEvent: (SettingsEvent) -> Unit, confirm: SettingsEvent) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.CancelAck) },
        title = { Text("Bring your own key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(BYOK_COPY)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text("I understand keys live on this device")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(confirm) },
                enabled = checked,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.CancelAck) }) { Text("Cancel") }
        },
    )
}
