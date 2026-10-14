package com.lamesounds.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AnalogueColorScheme = darkColorScheme(
    primary = InkWhite,
    onPrimary = CanvasBlack,
    secondary = InkMuted,
    onSecondary = CanvasBlack,
    background = CanvasBlack,
    onBackground = InkWhite,
    surface = SurfaceDark,
    onSurface = InkWhite,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = InkMuted,
    outline = HairlineBorder,
    outlineVariant = HairlineBright
)

@Composable
fun LameSoundsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AnalogueColorScheme,
        content = content
    )
}
