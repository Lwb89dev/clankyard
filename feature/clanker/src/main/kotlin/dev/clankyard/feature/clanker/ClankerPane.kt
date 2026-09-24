package dev.clankyard.feature.clanker

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.clankyard.ai.context.AgentMode
import dev.clankyard.core.ui.R
import dev.clankyard.core.ui.WorkshopSemantics

@Composable
fun ClankerPane(viewModel: ClankerViewModel?, modifier: Modifier = Modifier) {
    if (viewModel == null) {
        Text("Open a workshop.", modifier = modifier.padding(16.dp))
        return
    }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.refreshKeyFlag() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .semantics { contentDescription = WorkshopSemantics.CLANKER_PANE },
    ) {
        Text("The Clanker", style = MaterialTheme.typography.titleMedium)
        Text(state.status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Row(
            Modifier
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            AgentMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { viewModel.onEvent(ClankerEvent.Mode(mode)) },
                    label = { Text(mode.name) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            items(state.lines) { line ->
                val who = if (line.fromUser) "You" else "Clanker"
                Text("$who: ${line.text}", modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        state.patch?.let { patch ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(patch.label, style = MaterialTheme.typography.titleSmall)
                patch.diffs.forEach { diff ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = diff.path in state.accepted,
                            onCheckedChange = { viewModel.onEvent(ClankerEvent.ToggleFile(diff.path)) },
                        )
                        Text(diff.summary())
                    }
                    Text(
                        diff.unified,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 16,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                    )
                }
            }
            Row {
                Button(onClick = { viewModel.onEvent(ClankerEvent.AcceptAll) }) { Text("Accept") }
                TextButton(onClick = { viewModel.onEvent(ClankerEvent.RejectAll) }) { Text("Reject") }
            }
        }
        if (state.needsKey) {
            Image(
                painter = painterResource(R.drawable.clanker_still),
                contentDescription = WorkshopSemantics.CLANKER_STILL,
                modifier = Modifier.size(72.dp).padding(top = 4.dp),
            )
            Text("configure a key in Settings, or just keep welding files.")
        }
        OutlinedTextField(
            value = state.draft,
            onValueChange = { viewModel.onEvent(ClankerEvent.Draft(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask the Clanker") },
        )
        Row {
            Button(
                onClick = { viewModel.onEvent(ClankerEvent.Send) },
                enabled = !state.running && !state.needsKey,
            ) { Text("Send") }
            TextButton(
                onClick = { viewModel.onEvent(ClankerEvent.Cancel) },
                enabled = state.running,
            ) { Text("Cancel") }
        }
    }
}
