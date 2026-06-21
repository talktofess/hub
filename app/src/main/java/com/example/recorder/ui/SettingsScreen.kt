package com.example.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.recorder.model.AppSettings

/** Universal app settings (apply to every sim/take). Opened from the hub. */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back to hub") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Recording")
            val fpsOpts = listOf(24, 30, 48, 60)
            EnumPicker("Frame rate", "${AppSettings.fps} fps", fpsOpts.map { "$it fps" }) { AppSettings.updateFps(fpsOpts[it]) }
            val scaleOpts = listOf(1f, 1.5f, 2f)
            val scaleLabels = listOf("1× — 1080×1920", "1.5× — 1620×2880", "2× — 2160×3840 (4K)")
            val curScale = scaleOpts.indexOfFirst { kotlin.math.abs(it - AppSettings.recordScale) < 0.01f }.coerceAtLeast(0)
            EnumPicker("Quality (supersample)", scaleLabels[curScale], scaleLabels) { AppSettings.updateRecordScale(scaleOpts[it]) }
            Text(
                "Higher frame rate and quality make smoother, sharper recordings but produce larger files and are heavier on the device. Per-sim typing speed, sound, and look live in each sim's own config.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
