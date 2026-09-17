package dev.clankyard.app.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.clankyard.app.R
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.core.ui.BottomTab
import dev.clankyard.core.ui.OpenTab
import dev.clankyard.core.ui.WorkshopSemantics
import dev.clankyard.core.ui.theme.PathTextStyle
import dev.clankyard.editor.CodeEditorController
import dev.clankyard.editor.CodeEditorPane
import dev.clankyard.editor.OpenDocument

@Composable
fun EditorHost(
    documents: List<OpenDocument>,
    tabs: List<OpenTab>,
    activePath: WorkspacePath?,
    controller: CodeEditorController,
    onSelectTab: (WorkspacePath) -> Unit,
    onCloseTab: (WorkspacePath) -> Unit,
    onEdit: (WorkspacePath, String) -> Unit,
    onCursor: (WorkspacePath, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    applyImePadding: Boolean = true,
) {
    val active = documents.firstOrNull { it.path == activePath } ?: documents.lastOrNull()
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = WorkshopSemantics.EDITOR_PANE },
    ) {
        if (documents.isNotEmpty()) {
            EditorTabRow(documents, activePath, onSelectTab, onCloseTab)
        }
        if (active == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Open a file from Files.")
            }
        } else {
            val cursor = tabs.firstOrNull { it.path == active.path }
            CodeEditorPane(
                fileName = active.path.name.ifEmpty { active.path.relative },
                text = active.text,
                modifier = Modifier.fillMaxSize(),
                controller = controller,
                contentEpoch = active.contentEpoch,
                cursorLine = cursor?.cursorLine ?: 0,
                cursorCol = cursor?.cursorCol ?: 0,
                applyImePadding = applyImePadding,
                onTextChange = { onEdit(active.path, it) },
                onCursorChange = { line, col -> onCursor(active.path, line, col) },
            )
            active.conflict?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun EditorTabRow(
    documents: List<OpenDocument>,
    activePath: WorkspacePath?,
    onSelectTab: (WorkspacePath) -> Unit,
    onCloseTab: (WorkspacePath) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        documents.forEach { doc ->
            val mark = if (doc.dirty) "• " else ""
            Text(
                text = "$mark${doc.path.name.ifEmpty { doc.path.relative }}",
                style = PathTextStyle,
                color = if (doc.path == activePath) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .clickable { onSelectTab(doc.path) }
                    .padding(8.dp),
            )
            Text(
                text = "×",
                modifier = Modifier
                    .clickable { onCloseTab(doc.path) }
                    .padding(end = 8.dp)
                    .semantics { contentDescription = "Close ${doc.path.name}" },
            )
        }
    }
}

@Composable
fun ClankerPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = WorkshopSemantics.CLANKER_PANE }
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.clanker_still),
            contentDescription = WorkshopSemantics.CLANKER_STILL,
            modifier = Modifier.size(96.dp),
        )
        Text("configure a key", style = MaterialTheme.typography.titleLarge)
        Text(
            "The workshop works without AI. Transcript is ephemeral.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun FeaturePlaceholder(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun BottomToolsPane(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .semantics { contentDescription = WorkshopSemantics.BOTTOM_PANE },
    ) {
        SecondaryScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 8.dp) {
            BottomTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    text = { Text(tab.name) },
                    modifier = Modifier.semantics { contentDescription = tab.name },
                )
            }
        }
        when (selected) {
            BottomTab.Terminal -> FeaturePlaceholder(
                title = "Terminal",
                body = "Sandbox shell lands later. This is a sandbox shell, not a Linux distro.",
            )
            BottomTab.Problems -> FeaturePlaceholder(title = "Problems", body = "No problems yet.")
            BottomTab.Git -> FeaturePlaceholder(
                title = "Git",
                body = "Local status/diff/commit lands later. No clone/pull/push.",
            )
            BottomTab.Output -> FeaturePlaceholder(title = "Output", body = "No output yet.")
        }
    }
}

@Composable
fun VerticalPaneHandle(
    contentDescription: String,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(12.dp)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(onDrag, onDragEnd) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.x)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
fun HorizontalPaneHandle(
    contentDescription: String,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(onDrag, onDragEnd) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
fun SettingsPlaceholder(onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Text(
                "Keys, Git identity, and export live in later changes. " +
                    "The workshop is useful with no AI provider configured.",
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}
