package com.example.recorder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF5B8DEF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF15161B),
    surfaceVariant = Color(0xFF1E2027),
    onBackground = Color(0xFFE7E7EA),
    onSurface = Color(0xFFE7E7EA),
)

private val LightColors = lightColorScheme(primary = Accent)

@Composable
fun SimHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
