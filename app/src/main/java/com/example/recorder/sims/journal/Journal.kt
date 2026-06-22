package com.example.recorder.sims.journal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.SimFrame
import com.example.recorder.sims.SimLogical
import org.json.JSONObject

private const val LINE_H = 96f       // ruling spacing at fs = 1
private const val TOP_PAD = 120f     // where the first written line sits
private const val MARGIN_X = 110f    // red margin rule, from the page's left edge
private const val TEXT_INDENT = 134f // text starts just past the margin
private const val TEXT_END = 38f     // right padding inside the page
private const val PAGE_PAD = 56f     // dark border around the page
private const val USABLE_W = 1080f - 2 * PAGE_PAD - TEXT_INDENT - TEXT_END
private const val DRAW_MS = 78f      // time to drag one letter on

object JournalSim : SimDef {
    override val id = "journal"
    override val label = "Journal"
    override val glyph = "📓"
    override val accent = Color(0xFFC85A5A)
    override val frame = SimFrame.PHONE
    override val logical = SimLogical(1080, 1920)
    override val ready = true
    override val defaultScript = ""

    override fun reset() = JournalStore.reset()
    override val tabLabel = "Entry"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = JournalBuilder(ctx)

    override fun toJson() = JSONObject().apply {
        put("text", JournalStore.text); put("date", JournalStore.date); put("font", JournalStore.font.name)
        put("ink", JournalStore.ink); put("paper", JournalStore.paper); put("textScale", JournalStore.textScale.toDouble())
        put("messiness", JournalStore.messiness.toDouble()); put("scatter", JournalStore.scatter.toDouble())
        put("typeSpeed", JournalStore.typeSpeed.toDouble()); put("pacing", JournalStore.pacing.toDouble())
        put("keySound", JournalStore.keySound.name)
    }

    override fun fromJson(o: JSONObject) {
        JournalStore.text = o.optString("text", JournalStore.text)
        JournalStore.date = o.optString("date", JournalStore.date)
        JournalStore.font = runCatching { com.example.recorder.sims.notes.NoteFont.valueOf(o.optString("font")) }.getOrDefault(com.example.recorder.sims.notes.NoteFont.MARKER)
        JournalStore.ink = o.optLong("ink", JournalStore.ink)
        JournalStore.paper = o.optLong("paper", JournalStore.paper)
        JournalStore.textScale = o.optDouble("textScale", 1.0).toFloat()
        JournalStore.messiness = o.optDouble("messiness", 0.45).toFloat()
        JournalStore.scatter = o.optDouble("scatter", 0.0).toFloat()
        JournalStore.typeSpeed = o.optDouble("typeSpeed", 0.8).toFloat()
        JournalStore.pacing = o.optDouble("pacing", 0.6).toFloat()
        JournalStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.STYLUS)
    }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale * JournalStore.textScale
        val s = JournalStore
        val lineH = LINE_H * fs

        // Auto-fit the hand to the page width. The TextMeasurer mis-measures the bundled
        // font (caches the fallback width), so instead measure through the real layout
        // pipeline via onTextLayout on hidden Texts below — that uses the resolved font
        // and self-corrects once it loads.
        var measuredMaxW by remember(s.text, s.font, fs) { mutableFloatStateOf(0f) }
        // parse each line into clean text (what's written) + the marks drawn over it
        val plines = remember(s.text) { s.text.split("\n").map { parseJournalLine(it) } }
        val lines = remember(plines) { plines.map { it.text } }
        val fitSrc = lines
        // Cap at a size that reliably fits ~22 wide chars even if the async measurement
        // hasn't corrected yet; the onTextLayout fit shrinks further for longer lines.
        val fontPx = minOf(54f * fs, if (measuredMaxW > USABLE_W) 70f * fs * (USABLE_W / measuredMaxW) else 70f * fs)

        // Written one letter at a time: each new letter is dragged on left-to-right
        // (the pen stroke), then the next — no caret, no pop, no slide.
        val doneLines = remember { mutableStateListOf<Int>() } // which lines are fully written (any order)
        var activeIdx by remember { mutableIntStateOf(-1) }
        var activeLen by remember { mutableIntStateOf(0) }
        var charKey by remember { mutableIntStateOf(0) }
        var dateShown by remember { mutableStateOf(false) }
        var revision by remember { mutableIntStateOf(0) }
        // hand-drawn marks (underline / circle / strike / divider) draw after their line
        val doneAnnos = remember { mutableStateListOf<String>() }
        var drawingLine by remember { mutableIntStateOf(-1) }
        var drawingAnnoIdx by remember { mutableIntStateOf(-1) }
        var annoDur by remember { mutableIntStateOf(420) }
        var annoKey by remember { mutableIntStateOf(0) }

        fun buildPlan(): List<TypeStep> {
            val steps = mutableListOf<TypeStep>()
            val drawMs = (DRAW_MS / s.typeSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(16)
            steps.add(TypeStep.Reveal({
                doneLines.clear(); activeIdx = -1; activeLen = 0; dateShown = false; drawingLine = -1; doneAnnos.clear(); revision++
                rt.audio.profile = s.keySound
            }))
            if (s.date.isNotBlank()) steps.add(TypeStep.Reveal({ dateShown = true; revision++ }, delay = 380))
            // when scattered, write the lines in a shuffled order (a bottom note first, a top
            // side-note later, …) — deterministic so it replays the same.
            val order = if (s.scatter > 0f) plines.indices.sortedBy { jitter(99, it, 20) } else plines.indices.toList()
            order.forEach { idx ->
                val pl = plines[idx]
                val ln = pl.text
                if (ln.isBlank() && pl.annos.isEmpty()) {
                    steps.add(TypeStep.Reveal({ doneLines.add(idx); revision++ }))
                    steps.add(TypeStep.Pause(120))
                } else {
                    for (j in 1..ln.length) {
                        val ch = ln[j - 1]
                        // one soft graphite stroke per letter — sound stays in step with the hand
                        steps.add(TypeStep.Reveal({ activeIdx = idx; activeLen = j; charKey++; revision++; if (!ch.isWhitespace()) rt.audio.key() }))
                        steps.add(TypeStep.Pause(drawMs))
                    }
                    steps.add(TypeStep.Reveal({ activeIdx = idx; activeLen = ln.length; revision++ }))
                    pl.annos.forEachIndexed { ai, anno ->
                        val dur = annoDurMs(anno.type)
                        steps.add(TypeStep.Reveal({ drawingLine = idx; drawingAnnoIdx = ai; annoDur = dur; annoKey++; revision++; rt.audio.key() }, delay = 120))
                        steps.add(TypeStep.Pause(dur + 90))
                        steps.add(TypeStep.Reveal({ doneAnnos.add("$idx:$ai"); drawingLine = -1; revision++ }))
                    }
                    steps.add(TypeStep.Reveal({ doneLines.add(idx); activeIdx = -1; revision++ }))
                    steps.add(TypeStep.Pause(150))
                }
            }
            steps.add(TypeStep.Reveal({ activeIdx = -1; revision++ }))
            steps.add(TypeStep.Pause(900))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null; rt.audio.writing(false) } }

        val showDate = if (preview) s.date.isNotBlank() else dateShown

        // draw the current letter on with a ballistic stroke — slow at the letter
        // boundaries, quick through the middle (real handwriting velocity profile).
        val charDraw = remember { Animatable(1f) }
        LaunchedEffect(charKey) {
            if (!preview && activeLen > 0) {
                val ms = (DRAW_MS / s.typeSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(16)
                // constant-velocity draw so each letter finishes exactly as the next starts —
                // no slow-end that snaps to full (which read as a per-letter stutter).
                charDraw.snapTo(0f); charDraw.animateTo(1f, tween(ms, easing = LinearEasing))
            }
        }

        // draw the current mark on (a continuous pen stroke)
        val annoProgress = remember { Animatable(0f) }
        LaunchedEffect(annoKey) {
            if (!preview && drawingLine >= 0) { annoProgress.snapTo(0f); annoProgress.animateTo(1f, tween(annoDur, easing = EaseInOut)) }
        }

        val scroll = rememberScrollState()
        // no spring scroll (that read as the page "stretching/shaking"); the page stays put
        // and only jumps instantly if the writing overflows it.
        LaunchedEffect(scroll.maxValue) { if (s.scatter == 0f && scroll.maxValue > 0) scroll.scrollTo(scroll.maxValue) }
        LaunchedEffect(rt.playing) { if (!rt.playing) rt.audio.writing(false) }

        val ink = Color(s.ink)

        // The notes app fills the whole screen — a clean blank canvas (like writing in a
        // tablet notes app), not a paper card floating on a desk.
        Box(Modifier.fillMaxSize().background(Color(s.paper))) {
            val style = TextStyle(fontSize = fontPx.sp, lineHeight = lineH.sp, fontFamily = s.font.family)
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 96.dp, end = 72.dp, top = TOP_PAD.dp, bottom = 120.dp)) {
                    lines.forEachIndexed { i, ln ->
                        val showLen = when {
                            preview || i in doneLines -> ln.length
                            i == activeIdx -> activeLen
                            else -> -1
                        }
                        if (showLen >= 0) {
                            val annos = plines[i].annos
                            val progs = annos.indices.map { ai ->
                                when {
                                    preview || "$i:$ai" in doneAnnos -> 1f
                                    i == drawingLine && ai == drawingAnnoIdx -> annoProgress.value
                                    else -> 0f
                                }
                            }
                            // scatter: shove the line somewhere random on the page, tilt it, and
                            // occasionally flip it upside down — like notes jotted all over.
                            val sc = s.scatter
                            val sx = if (sc > 0f) (jitter(7, i, 21) - 0.5f) * sc * 230f else 0f
                            val sy = if (sc > 0f) (jitter(7, i, 22) - 0.5f) * sc * (lineH * 6f) else 0f
                            val invert = sc > 0f && jitter(7, i, 24) < sc * 0.22f
                            val rot = if (sc > 0f) (jitter(7, i, 23) - 0.5f) * sc * 34f + (if (invert) 180f else 0f) else 0f
                            Box(Modifier.offset(x = sx.dp, y = sy.dp).rotate(rot)) {
                                HandLine(ln, showLen, if (i == activeIdx) charDraw.value else 1f, ink, style, lineH, i, s.messiness, annos, progs, fontPx)
                            }
                        }
                    }
                }
                if (showDate) {
                    Text(
                        s.date, color = Color(0x99535862), fontSize = (58f * fs).sp, fontFamily = s.font.family,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 70.dp).rotate(-1.5f),
                    )
                }
            // hidden measuring pass — real layout pipeline gives the correct resolved-font
            // width (and re-fires when the bundled hand loads), driving the auto-fit above.
            Box(Modifier.alpha(0f)) {
                fitSrc.forEach { ln ->
                    Text(
                        ln.ifEmpty { " " }, fontSize = (70f * fs).sp, fontFamily = s.font.family,
                        softWrap = false, maxLines = 1, modifier = Modifier.wrapContentWidth(unbounded = true),
                        onTextLayout = { r -> val w = r.size.width.toFloat(); if (w > measuredMaxW) measuredMaxW = w },
                    )
                }
            }
        }
    }
}

/** A ruled line written in a human hand. The whole line is ONE Text — so every letter
 *  shares the font's baseline (no per-glyph box that would lift descenders and look jittery)
 *  — and it's revealed left-to-right with a clip, the newest letter dragged on by [penFrac].
 *  The hand look is the font itself; the line is dead steady. */
@Composable
private fun HandLine(
    text: String, revealLen: Int, penFrac: Float, ink: Color, style: TextStyle, lineH: Float,
    seed: Int, messiness: Float, annos: List<Anno> = emptyList(), annoProgs: List<Float> = emptyList(), fontPx: Float = 60f,
) {
    if (text.isEmpty() || revealLen <= 0) { Box(Modifier.height(lineH.dp)); return }
    var layout by remember(text, style) { mutableStateOf<TextLayoutResult?>(null) }
    Box(Modifier.fillMaxWidth().height(lineH.dp), contentAlignment = Alignment.BottomStart) {
        // invisible full line gives exact glyph positions (and reserves the height)
        Text(text, color = Color.Transparent, style = style, softWrap = false, maxLines = 1, onTextLayout = { layout = it })
        // hand-drawn marks over their spans
        if (annos.isNotEmpty()) {
            val lr0 = layout
            Canvas(Modifier.matchParentSize()) {
                val lr = lr0 ?: return@Canvas
                annos.forEachIndexed { ai, anno ->
                    val prog = annoProgs.getOrElse(ai) { 0f }
                    if (prog <= 0f) return@forEachIndexed
                    val x0: Float; val x1: Float
                    if (anno.type == AnnoType.HR) { x0 = 0f; x1 = size.width } else {
                        x0 = lr.getHorizontalPosition(anno.start.coerceIn(0, text.length), usePrimaryDirection = true)
                        x1 = lr.getHorizontalPosition(anno.end.coerceIn(0, text.length), usePrimaryDirection = true)
                    }
                    drawAnno(anno.type, x0, x1, size.height, fontPx, prog, ink, seed * 31 + ai * 7 + anno.start)
                }
            }
        }
        val lr = layout ?: return@Box
        val n = revealLen.coerceAtMost(text.length)
        // reveal up to the current letter; drag the last one on by penFrac of its width
        val xStart = lr.getHorizontalPosition((n - 1).coerceIn(0, text.length), usePrimaryDirection = true)
        val xEnd = lr.getHorizontalPosition(n.coerceAtMost(text.length), usePrimaryDirection = true)
        val revealX = (xStart + (xEnd - xStart) * penFrac).coerceAtLeast(0.5f)
        Text(
            text, color = ink, style = style, softWrap = false, maxLines = 1,
            modifier = Modifier.drawWithContent { clipRect(right = revealX) { this@drawWithContent.drawContent() } },
        )
    }
}

/** Deterministic per-glyph pseudo-random in [0,1) — stable across recompositions. */
private fun jitter(seed: Int, i: Int, salt: Int): Float {
    var h = (seed * 73856093) xor (i * 19349663) xor (salt * 83492791)
    h = h xor (h ushr 13); h *= 1274126177; h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF) / 2147483647f
}
