package dev.clankyard.feature.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.clankyard.core.ui.theme.PathTextStyle
import dev.clankyard.diff.DiffEngine
import dev.clankyard.diff.MyersDiffEngine

enum class DiffLineKind { Header, Hunk, Added, Removed, Context, Other }

data class DiffLine(val kind: DiffLineKind, val text: String)

fun parseUnifiedLines(unified: String): List<DiffLine> {
    if (unified.isEmpty()) return emptyList()
    return unified.split('\n').map { line ->
        DiffLine(kindOf(line), line)
    }
}

private fun kindOf(line: String): DiffLineKind = when {
    line.startsWith("+++") || line.startsWith("---") -> DiffLineKind.Header
    line.startsWith("@@") -> DiffLineKind.Hunk
    line.startsWith("+") -> DiffLineKind.Added
    line.startsWith("-") -> DiffLineKind.Removed
    line.startsWith(" ") || line.isEmpty() -> DiffLineKind.Context
    else -> DiffLineKind.Other
}

@Composable
fun UnifiedDiffPane(
    unified: String,
    modifier: Modifier = Modifier,
) {
    val lines = parseUnifiedLines(unified)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        for (line in lines) {
            Text(
                text = line.text.ifEmpty { " " },
                style = PathTextStyle.copy(fontFamily = FontFamily.Monospace),
                color = colorFor(line.kind),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun UnifiedDiffPane(
    beforeUtf8: String,
    afterUtf8: String,
    pathLabel: String,
    engine: DiffEngine = MyersDiffEngine(),
    modifier: Modifier = Modifier,
) {
    UnifiedDiffPane(engine.unified(beforeUtf8, afterUtf8, pathLabel), modifier)
}

@Composable
private fun colorFor(kind: DiffLineKind): Color {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        DiffLineKind.Added -> scheme.tertiary
        DiffLineKind.Removed -> scheme.primary
        DiffLineKind.Hunk -> scheme.secondary
        DiffLineKind.Header -> scheme.onSurfaceVariant
        DiffLineKind.Context, DiffLineKind.Other -> scheme.onSurface
    }
}
