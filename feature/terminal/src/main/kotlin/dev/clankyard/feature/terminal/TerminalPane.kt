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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.clankyard.core.ui.WorkshopSemantics

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
        Text(state.banner, style = MaterialTheme.typography.bodySmall)
        Text(
            text = state.output,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.input,
                onValueChange = session::onInput,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("stdin") },
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
