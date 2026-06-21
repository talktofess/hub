package com.example.recorder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF5B8DEF)

// The app chrome is always a really dark gray (the sims render their own colours).
private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFFF2F4FA),
    background = Color(0xFF161618),
    surface = Color(0xFF1E1E21),
    surfaceVariant = Color(0xFF2A2A2E),
    secondaryContainer = Color(0xFF34343A),
    onSecondaryContainer = Color(0xFFE7E7EA),
    onBackground = Color(0xFFE7E7EA),
    onSurface = Color(0xFFE7E7EA),
    onSurfaceVariant = Color(0xFFB6B6BC),
)

@Composable
fun SimHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
