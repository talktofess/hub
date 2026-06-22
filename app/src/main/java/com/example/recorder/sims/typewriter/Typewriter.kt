package com.example.recorder.sims.typewriter

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.NoteTiming
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.engine.settledText
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.SimFrame
import com.example.recorder.sims.SimLogical
import org.json.JSONObject

private const val FONT_BASE = 70f      // big, bold strikes — auto-fit shrinks only if a line is too wide
private const val MARGIN_BELL_COL = 24 // ring the warning bell this many chars in (near the page edge)
// machine geometry (logical px). The paper emerges UP from the platen and grows as
// lines fill; the carriage head rides the current column and swings home on a return.
private const val PAPER_W = 1048f      // wide page — lots of horizontal room
private const val PAPER_X = 16f        // (1080 - 1048) / 2 — centred
private const val PAPER_PAD = 40f
private const val PAPER_TOPPAD = 64f
private const val PAPER_BOTMARGIN = 26f
private const val PLATEN_Y = 1200f     // where the paper meets the platen (current line bottom)

object TypewriterSim : SimDef {
    override val id = "typewriter"
    override val label = "Typewriter"
    override val glyph = "🖋️"
    override val accent = Color(0xFFE8413A)
    override val frame = SimFrame.PHONE
    override val logical = SimLogical(1080, 1920)
    override val ready = true
    override val defaultScript = ""

    override fun reset() = TypewriterStore.reset()
    override val tabLabel = "Page"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = TypewriterBuilder(ctx)

    override fun toJson() = JSONObject().apply {
        put("text", TypewriterStore.text); put("textScale", TypewriterStore.textScale.toDouble())
        put("typeSpeed", TypewriterStore.typeSpeed.toDouble()); put("pacing", TypewriterStore.pacing.toDouble())
        put("keySound", TypewriterStore.keySound.name); put("bell", TypewriterStore.bell)
        put("font", TypewriterStore.font.name); put("ink", TypewriterStore.ink); put("paper", TypewriterStore.paper)
    }

    override fun fromJson(o: JSONObject) {
        TypewriterStore.text = o.optString("text", TypewriterStore.text)
        TypewriterStore.textScale = o.optDouble("textScale", 1.0).toFloat()
        TypewriterStore.typeSpeed = o.optDouble("typeSpeed", 1.0).toFloat(); TypewriterStore.pacing = o.optDouble("pacing", 0.35).toFloat()
        TypewriterStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.TYPEWRITER)
        TypewriterStore.bell = o.optBoolean("bell", true)
        TypewriterStore.font = runCatching { com.example.recorder.sims.notes.NoteFont.valueOf(o.optString("font")) }.getOrDefault(com.example.recorder.sims.notes.NoteFont.TYPEWRITER)
        TypewriterStore.ink = o.optLong("ink", 0xFF241B12); TypewriterStore.paper = o.optLong("paper", 0xFFFCF8EE)
    }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale * TypewriterStore.textScale
        var shown by remember { mutableStateOf("") }
        var done by remember { mutableStateOf(false) }
        var revision by remember { mutableIntStateOf(0) }

        fun buildPlan(): List<TypeStep> {
            val s = TypewriterStore
            val steps = mutableListOf<TypeStep>()
            steps.add(TypeStep.Reveal({
                shown = ""; done = false; revision++
                rt.audio.profile = s.keySound
                rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.45f, 0.6f, 0f, s.text.length.coerceAtLeast(1), emptyMap()))
            }))
            var prev = ""
            steps.add(TypeStep.Type(s.text, { v ->
                if (s.bell && v.length > prev.length) {
                    val lastLen = v.substringAfterLast('\n').length
                    val prevLen = prev.substringAfterLast('\n').length
                    when {
                        v.endsWith("\n") -> rt.audio.cue("return")                              // carriage return swipe
                        lastLen == MARGIN_BELL_COL && prevLen < MARGIN_BELL_COL -> rt.audio.cue("ding") // margin warning bell
                    }
                }
                prev = v; shown = v; revision++
            }))
            steps.add(TypeStep.Pause(500))
            steps.add(TypeStep.Reveal({ done = true; revision++ })) // typing finished — hide the carriage; paper stays put
            steps.add(TypeStep.Pause(700))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }

        val caretOn = run {
            val t = rememberInfiniteTransition(label = "caret")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
        }
        val textVal = if (preview) settledText(TypewriterStore.text) else shown
        val lines = textVal.split("\n")
        val col = lines.last().length
        val numLines = lines.size
        // Auto-fit the font so the widest line of the FULL text fits the page (as big
        // as possible, never clipped). Based on the full text so it stays stable while typing.
        val measurer = rememberTextMeasurer()
        val usableW = PAPER_W - PAPER_PAD - 28f
        val baseStyle = TextStyle(fontSize = (FONT_BASE * fs).sp, fontFamily = TypewriterStore.font.family, fontWeight = FontWeight.Bold)
        val longest = settledText(TypewriterStore.text).split("\n").maxByOrNull { it.length }?.ifEmpty { " " } ?: " "
        val longestW = measurer.measure(longest, baseStyle).size.width.toFloat()
        val fitScale = if (longestW > usableW) usableW / longestW else 1f
        val fontPx = FONT_BASE * fs * fitScale
        val lineH = fontPx * 1.5f
        val textStyle = TextStyle(fontSize = fontPx.sp, fontFamily = TypewriterStore.font.family, fontWeight = FontWeight.Bold)
        // measure the real rendered width of the current line so the caret/carriage
        // land exactly at its end (monospace advance varies by font — don't guess)
        val ch = measurer.measure("M", textStyle).size.width.toFloat()
        val lastLineW = if (col == 0) 0f else measurer.measure(lines.last(), textStyle).size.width.toFloat()

        // the paper grows up from the platen as lines fill; it stays put when done
        val paperContentH = numLines * lineH + PAPER_TOPPAD + PAPER_BOTMARGIN
        val paperTop by animateDpAsState((PLATEN_Y - paperContentH).dp, tween(320, easing = FastOutSlowInEasing), label = "roll")
        val carriageX by animateDpAsState((PAPER_X + PAPER_PAD + lastLineW).dp, tween(200, easing = FastOutSlowInEasing), label = "carriage")
        val strikeY = PLATEN_Y - lineH - PAPER_BOTMARGIN // current line sits just above the platen

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF26201A), Color(0xFF120F0B))))) {
            // the sheet of paper — emerges from the platen and grows as you type
            Box(
                Modifier.offset(x = PAPER_X.dp, y = paperTop).width(PAPER_W.dp).height(paperContentH.dp)
                    .shadow(20.dp, RoundedCornerShape(5.dp))
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.verticalGradient(listOf(Color(TypewriterStore.paper), androidx.compose.ui.graphics.lerp(Color(TypewriterStore.paper), Color.Black, 0.07f))))
                    .border(1.dp, Color(0x14000000), RoundedCornerShape(5.dp)),
            ) {
                // a faint left-margin guide line, like a typist's pencil margin
                Box(Modifier.offset(x = (PAPER_PAD - 16f).dp).width(2.dp).fillMaxHeight().background(Color(0x11B0402F)))
                // soft shadow where the sheet curls over the platen at the bottom
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(46.dp).background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x1F000000)))))
                // top edge highlight
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(20.dp).background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x00FFFFFF)))))
                Column(Modifier.padding(start = PAPER_PAD.dp, end = 24.dp, top = PAPER_TOPPAD.dp)) {
                    lines.forEach { ln ->
                        Text(
                            ln.ifEmpty { " " }, color = Color(TypewriterStore.ink), fontSize = fontPx.sp, lineHeight = lineH.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            softWrap = false, maxLines = 1, overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
            // platen roller (in front, covering where the paper wraps over it) + knobs
            val knob = Brush.radialGradient(listOf(Color(0xFF6A5F54), Color(0xFF211B15)))
            Box(Modifier.offset(x = (PAPER_X - 26).dp, y = PLATEN_Y.dp).width((PAPER_W + 52).dp).height(62.dp).clip(RoundedCornerShape(31.dp)).background(Brush.verticalGradient(listOf(Color(0xFF352E26), Color(0xFF0C0907)))))
            Box(Modifier.offset(x = (PAPER_X - 26).dp, y = (PLATEN_Y + 6).dp).width((PAPER_W + 52).dp).height(8.dp).background(Color(0x33FFFFFF))) // roller highlight
            Box(Modifier.offset(x = (PAPER_X - 84).dp, y = (PLATEN_Y - 18).dp).size(94.dp).clip(CircleShape).background(knob).border(3.dp, Color(0x55000000), CircleShape))
            Box(Modifier.offset(x = (PAPER_X + PAPER_W - 10).dp, y = (PLATEN_Y - 18).dp).size(94.dp).clip(CircleShape).background(knob).border(3.dp, Color(0x55000000), CircleShape))

            // carriage head + type guide + caret — only while still typing
            if (!done) {
                // metal carriage head with a top highlight
                Box(Modifier.offset(x = carriageX - 52.dp, y = (strikeY - 78f).dp).width(104.dp).height(48.dp).clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFF8A8077), Color(0xFF2A241D)))))
                Box(Modifier.offset(x = carriageX - 44.dp, y = (strikeY - 72f).dp).width(88.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x66FFFFFF)))
                // red type-guide bar pointing at the strike
                Box(Modifier.offset(x = carriageX - 4.dp, y = (strikeY - 30f).dp).width(8.dp).height((lineH + 12f).dp).clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(listOf(Color(0xFFF24B43), Color(0xFF8E1B16)))))
                if (!preview && caretOn) {
                    // bold underscore cursor sitting on the baseline of the current cell
                    Box(Modifier.offset(x = carriageX, y = (strikeY + lineH - 16f).dp).width(ch.dp).height(11.dp).clip(RoundedCornerShape(2.dp)).background(Color(TypewriterStore.ink)))
                }
            }
        }
    }
}

