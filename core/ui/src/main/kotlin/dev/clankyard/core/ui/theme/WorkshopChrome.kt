package dev.clankyard.core.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A restrained technical grid that gives the workshop depth without hurting readability. */
@Composable
fun WorkshopBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val gridColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
    val crossColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    Box(modifier = modifier.background(WorkshopWindowBrush())) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 32.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
            drawLine(crossColor, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 1f)
            drawLine(crossColor, Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height), 1f)
        }
        val boxScope = this
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) {
            boxScope.content()
        }
    }
}

@Composable
fun WorkshopPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    accent: Boolean = false,
    tonalElevation: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val borderColor = if (accent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }
    Surface(
        modifier = modifier.border(1.dp, borderColor, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = tonalElevation,
    ) {
        Box(content = content)
    }
}

@Composable
fun WorkshopSectionHeader(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 28.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primary),
        )
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                text = kicker.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke(this)
    }
}

@Composable
fun WorkshopStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val signal = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(signal.copy(alpha = 0.12f))
            .border(1.dp, signal.copy(alpha = 0.54f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(signal))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = signal,
            maxLines = 1,
        )
    }
}

@Composable
fun WorkshopHazardStrip(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier.fillMaxWidth()) {
        drawRect(color.copy(alpha = 0.18f))
        val stripe = 18.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = color.copy(alpha = 0.72f),
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = stripe * 0.34f,
            )
            x += stripe
        }
    }
}
