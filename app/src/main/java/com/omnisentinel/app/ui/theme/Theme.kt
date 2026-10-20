package com.omnisentinel.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tactical Cyber Theme Colors
val TacticalBlack = Color(0xFF080C10)
val DarkSurface = Color(0xFF0F1722)
val DarkSurfaceElevated = Color(0xFF162232)
val BorderCyan = Color(0xFF00E5FF)
val NeonCyan = Color(0xFF00E5FF)
val RadarGreen = Color(0xFF00FF7F)
val AlertAmber = Color(0xFFFFB703)
val ThreatCrimson = Color(0xFFFF1744)
val UltrasonicViolet = Color(0xFFB388FF)
val ElectricGold = Color(0xFFFFD600)
val TextPrimary = Color(0xFFF0F4F8)
val TextSecondary = Color(0xFF90A4AE)
val TextMuted = Color(0xFF546E7A)

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = TacticalBlack,
    primaryContainer = Color(0xFF003847),
    onPrimaryContainer = NeonCyan,
    secondary = RadarGreen,
    onSecondary = TacticalBlack,
    tertiary = AlertAmber,
    background = TacticalBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = ThreatCrimson,
    onError = Color.White
)

@Composable
fun OmniSentinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
