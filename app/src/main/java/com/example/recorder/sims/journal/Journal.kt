package com.example.recorder.sims.journal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
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
        put("messiness", JournalStore.messiness.toDouble())
        put("typeSpeed", JournalStore.typeSpeed.toDouble()); put("pacing", JournalStore.pacing.toDouble())
        put("keySound", JournalStore.keySound.name)
    }

    override fun fromJson(o: JSONObject) {
        JournalStore.text = o.optString("text", JournalStore.text)
        JournalStore.date = o.optString("date", JournalStore.date)
        JournalStore.font = runCatching { com.example.recorder.sims.notes.NoteFont.valueOf(o.optString("font")) }.getOrDefault(com.example.recorder.sims.notes.NoteFont.HANDWRITING)
        JournalStore.ink = o.optLong("ink", JournalStore.ink)
        JournalStore.paper = o.optLong("paper", JournalStore.paper)
        JournalStore.textScale = o.optDouble("textScale", 1.0).toFloat()
        JournalStore.messiness = o.optDouble("messiness", 0.45).toFloat()
        JournalStore.typeSpeed = o.optDouble("typeSpeed", 0.8).toFloat()
        JournalStore.pacing = o.optDouble("pacing", 0.6).toFloat()
        JournalStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.PENCIL)
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
        var doneCount by remember { mutableIntStateOf(0) }
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
                doneCount = 0; activeIdx = -1; activeLen = 0; dateShown = false; drawingLine = -1; doneAnnos.clear(); revision++
                rt.audio.profile = s.keySound
            }))
            if (s.date.isNotBlank()) steps.add(TypeStep.Reveal({ dateShown = true; revision++ }, delay = 380))
            plines.forEachIndexed { idx, pl ->
                val ln = pl.text
                if (ln.isBlank() && pl.annos.isEmpty()) {
                    steps.add(TypeStep.Reveal({ doneCount = idx + 1; revision++ }))
                    steps.add(TypeStep.Pause(120))
                } else {
                    for (j in 1..ln.length) {
                        val ch = ln[j - 1]
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
                    steps.add(TypeStep.Reveal({ doneCount = idx + 1; activeIdx = -1; revision++ }))
                    steps.add(TypeStep.Pause(150))
                }
            }
            steps.add(TypeStep.Reveal({ activeIdx = -1; revision++ }))
            steps.add(TypeStep.Pause(900))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }

        val showDate = if (preview) s.date.isNotBlank() else dateShown

        // draw the current letter on with a ballistic stroke — slow at the letter
        // boundaries, quick through the middle (real handwriting velocity profile).
        val charDraw = remember { Animatable(1f) }
        LaunchedEffect(charKey) {
            if (!preview && activeLen > 0) {
                val ms = (DRAW_MS / s.typeSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(16)
                charDraw.snapTo(0f); charDraw.animateTo(1f, tween(ms, easing = EaseInOut))
            }
        }

        // draw the current mark on (a continuous pen stroke)
        val annoProgress = remember { Animatable(0f) }
        LaunchedEffect(annoKey) {
            if (!preview && drawingLine >= 0) { annoProgress.snapTo(0f); annoProgress.animateTo(1f, tween(annoDur, easing = EaseInOut)) }
        }

        val scroll = rememberScrollState()
        LaunchedEffect(doneCount) { scroll.animateScrollTo(scroll.maxValue) }

        val ink = Color(s.ink)
        val ruleColor = Color(0x525A78A0)

        Box(Modifier.fillMaxSize().background(Color(0xFF1B1712)).padding(horizontal = PAGE_PAD.dp, vertical = 80.dp)) {
            Box(
                Modifier.fillMaxSize().shadow(36.dp, RoundedCornerShape(8.dp)).background(Color(s.paper), RoundedCornerShape(8.dp))
                    .drawBehind {
                        var y = TOP_PAD + lineH
                        while (y < size.height) {
                            drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), 2f)
                            y += lineH
                        }
                        drawLine(Color(0x8CC85A5A), Offset(MARGIN_X, 0f), Offset(MARGIN_X, size.height), 3f)
                    },
            ) {
                val style = TextStyle(fontSize = fontPx.sp, lineHeight = lineH.sp, fontFamily = s.font.family)
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = TEXT_INDENT.dp, end = TEXT_END.dp, top = TOP_PAD.dp, bottom = 120.dp)) {
                    lines.forEachIndexed { i, ln ->
                        val showLen = when {
                            preview || i < doneCount -> ln.length
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
                            HandLine(ln, showLen, if (i == activeIdx) charDraw.value else 1f, ink, style, lineH, i, s.messiness, annos, progs, fontPx)
                        }
                    }
                }
                if (showDate) {
                    Text(
                        s.date, color = Color(0xBF4A4036), fontSize = (58f * fs).sp, fontFamily = s.font.family,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 70.dp).rotate(-1.5f),
                    )
                }
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

/** A ruled line written in a human hand: each glyph sits at its real layout x but is
 *  slightly rotated, resized and inked unevenly (deterministic per glyph, so it never
 *  jitters frame to frame). The whole line is laid out once — no re-layout shake. The
 *  newest glyph (index [revealLen]-1) is dragged on by [penFrac] of its width. */
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
        for (i in 0 until n) {
            val ch = text[i]
            if (ch == ' ') continue
            val x0 = lr.getHorizontalPosition(i, usePrimaryDirection = true)
            val x1 = lr.getHorizontalPosition((i + 1).coerceAtMost(text.length), usePrimaryDirection = true)
            val cw = (x1 - x0).coerceAtLeast(1f)
            val angle = (jitter(seed, i, 1) - 0.5f) * 5f * messiness       // crooked (subtle — big = loopy)
            val sc = 1f + (jitter(seed, i, 2) - 0.5f) * 0.16f * messiness  // bigger/smaller
            val dy = (jitter(seed, i, 3) - 0.5f) * 3.5f * messiness        // baseline kept steady
            val dx = (jitter(seed, i, 5) - 0.5f) * 3f * messiness          // uneven spacing
            val a = 1f - jitter(seed, i, 4) * 0.30f * messiness            // ink weight (some lighter)
            val frac = if (i == n - 1) penFrac else 1f
            Box(
                Modifier.offset(x = (x0 + dx).dp, y = dy.dp)
                    .graphicsLayer(rotationZ = angle, scaleX = sc, scaleY = sc, transformOrigin = TransformOrigin(0.5f, 1f))
                    .drawWithContent { clipRect(right = (cw * frac).coerceAtLeast(0.5f)) { this@drawWithContent.drawContent() } },
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(ch.toString(), color = ink.copy(alpha = a.coerceIn(0.5f, 1f)), style = style, softWrap = false, maxLines = 1)
            }
        }
    }
}

/** Deterministic per-glyph pseudo-random in [0,1) — stable across recompositions. */
private fun jitter(seed: Int, i: Int, salt: Int): Float {
    var h = (seed * 73856093) xor (i * 19349663) xor (salt * 83492791)
    h = h xor (h ushr 13); h *= 1274126177; h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF) / 2147483647f
}
