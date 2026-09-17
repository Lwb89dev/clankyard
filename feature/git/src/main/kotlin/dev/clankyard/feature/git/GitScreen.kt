package dev.clankyard.feature.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.theme.PathTextStyle

@Composable
fun GitScreen(viewModel: GitViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    GitScreenContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun GitScreenContent(
    state: GitUiState,
    onEvent: (GitUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val branch = state.branch ?: "no branch"
        Text(
            text = if (state.repoPresent) "Git · $branch" else "No git repository",
            style = MaterialTheme.typography.titleLarge,
        )
        Text("Clanker does not commit. You do.", style = MaterialTheme.typography.bodyMedium)
        if (!state.repoPresent && !state.busy) {
            Button(onClick = { onEvent(GitUiEvent.Init) }, enabled = state.identityReady) {
                Text("Init repository")
            }
        }
        IdentityFields(state, onEvent)
        if (state.repoPresent) RepoPanel(state, onEvent)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun ColumnScope.RepoPanel(state: GitUiState, onEvent: (GitUiEvent) -> Unit) {
    CommitFields(state, onEvent)
    StatusLists(state, onEvent, Modifier.weight(1f, fill = false))
    if (state.diffs.isEmpty()) return
    Text("Diff", style = MaterialTheme.typography.titleLarge)
    Text(
        text = state.diffs.joinToString("\n") { it.unified },
        style = PathTextStyle.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = true)
            .verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun IdentityFields(state: GitUiState, onEvent: (GitUiEvent) -> Unit) {
    OutlinedTextField(
        value = state.identityName,
        onValueChange = { onEvent(GitUiEvent.SetIdentityName(it)) },
        label = { Text("user.name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.identityEmail,
        onValueChange = { onEvent(GitUiEvent.SetIdentityEmail(it)) },
        label = { Text("user.email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!state.identityReady) {
        Text("Name and email required before commit.")
    }
}

@Composable
private fun CommitFields(state: GitUiState, onEvent: (GitUiEvent) -> Unit) {
    OutlinedTextField(
        value = state.commitMessage,
        onValueChange = { onEvent(GitUiEvent.SetCommitMessage(it)) },
        label = { Text("Commit message") },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onEvent(GitUiEvent.Commit) }, enabled = state.canCommit) {
        Text("Commit")
    }
}

@Composable
private fun StatusLists(
    state: GitUiState,
    onEvent: (GitUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status ?: return
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        statusSection("Conflicts", status.conflicts, state.selectedPath, onEvent, stage = false, unstage = false)
        statusSection("Staged", status.staged, state.selectedPath, onEvent, stage = false, unstage = true)
        statusSection("Unstaged", status.unstaged, state.selectedPath, onEvent, stage = true, unstage = false)
        statusSection("Untracked", status.untracked, state.selectedPath, onEvent, stage = true, unstage = false)
    }
}

private fun LazyListScope.statusSection(
    title: String,
    paths: List<WorkspacePath>,
    selected: WorkspacePath?,
    onEvent: (GitUiEvent) -> Unit,
    stage: Boolean,
    unstage: Boolean,
) {
    if (paths.isEmpty()) return
    item(key = title) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
    }
    items(paths, key = { "$title/${it.relative}" }) { path ->
        StatusRow(path, selected == path, stage, unstage, onEvent)
    }
}

@Composable
private fun StatusRow(
    path: WorkspacePath,
    selected: Boolean,
    stage: Boolean,
    unstage: Boolean,
    onEvent: (GitUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEvent(GitUiEvent.Select(path)) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val label = if (selected) "▸ ${path.relative}" else path.relative
        Text(label, style = PathTextStyle, modifier = Modifier.weight(1f))
        if (stage) {
            OutlinedButton(onClick = { onEvent(GitUiEvent.Stage(listOf(path))) }) { Text("Stage") }
        }
        if (unstage) {
            OutlinedButton(onClick = { onEvent(GitUiEvent.Unstage(listOf(path))) }) { Text("Unstage") }
        }
    }
}
