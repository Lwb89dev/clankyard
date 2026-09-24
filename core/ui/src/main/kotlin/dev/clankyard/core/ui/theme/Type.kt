package dev.clankyard.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TerminalFont = FontFamily.Monospace

val PathTextStyle = TextStyle(
    fontFamily = TerminalFont,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
)

private fun TextStyle.terminal(): TextStyle = copy(fontFamily = TerminalFont)

internal val ClankyardTypography: Typography = run {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.terminal(),
        displayMedium = base.displayMedium.terminal(),
        displaySmall = base.displaySmall.terminal(),
        headlineLarge = base.headlineLarge.terminal(),
        headlineMedium = base.headlineMedium.terminal(),
        headlineSmall = base.headlineSmall.terminal(),
        titleLarge = base.titleLarge.terminal(),
        titleMedium = base.titleMedium.terminal(),
        titleSmall = base.titleSmall.terminal(),
        bodyLarge = base.bodyLarge.terminal(),
        bodyMedium = base.bodyMedium.terminal(),
        bodySmall = base.bodySmall.terminal(),
        labelLarge = PathTextStyle,
        labelMedium = base.labelMedium.terminal(),
        labelSmall = base.labelSmall.terminal(),
    )
}
