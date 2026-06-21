package com.example.recorder.sims.journal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A hand-drawn mark over a span of a line. Markup in the journal text:
 *   [o]…[/o]  circle the span      [u]…[/u]  underline
 *   [x]…[/x]  strike out (cancel)  a line that is just `---`  → a divider line. */
enum class AnnoType { UNDERLINE, STRIKE, CIRCLE, HR }

data class Anno(val type: AnnoType, val start: Int, val end: Int)

/** A parsed line: the clean text that gets written + the marks drawn over it. */
data class PLine(val text: String, val annos: List<Anno>)

fun parseJournalLine(raw: String): PLine {
    if (raw.trim() == "---") return PLine(" ", listOf(Anno(AnnoType.HR, 0, 1)))
    val annos = mutableListOf<Anno>()
    val sb = StringBuilder()
    var openType: AnnoType? = null
    var openStart = 0
    var i = 0
    while (i < raw.length) {
        val three = if (i + 3 <= raw.length) raw.substring(i, i + 3) else ""
        val four = if (i + 4 <= raw.length) raw.substring(i, i + 4) else ""
        val open = when (three) {
            "[u]" -> AnnoType.UNDERLINE; "[x]" -> AnnoType.STRIKE; "[o]" -> AnnoType.CIRCLE; else -> null
        }
        if (open != null) { openType = open; openStart = sb.length; i += 3; continue }
        if (openType != null && (four == "[/u]" || four == "[/x]" || four == "[/o]")) {
            annos.add(Anno(openType!!, openStart, sb.length)); openType = null; i += 4; continue
        }
        sb.append(raw[i]); i++
    }
    if (openType != null) annos.add(Anno(openType!!, openStart, sb.length))
    return PLine(sb.toString(), annos)
}

/** Roughly how long the mark takes to draw (ms). */
fun annoDurMs(type: AnnoType): Int = when (type) { AnnoType.CIRCLE -> 720; AnnoType.HR -> 520; else -> 420 }

/** Draw a mark over [x0]..[x1] within a line box of height [h], up to [progress]. */
fun DrawScope.drawAnno(
    type: AnnoType, x0: Float, x1: Float, h: Float, fontPx: Float, progress: Float, color: Color, seed: Int,
) {
    if (progress <= 0f) return
    val stroke = Stroke(width = max(3f, fontPx * 0.055f), cap = StrokeCap.Round)
    val path = when (type) {
        AnnoType.UNDERLINE, AnnoType.HR -> wavy(x0, x1, h * 0.90f, fontPx, seed)
        AnnoType.STRIKE -> wavy(x0, x1, h * 0.56f, fontPx, seed)
        AnnoType.CIRCLE -> ellipse(x0, x1, h, fontPx, seed)
    }
    val pm = PathMeasure(); pm.setPath(path, false)
    val seg = Path(); pm.getSegment(0f, pm.length * progress.coerceIn(0f, 1f), seg, true)
    drawPath(seg, color, style = stroke)
}

private fun wavy(x0: Float, x1: Float, y: Float, fontPx: Float, seed: Int): Path {
    val p = Path(); val n = 26; val amp = fontPx * 0.028f
    p.moveTo(x0, y)
    for (k in 1..n) {
        val t = k / n.toFloat()
        val x = x0 + (x1 - x0) * t
        val yy = y + sin(t * PI.toFloat() * 3f + seed).toFloat() * amp
        p.lineTo(x, yy)
    }
    return p
}

private fun ellipse(x0: Float, x1: Float, h: Float, fontPx: Float, seed: Int): Path {
    val cx = (x0 + x1) / 2f
    val cy = h * 0.52f
    val rx = (x1 - x0) / 2f + fontPx * 0.32f
    val ry = h * 0.50f + fontPx * 0.10f
    val p = Path(); val n = 64
    val start = -1.4f                     // start near the top
    val sweep = (2 * PI.toFloat()) * 1.08f // overshoot past the start, like a real loop
    for (k in 0..n) {
        val a = start + sweep * (k / n.toFloat())
        val wob = 1f + 0.045f * sin(a * 3f + seed)
        val x = cx + cos(a) * rx * wob
        val y = cy + sin(a) * ry * wob
        if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    return p
}
