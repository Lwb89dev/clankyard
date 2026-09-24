package dev.clankyard.feature.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.clankyard.core.ui.WorkshopSemantics
import dev.clankyard.core.ui.theme.WorkshopPanel
import dev.clankyard.core.ui.theme.WorkshopSectionHeader
import dev.clankyard.core.ui.theme.WorkshopStatusPill

@Composable
fun TerminalPane(session: TerminalSession?, modifier: Modifier = Modifier) {
    if (session == null) {
        Text("Open a workshop to use the sandbox shell.", modifier = modifier.padding(16.dp))
        return
    }
    val state by session.state.collectAsState()
    LaunchedEffect(session) { if (!state.running) session.start() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(8.dp)
            .semantics { contentDescription = WorkshopSemantics.NAV_TERMINAL },
    ) {
        WorkshopSectionHeader(
            kicker = "shell bay // local sandbox",
            title = "Terminal",
            trailing = {
                WorkshopStatusPill(
                    if (state.running) "process live" else "standby",
                    active = state.running,
                )
            },
        )
        Text(
            "// ${state.banner}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        )
        WorkshopPanel(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            accent = true,
        ) {
            Text(
                text = state.output,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.input,
                onValueChange = session::onInput,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("\$ stdin") },
            )
            Button(onClick = session::submit, modifier = Modifier.padding(start = 8.dp)) {
                Text("Send")
            }
            Button(onClick = session::restart, modifier = Modifier.padding(start = 8.dp)) {
                Text("Restart")
            }
        }
    }
}
