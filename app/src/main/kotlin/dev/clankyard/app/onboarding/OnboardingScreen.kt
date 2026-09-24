package dev.clankyard.app.onboarding

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clankyard.core.ui.R
import dev.clankyard.feature.settings.AmberBridge
import dev.clankyard.feature.settings.SettingsProvider
import dev.clankyard.core.ui.theme.WorkshopBackdrop
import dev.clankyard.core.ui.theme.WorkshopHazardStrip
import dev.clankyard.core.ui.theme.WorkshopPanel
import dev.clankyard.core.ui.theme.WorkshopSectionHeader
import dev.clankyard.core.ui.theme.WorkshopStatusPill

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val amber = remember(context) { AmberBridge(context.applicationContext) }
    val directoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::importDirectory) }
    val nostrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.amberCancelled()
            return@rememberLauncherForActivityResult
        }
        val parsed = amber.parsePubkeyResult(result.data)
        if (parsed == null) viewModel.amberCancelled()
        else viewModel.linkNostr(parsed.first, parsed.second)
    }

    WorkshopBackdrop(modifier = modifier.fillMaxSize().imePadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnboardingHero(
                        compact = false,
                        modifier = Modifier.weight(0.42f),
                    )
                    WorkshopPanel(
                        modifier = Modifier.weight(0.58f).widthIn(max = 720.dp),
                        accent = true,
                    ) {
                        OnboardingPanel(
                            state = state,
                            viewModel = viewModel,
                            onChooseDirectory = { directoryLauncher.launch(null) },
                            onLoginNostr = { nostrLauncher.launch(amber.getPublicKeyIntent()) },
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OnboardingHero(compact = true, modifier = Modifier.fillMaxWidth())
                    WorkshopPanel(modifier = Modifier.fillMaxWidth(), accent = true) {
                        OnboardingPanel(
                            state = state,
                            viewModel = viewModel,
                            onChooseDirectory = { directoryLauncher.launch(null) },
                            onLoginNostr = { nostrLauncher.launch(amber.getPublicKeyIntent()) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHero(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        WorkshopPanel(modifier = modifier) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.clanker_still),
                    contentDescription = "Clanker unit",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(86.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WorkshopStatusPill("CLNK-01 online")
                    Text("CLANKYARD", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "A local-first coding rig built from useful scrap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WorkshopHazardStrip(Modifier.fillMaxWidth().height(5.dp))
        }
        return
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WorkshopStatusPill("unit CLNK-01 // online")
        Image(
            painter = painterResource(R.drawable.clanker_still),
            contentDescription = "Clanker unit",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(230.dp).padding(vertical = 10.dp),
        )
        Text(
            "CLANKYARD",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "CODE. BUILD. SHIP.\nFROM THE SCRAP HEAP.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Your private Android workshop for files, terminal, Git and an AI mechanic.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 440.dp),
        )
        WorkshopHazardStrip(Modifier.padding(top = 20.dp).fillMaxWidth(0.72f).height(6.dp))
    }
}

@Composable
private fun OnboardingPanel(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onChooseDirectory: () -> Unit,
    onLoginNostr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorkshopSectionHeader(
            kicker = "boot sequence // 0${state.page.step}",
            title = state.page.title,
            trailing = { WorkshopStatusPill("${state.page.step}/4") },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(
                            if (index < state.page.step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            MaterialTheme.shapes.extraSmall,
                        ),
                )
            }
        }
        when (state.page) {
            OnboardingPage.Intro -> IntroPage(onContinue = viewModel::continueFromIntro)
            OnboardingPage.Directory -> DirectoryPage(
                state = state,
                onChoose = onChooseDirectory,
                onEmpty = viewModel::useEmptyWorkspace,
                onContinue = viewModel::continueFromDirectory,
            )
            OnboardingPage.Nostr -> NostrPage(
                state = state,
                onLogin = onLoginNostr,
                onSkip = viewModel::continueFromNostr,
                onBack = viewModel::back,
            )
            OnboardingPage.Ai -> AiPage(
                state = state,
                onProvider = viewModel::selectProvider,
                onModel = viewModel::setModel,
                onBaseUrl = viewModel::setBaseUrl,
                onKey = viewModel::setKey,
                onAcknowledged = viewModel::setAiAcknowledged,
                onSave = viewModel::saveAiConfiguration,
                onSkip = viewModel::skipAi,
                onBack = viewModel::back,
            )
        }
        state.status?.let {
            WorkshopPanel(modifier = Modifier.fillMaxWidth(), accent = true) {
                Text(
                    text = "> $it",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun IntroPage(onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Clankyard is a local coding workshop for editing, searching, running and reviewing projects on your Android device.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Your project is kept in an app-private workspace. AI is optional, and API keys are stored through Android Keystore. Nostr login uses Amber without importing your nsec.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WorkshopPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("[01] LOCAL-FIRST WORKSPACE", style = MaterialTheme.typography.labelMedium)
                Text("[02] TERMINAL + EDITOR + GIT", style = MaterialTheme.typography.labelMedium)
                Text("[03] OPTIONAL AI MECHANIC", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onContinue) { Text("Get started") }
    }
}

@Composable
private fun DirectoryPage(
    state: OnboardingUiState,
    onChoose: () -> Unit,
    onEmpty: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Choose the project folder Clankyard should work on. It will be copied into the app's private environment; the original folder is never modified.",
        )
        Button(onClick = onChoose, enabled = !state.busy) { Text("Choose project directory") }
        TextButton(onClick = onEmpty, enabled = !state.busy) { Text("Use an empty secure workspace") }
        state.directoryLabel.takeIf(String::isNotBlank)?.let {
            WorkshopPanel(Modifier.fillMaxWidth(), accent = true) {
                Column(Modifier.padding(12.dp)) {
                    Text("SECURE BAY MOUNTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Workspace: $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.busy) {
            val progress = if (state.totalBytes > 0) {
                state.copiedBytes.toFloat() / state.totalBytes.toFloat()
            } else {
                null
            }
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = onContinue, enabled = state.selectedWorkspaceId != null && !state.busy) {
            Text("Continue")
        }
    }
}

@Composable
private fun NostrPage(
    state: OnboardingUiState,
    onLogin: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Connect an Amber-compatible Nostr signer. Clankyard receives only your public key; your nsec remains inside the signer.",
        )
        if (state.nostrLinked) {
            WorkshopStatusPill("linked ${state.nostrPubkey.take(12)}…")
        } else if (!state.amberInstalled) {
            Text("No Nostr signer was found. Install Amber or continue without Nostr.")
        }
        Button(onClick = onLogin, enabled = state.amberInstalled) { Text("Login with Amber") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            TextButton(onClick = onSkip) { Text(if (state.nostrLinked) "Continue" else "Continue without Nostr") }
        }
    }
}

@Composable
private fun AiPage(
    state: OnboardingUiState,
    onProvider: (SettingsProvider) -> Unit,
    onModel: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onKey: (String) -> Unit,
    onAcknowledged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val needsUrl = state.provider == SettingsProvider.Compatible || state.provider == SettingsProvider.Ollama
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI is optional. Configure a provider now, or start with the local tools and add one later in Settings.")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsProvider.entries.forEach { provider ->
                FilterChip(
                    selected = state.provider == provider,
                    onClick = { onProvider(provider) },
                    label = { Text(provider.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        OutlinedTextField(
            value = state.model,
            onValueChange = onModel,
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (needsUrl) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrl,
                label = { Text(if (state.provider == SettingsProvider.Ollama) "Ollama URL" else "HTTPS base URL") },
                supportingText = {
                    Text(if (state.provider == SettingsProvider.Ollama) "Default: http://127.0.0.1:11434" else "Use HTTPS for remote compatible providers.")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        if (state.provider.keyRequired) {
            OutlinedTextField(
                value = state.keyDraft,
                onValueChange = onKey,
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.aiAcknowledged, onCheckedChange = onAcknowledged)
                Text("I understand the API key stays on this device and can incur provider charges.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(onClick = onSave) { Text("Save and finish") }
        }
        TextButton(onClick = onSkip) { Text("Continue without AI") }
    }
}
