package com.example.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.SimRuntime
import com.example.recorder.recording.Gallery
import com.example.recorder.recording.RecordController
import com.example.recorder.sims.SimDef
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Full-screen stage. The sim auto-plays as a preview on entry. The overlay
 * controls (Play/Stop, Record/Stop, Exit) are drawn OUTSIDE the capture layer,
 * so they never appear in the recording — only the sim's [CaptureStage] is
 * encoded. Record restarts the take from the top and captures the whole run.
 */
@Composable
fun PresentScreen(
    rt: SimRuntime,
    sim: SimDef,
    onExit: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
) {
    val recording = RecordController.isRecording
    val captureLayer = rememberGraphicsLayer()

    // Toasts auto-dismiss so they don't sit on screen after a take finishes.
    var savedToast by remember { mutableStateOf(false) }
    var errToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(RecordController.savedCount) {
        if (RecordController.savedCount > 0) { savedToast = true; delay(3500); savedToast = false }
    }
    LaunchedEffect(RecordController.lastError) {
        errToast = RecordController.lastError
        if (errToast != null) { delay(5000); errToast = null }
    }

    // The stage opens paused — the sim shows its settled preview and waits for Play.

    // Recording: reset any current preview, restart the take from the top, capture
    // every frame, auto-stop when it ends.
    LaunchedEffect(recording) {
        if (!recording) {
            rt.oneShot = false
            return@LaunchedEffect
        }
        rt.stop() // reset any in-progress preview before recording
        rt.oneShot = true
        // Capture on a background dispatcher: toImageBitmap() is a heavy GPU
        // read-back, and running it on the main thread starved the typing engine
        // and keystroke audio (the take appeared to "freeze" while recording).
        val pump = launch(Dispatchers.Default) {
            while (isActive && RecordController.isRecording) {
                try {
                    RecordController.recorder?.encodeFrame(captureLayer.toImageBitmap())
                } catch (_: Throwable) { /* layer not ready yet */ }
                delay(33) // ~30fps ceiling; yields so typing/audio stay smooth
            }
        }
        delay(300)
        rt.play() // deterministic restart from the top
        val started = withTimeoutOrNull(2500) { snapshotFlow { rt.playing }.first { it } } != null
        if (started) snapshotFlow { rt.playing }.first { !it }
        delay(800)
        pump.cancel()
        onStopRecord()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CaptureStage(sim.logical, captureLayer, recordScale = rt.settings.recordScale) {
            SimBackground(rt, previewMode = false)
            sim.Content(rt)
        }

        // ----- overlays (never captured) -----

        CircleButton(Modifier.align(Alignment.TopEnd).padding(16.dp), onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = "exit", tint = Color.White)
        }

        if (recording) {
            Row(
                Modifier.align(Alignment.TopStart).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Color(0xFFE5484D), modifier = Modifier.size(16.dp))
                Text("REC", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play / Stop (preview) — only when not recording
            if (!recording) {
                PillButton(
                    icon = if (rt.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    text = if (rt.playing) "Stop" else "Play",
                    bg = Color.White.copy(alpha = 0.16f),
                    onClick = {
                        if (rt.playing) rt.stop()
                        else { rt.oneShot = false; rt.play() }
                    },
                )
            }
            // Record / Stop recording
            if (Gallery.supported) {
                PillButton(
                    icon = if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    text = if (recording) "Stop recording" else "Record",
                    bg = Color(0xFFE5484D),
                    onClick = { if (recording) onStopRecord() else onStartRecord() },
                )
            }
        }

        if (!recording) {
            if (errToast != null) {
                Toast(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp), "⚠  $errToast", Color(0xFF7A1C1C))
            } else if (savedToast) {
                Toast(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                    "✓  Recording saved to your gallery\nFind it in Movies › SimHub",
                    Color(0xFF1C7A45),
                )
            }
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector, text: String, bg: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = text, tint = Color.White)
        Text(text, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CircleButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Toast(modifier: Modifier, text: String, bg: Color) {
    Surface(modifier, color = bg, shape = MaterialTheme.shapes.large) {
        Text(text, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}
