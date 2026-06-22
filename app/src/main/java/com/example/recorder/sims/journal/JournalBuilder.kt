package com.example.recorder.sims.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.sims.BuilderContext
import com.example.recorder.sims.notes.NoteFont
import com.example.recorder.ui.BubbleColorRow
import com.example.recorder.ui.EnumPicker
import com.example.recorder.ui.LabeledSlider
import com.example.recorder.ui.SectionLabel
import com.example.recorder.ui.WritingSoundPicker
import kotlin.math.roundToInt

@Composable
fun JournalBuilder(ctx: BuilderContext) {
    val s = JournalStore
    var selId by remember { mutableStateOf(s.elements.firstOrNull()?.id) }
    var drawMode by remember { mutableStateOf(false) }
    val livePts = remember { mutableStateListOf<Offset>() }
    val sel = s.elements.firstOrNull { it.id == selId }
    val fonts = NoteFont.values()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(if (drawMode) "Draw a doodle on the canvas" else "Tap empty space to add text · tap a piece to edit · drag to move")

        Box(
            Modifier.fillMaxWidth().aspectRatio(1080f / 1920f).clip(RoundedCornerShape(10.dp))
                .background(if (s.fill) Color(s.paper) else Color(s.backdrop)),
            contentAlignment = Alignment.Center,
        ) {
            val surfaceMod = if (s.fill) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth(s.widthPct.coerceIn(0.2f, 1f)).fillMaxHeight().background(Color(s.paper))
            BoxWithConstraints(
                surfaceMod.pointerInput(drawMode) {
                    if (drawMode) {
                        detectDragGestures(
                            onDragStart = { livePts.clear(); livePts.add(it) },
                            onDrag = { ch, _ -> livePts.add(ch.position) },
                            onDragEnd = {
                                s.addDoodle(livePts.toList(), size.width.toFloat(), size.height.toFloat())?.let { selId = it.id }
                                livePts.clear(); drawMode = false
                            },
                            onDragCancel = { livePts.clear(); drawMode = false },
                        )
                    } else {
                        detectTapGestures { pos -> selId = s.add(pos.x / size.width, pos.y / size.height).id }
                    }
                },
            ) {
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val scale = maxWidth.value / 1080f

                // render in list order (z-stack): text pieces are interactive Boxes, doodles
                // are drawn into their own Canvas — so stacking matches the real output.
                s.elements.forEach { el ->
                    if (el.kind == ElKind.DOODLE) {
                        Canvas(Modifier.fillMaxSize()) { drawDoodle(el, 1f) }
                    } else {
                        val selected = el.id == selId
                        Box(
                            Modifier.align(Alignment.Center)
                                .offset { IntOffset(((el.xPct - 0.5f) * w).roundToInt(), ((el.yPct - 0.5f) * h).roundToInt()) }
                                .rotate(el.rotation)
                                .border(if (selected) 1.5.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(4.dp))
                                .padding(2.dp)
                                .pointerInput(el.id) { detectTapGestures { selId = el.id } }
                                .pointerInput(el.id) {
                                    detectDragGestures(onDragStart = { selId = el.id }) { _, drag ->
                                        el.xPct = (el.xPct + drag.x / w).coerceIn(0.02f, 0.98f)
                                        el.yPct = (el.yPct + drag.y / h).coerceIn(0.02f, 0.98f)
                                    }
                                },
                        ) {
                            Text(el.text.ifEmpty { "·" }, color = Color(el.color).copy(alpha = el.opacity), fontFamily = el.font.family, fontSize = (62f * el.size * scale).sp, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // the live stroke being drawn
                if (livePts.size > 1) {
                    Canvas(Modifier.fillMaxSize()) {
                        val p = Path().apply {
                            moveTo(livePts[0].x, livePts[0].y)
                            for (i in 1 until livePts.size) lineTo(livePts[i].x, livePts[i].y)
                        }
                        drawPath(p, Color(s.defColor), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }

                // doodle handles — a dot at each doodle's anchor to select / drag (always on top)
                s.elements.forEach { el ->
                    if (el.kind != ElKind.DOODLE) return@forEach
                    val selected = el.id == selId
                    Box(
                        Modifier.align(Alignment.Center)
                            .offset { IntOffset(((el.xPct - 0.5f) * w).roundToInt(), ((el.yPct - 0.5f) * h).roundToInt()) }
                            .size(if (selected) 18.dp else 13.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color(0x66000000))
                            .pointerInput(el.id) { detectTapGestures { selId = el.id } }
                            .pointerInput(el.id) {
                                detectDragGestures(onDragStart = { selId = el.id }) { _, drag ->
                                    el.xPct = (el.xPct + drag.x / w).coerceIn(0.02f, 0.98f)
                                    el.yPct = (el.yPct + drag.y / h).coerceIn(0.02f, 0.98f)
                                }
                            },
                    ) {}
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { drawMode = false; selId = s.add(0.5f, 0.5f).id }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Text(" Text")
            }
            OutlinedButton(onClick = { drawMode = !drawMode }) {
                Icon(Icons.Filled.Gesture, null, Modifier.size(18.dp)); Text(if (drawMode) " Drawing…" else " Doodle")
            }
            if (sel != null) {
                TextButton(onClick = { s.remove(sel.id); selId = s.elements.firstOrNull()?.id }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Text(" Delete")
                }
            }
        }

        if (sel != null) {
            SectionLabel(if (sel.kind == ElKind.DOODLE) "Selected doodle" else "Selected text")
            if (sel.kind == ElKind.TEXT) {
                OutlinedTextField(sel.text, { sel.text = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                EnumPicker("Font", sel.font.label, fonts.map { it.label }) { sel.font = fonts[it] }
            }
            BubbleColorRow("Colour", sel.color) { sel.color = it }
            LabeledSlider(if (sel.kind == ElKind.DOODLE) "Thickness / scale" else "Size", sel.size, 0.4f..3.2f, "%.2f×") { sel.size = it }
            LabeledSlider("Ink opacity", sel.opacity, 0.1f..1f, "%.0f%%") { sel.opacity = it }
            LabeledSlider("Rotation", sel.rotation, -180f..180f, "%.0f°") { sel.rotation = it }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0f, 90f, 180f, 270f).forEach { deg ->
                    OutlinedButton(onClick = { sel.rotation = if (deg > 180f) deg - 360f else deg }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                        Text("${deg.toInt()}°", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            // stacking (z-order): later in the list draws on top
            Text("Stacking", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val pad = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                OutlinedButton(onClick = { s.toBack(sel.id) }, contentPadding = pad) { Text("⤓ Back", style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(onClick = { s.backward(sel.id) }, contentPadding = pad) { Text("↓", style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(onClick = { s.forward(sel.id) }, contentPadding = pad) { Text("↑", style = MaterialTheme.typography.labelMedium) }
                OutlinedButton(onClick = { s.toFront(sel.id) }, contentPadding = pad) { Text("⤒ Front", style = MaterialTheme.typography.labelMedium) }
            }
            val maxOrder = (s.elements.size - 1).coerceAtLeast(1)
            LabeledSlider("Write order (lower = written first)", sel.order.toFloat().coerceIn(0f, maxOrder.toFloat()), 0f..maxOrder.toFloat(), "%.0f") { sel.order = it.roundToInt() }
        }

        SectionLabel("Surface")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = s.fill, onCheckedChange = { s.fill = it })
            Text(if (s.fill) "Fills the whole screen" else "Sits centred on screen", style = MaterialTheme.typography.bodyMedium)
        }
        if (!s.fill) {
            LabeledSlider("Surface width", s.widthPct, 0.3f..1f, "%.0f%%") { s.widthPct = it }
            LabeledSlider("Surface height", s.heightPct, 0.3f..1f, "%.0f%%") { s.heightPct = it }
            BubbleColorRow("Behind the surface", s.backdrop) { s.backdrop = it }
        }
        BubbleColorRow("Paper / surface colour", s.paper) { s.paper = it }
        LabeledSlider("Corner rounding", s.corner, 0f..80f, "%.0f") { s.corner = it }
        val lineOpts = SurfaceLines.values()
        EnumPicker("Lines", s.lines.label, lineOpts.map { it.label }) { s.lines = lineOpts[it] }
        if (s.lines != SurfaceLines.NONE) BubbleColorRow("Line colour", s.lineColor) { s.lineColor = it }

        SectionLabel("New-piece defaults")
        EnumPicker("Default font", s.defFont.label, fonts.map { it.label }) { s.defFont = fonts[it] }
        BubbleColorRow("Default colour", s.defColor) { s.defColor = it }

        SectionLabel("Writing & sound")
        LabeledSlider("Writing speed", s.typeSpeed, 0.3f..3f, "%.2f×") { s.typeSpeed = it }
        WritingSoundPicker("Writing sound", s.keySound) { s.keySound = it }
    }
}
