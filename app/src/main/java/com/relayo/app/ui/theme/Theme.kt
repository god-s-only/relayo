package com.relayo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = SignalCyan,
    onPrimary = VoidBlack,
    secondary = SignalCyanDim,
    background = VoidBlack,
    surface = SurfaceElevated,
    surfaceVariant = SurfaceOverlay,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = EmergencyRed
)

private val LightColors = lightColorScheme(
    primary = SignalCyanDim,
    secondary = SignalCyan,
    error = EmergencyRed
)

@Composable
fun RelayoTheme(
    darkTheme:Boolean = isSystemInDarkTheme(),
    content:@Composable () -> Unit
) {
    val colors = if(darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = RelayoTypography,
        content = content
    )
}
