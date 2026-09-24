package dev.clankyard.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class WorkshopGradientColors(
    val start: Color,
    val end: Color,
)

val LocalWorkshopGradient = staticCompositionLocalOf {
    WorkshopGradientColors(
        start = WorkshopNight,
        end = SurfaceNight,
    )
}

enum class WorkshopTheme(
    val id: String,
    val label: String,
    internal val lightPrimary: Color,
    internal val lightPrimaryContainer: Color,
    internal val darkPrimary: Color,
    internal val darkPrimaryContainer: Color,
    internal val lightWindowStart: Color,
    internal val lightWindowEnd: Color,
    internal val darkWindowStart: Color,
    internal val darkWindowEnd: Color,
) {
    Rust(
        id = "rust",
        label = "Rust",
        lightPrimary = RustOrange,
        lightPrimaryContainer = Ember,
        darkPrimary = Ember,
        darkPrimaryContainer = RustOrange,
        lightWindowStart = Color(0xFFF8EBD9),
        lightWindowEnd = Color(0xFFE6D1B8),
        darkWindowStart = Color(0xFF261A15),
        darkWindowEnd = Color(0xFF0E0C0B),
    ),
    Terminal(
        id = "terminal",
        label = "Terminal",
        lightPrimary = Color(0xFF187A37),
        lightPrimaryContainer = Color(0xFFB9EFC8),
        darkPrimary = Color(0xFF63D889),
        darkPrimaryContainer = Color(0xFF1F6E39),
        lightWindowStart = Color(0xFFEEF9F0),
        lightWindowEnd = Color(0xFFD5EBDD),
        darkWindowStart = Color(0xFF102419),
        darkWindowEnd = Color(0xFF080D0A),
    ),
    Nostr(
        id = "nostr",
        label = "Nostr",
        lightPrimary = Color(0xFF7135A8),
        lightPrimaryContainer = Color(0xFFE8D1FF),
        darkPrimary = Color(0xFFCA9BFF),
        darkPrimaryContainer = Color(0xFF7135A8),
        lightWindowStart = Color(0xFFF7EEFF),
        lightWindowEnd = Color(0xFFE3D1F2),
        darkWindowStart = Color(0xFF21142D),
        darkWindowEnd = Color(0xFF0D0912),
    ),
    Firered(
        id = "firered",
        label = "Firered",
        lightPrimary = Color(0xFFB3261E),
        lightPrimaryContainer = Color(0xFFFFDAD6),
        darkPrimary = Color(0xFFFF8A80),
        darkPrimaryContainer = Color(0xFFB3261E),
        lightWindowStart = Color(0xFFFFF0EE),
        lightWindowEnd = Color(0xFFF3D4D1),
        darkWindowStart = Color(0xFF2B1416),
        darkWindowEnd = Color(0xFF10090A),
    ),
    Deepsea(
        id = "deepsea",
        label = "Deepsea",
        lightPrimary = Color(0xFF006B8F),
        lightPrimaryContainer = Color(0xFFB8E8FA),
        darkPrimary = Color(0xFF63C7F2),
        darkPrimaryContainer = Color(0xFF006B8F),
        lightWindowStart = Color(0xFFECF9FD),
        lightWindowEnd = Color(0xFFD1EAF2),
        darkWindowStart = Color(0xFF102630),
        darkWindowEnd = Color(0xFF080E11),
    ),
    ;

    companion object {
        fun fromId(id: String?): WorkshopTheme =
            entries.firstOrNull { it.id == id } ?: Rust
    }
}

@Composable
fun WorkshopWindowBrush(): Brush {
    val colors = LocalWorkshopGradient.current
    return Brush.linearGradient(listOf(colors.start, colors.end))
}

@Composable
fun WorkshopWindowSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tonalElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        // The surface must respect the slot that owns it. A fillMaxSize here
        // makes a top/bottom Scaffold slot consume the whole viewport and
        // hides the workspace panes underneath it.
        modifier = modifier
            .background(brush = WorkshopWindowBrush(), shape = shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                shape = shape,
            ),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = tonalElevation,
    ) {
        Box(
            content = content,
        )
    }
}

private fun darkWorkshopScheme(theme: WorkshopTheme): ColorScheme = darkColorScheme(
    primary = theme.darkPrimary,
    onPrimary = SteelNight,
    primaryContainer = theme.darkPrimaryContainer,
    onPrimaryContainer = OxidizedCream,
    secondary = theme.darkPrimary,
    onSecondary = WorkshopNight,
    secondaryContainer = theme.darkPrimaryContainer,
    onSecondaryContainer = OxidizedCream,
    tertiary = theme.darkPrimaryContainer,
    onTertiary = WorkshopPaper,
    background = theme.darkWindowEnd,
    onBackground = OnNight,
    surface = SteelNight,
    onSurface = OnNight,
    surfaceVariant = theme.darkWindowStart,
    onSurfaceVariant = MutedSteel,
    outline = theme.darkPrimary.copy(alpha = 0.72f),
    outlineVariant = Rivet,
    error = Color(0xFFFF766D),
    onError = SteelNight,
)

private fun lightWorkshopScheme(theme: WorkshopTheme): ColorScheme = lightColorScheme(
    primary = theme.lightPrimary,
    onPrimary = WorkshopPaper,
    primaryContainer = theme.lightPrimaryContainer,
    onPrimaryContainer = WorkshopNight,
    secondary = theme.lightPrimary,
    onSecondary = WorkshopPaper,
    secondaryContainer = theme.lightPrimaryContainer,
    onSecondaryContainer = OnPaper,
    tertiary = theme.lightPrimaryContainer,
    onTertiary = OnPaper,
    background = theme.lightWindowEnd,
    onBackground = OnPaper,
    surface = theme.lightWindowStart,
    onSurface = OnPaper,
    surfaceVariant = theme.lightWindowEnd,
    onSurfaceVariant = Rivet,
    outline = theme.lightPrimary,
    outlineVariant = Rivet.copy(alpha = 0.62f),
)

private val ClankyardShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

@Composable
fun ClankyardTheme(
    theme: WorkshopTheme = WorkshopTheme.Rust,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val gradient = if (darkTheme) {
        WorkshopGradientColors(theme.darkWindowStart, theme.darkWindowEnd)
    } else {
        WorkshopGradientColors(theme.lightWindowStart, theme.lightWindowEnd)
    }
    MaterialTheme(
        colorScheme = if (darkTheme) darkWorkshopScheme(theme) else lightWorkshopScheme(theme),
        typography = ClankyardTypography,
        shapes = ClankyardShapes,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalWorkshopGradient provides gradient,
            content = content,
        )
    }
}
