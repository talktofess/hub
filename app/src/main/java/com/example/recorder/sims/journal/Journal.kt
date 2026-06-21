package com.example.recorder.sims.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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

private const val LINE_H = 96f       // ruling spacing at fs = 1
private const val TOP_PAD = 120f     // where the first written line sits
private const val MARGIN_X = 110f    // red margin rule, from the page's left edge
private const val TEXT_INDENT = 134f // text starts just past the margin
private const val TEXT_END = 38f     // right padding inside the page
private const val PAGE_PAD = 56f     // dark border around the page
private const val USABLE_W = 1080f - 2 * PAGE_PAD - TEXT_INDENT - TEXT_END

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
        JournalStore.typeSpeed = o.optDouble("typeSpeed", 0.8).toFloat()
        JournalStore.pacing = o.optDouble("pacing", 0.6).toFloat()
        JournalStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.SOFT)
    }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale * JournalStore.textScale
        val s = JournalStore
        val lineH = LINE_H * fs
        // auto-fit the hand so the widest line fits the page (this hand is wide; a
        // fixed size would clip). Based on the full text so it's stable while writing.
        val measurer = rememberTextMeasurer()
        val longest = remember(s.text, s.font) { settledText(s.text).split("\n").maxByOrNull { it.length }?.ifEmpty { " " } ?: " " }
        val fontPx = run {
            val base = 70f * fs
            val w = measurer.measure(longest, TextStyle(fontSize = base.sp, fontFamily = s.font.family)).size.width.toFloat()
            if (w > USABLE_W) base * (USABLE_W / w) else base
        }

        // each line is written out letter by letter in the chosen hand (no caret).
        val typed = remember { mutableStateListOf<String>() }
        var dateShown by remember { mutableStateOf(false) }
        var revision by remember { mutableIntStateOf(0) }

        fun buildPlan(): List<TypeStep> {
            val steps = mutableListOf<TypeStep>()
            fun bn(len: Int) = rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.5f, 0.7f, 0f, len.coerceAtLeast(1), emptyMap()))
            steps.add(TypeStep.Reveal({ typed.clear(); dateShown = false; revision++; rt.audio.profile = s.keySound; bn(1) }))
            if (s.date.isNotBlank()) steps.add(TypeStep.Reveal({ dateShown = true; revision++ }, delay = 380))
            val src = s.text.split("\n")
            src.forEachIndexed { idx, ln ->
                steps.add(TypeStep.Reveal({ typed.add(""); revision++ }))
                if (ln.isNotEmpty()) {
                    steps.add(TypeStep.Reveal({ bn(ln.length) }))
                    steps.add(TypeStep.Type(ln, { typed[idx] = it; revision++ }))
                }
                steps.add(TypeStep.Pause(if (ln.isEmpty()) 110 else 210))
            }
            steps.add(TypeStep.Pause(900))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }

        val lines = if (preview) remember(s.text) { settledText(s.text).split("\n") } else typed.toList()
        val showDate = if (preview) s.date.isNotBlank() else dateShown

        val scroll = rememberScrollState()
        LaunchedEffect(revision) { scroll.scrollTo(scroll.maxValue) }

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
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = TEXT_INDENT.dp, end = TEXT_END.dp, top = TOP_PAD.dp, bottom = 120.dp)) {
                    lines.forEach { ln ->
                        Text(
                            ln.ifEmpty { " " }, color = ink, fontSize = fontPx.sp, lineHeight = lineH.sp,
                            fontFamily = s.font.family, softWrap = false, maxLines = 1, overflow = TextOverflow.Clip,
                        )
                    }
                }
                if (showDate) {
                    Text(
                        s.date, color = Color(0xBF4A4036), fontSize = (58f * fs).sp, fontFamily = s.font.family,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 70.dp).rotate(-1.5f),
                    )
                }
            }
        }
    }
}
