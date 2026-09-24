package dev.clankyard.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
        darkWindowStart = Color(0xFF2A1C15),
        darkWindowEnd = Color(0xFF15100D),
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
        darkWindowStart = Color(0xFF13291B),
        darkWindowEnd = Color(0xFF0B1510),
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
        darkWindowStart = Color(0xFF251832),
        darkWindowEnd = Color(0xFF130E1A),
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
        darkWindowStart = Color(0xFF30171A),
        darkWindowEnd = Color(0xFF190E10),
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
        darkWindowStart = Color(0xFF122A34),
        darkWindowEnd = Color(0xFF0B171D),
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
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        tonalElevation = tonalElevation,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WorkshopWindowBrush()),
            content = content,
        )
    }
}

private fun darkWorkshopScheme(theme: WorkshopTheme): ColorScheme = darkColorScheme(
    primary = theme.darkPrimary,
    onPrimary = WorkshopNight,
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
    surface = theme.darkWindowStart,
    onSurface = OnNight,
    surfaceVariant = theme.darkWindowEnd,
    onSurfaceVariant = OxidizedCream,
    outline = theme.darkPrimaryContainer,
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
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalWorkshopGradient provides gradient,
            content = content,
        )
    }
}
