package dev.clankyard.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkWorkshopScheme = darkColorScheme(
    primary = Ember,
    onPrimary = WorkshopNight,
    primaryContainer = RustOrange,
    onPrimaryContainer = OxidizedCream,
    secondary = OxidizedCream,
    onSecondary = WorkshopNight,
    secondaryContainer = Rivet,
    onSecondaryContainer = OxidizedCream,
    tertiary = PatinaBronze,
    onTertiary = WorkshopPaper,
    background = WorkshopNight,
    onBackground = OnNight,
    surface = SurfaceNight,
    onSurface = OnNight,
    surfaceVariant = ScreenBlack,
    onSurfaceVariant = OxidizedCream,
    outline = Rivet,
)

private val LightWorkshopScheme = lightColorScheme(
    primary = RustOrange,
    onPrimary = WorkshopPaper,
    primaryContainer = Ember,
    onPrimaryContainer = WorkshopNight,
    secondary = Rivet,
    onSecondary = WorkshopPaper,
    secondaryContainer = OxidizedCream,
    onSecondaryContainer = OnPaper,
    tertiary = PatinaBronze,
    onTertiary = WorkshopPaper,
    background = WorkshopPaper,
    onBackground = OnPaper,
    surface = SurfacePaper,
    onSurface = OnPaper,
    surfaceVariant = OxidizedCream,
    onSurfaceVariant = Rivet,
    outline = Rivet,
)

@Composable
fun ClankyardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkWorkshopScheme else LightWorkshopScheme,
        typography = ClankyardTypography,
        content = content,
    )
}
