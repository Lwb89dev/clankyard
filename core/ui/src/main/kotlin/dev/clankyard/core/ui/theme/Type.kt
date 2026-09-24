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
private fun TextStyle.terminalTitle(weight: FontWeight = FontWeight.Bold): TextStyle = copy(
    fontFamily = TerminalFont,
    fontWeight = weight,
    letterSpacing = 0.4.sp,
)

internal val ClankyardTypography: Typography = run {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.terminalTitle(FontWeight.Black),
        displayMedium = base.displayMedium.terminalTitle(FontWeight.Black),
        displaySmall = base.displaySmall.terminalTitle(FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.terminalTitle(FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.terminalTitle(FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.terminalTitle(),
        titleLarge = base.titleLarge.terminalTitle(FontWeight.ExtraBold),
        titleMedium = base.titleMedium.terminalTitle(),
        titleSmall = base.titleSmall.terminalTitle(),
        bodyLarge = base.bodyLarge.terminal(),
        bodyMedium = base.bodyMedium.terminal(),
        bodySmall = base.bodySmall.terminal(),
        labelLarge = PathTextStyle,
        labelMedium = base.labelMedium.terminalTitle(FontWeight.SemiBold),
        labelSmall = base.labelSmall.terminalTitle(FontWeight.SemiBold),
    )
}
