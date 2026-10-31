package com.stfc.overlay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SpaceNavyDark = Color(0xFF060B16)
val SpaceSurface = Color(0xFF0D1627)
val SpaceCard = Color(0xFF152238)
val LcarsCyan = Color(0xFF00E5FF)
val LcarsGold = Color(0xFFFFB300)
val LcarsCoral = Color(0xFFFF6E40)
val LcarsRed = Color(0xFFFF5252)
val TextPrimary = Color(0xFFE6F1FF)
val TextSecondary = Color(0xFF8FA4C4)

private val StfcDarkColorScheme = darkColorScheme(
    primary = LcarsCyan,
    onPrimary = SpaceNavyDark,
    secondary = LcarsGold,
    onSecondary = SpaceNavyDark,
    tertiary = LcarsCoral,
    background = SpaceNavyDark,
    onBackground = TextPrimary,
    surface = SpaceSurface,
    onSurface = TextPrimary,
    surfaceVariant = SpaceCard,
    onSurfaceVariant = TextSecondary,
    error = LcarsRed
)

@Composable
fun StfcOverlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StfcDarkColorScheme,
        content = content
    )
}
