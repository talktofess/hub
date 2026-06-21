package com.example.recorder.sims.typer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.NoteTiming
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.SimFrame
import com.example.recorder.sims.SimLogical
import org.json.JSONArray
import org.json.JSONObject

object TyperSim : SimDef {
    override val id = "typer"
    override val label = "Typer"
    override val glyph = "✏️"
    override val accent = Color(0xFF8A94A6)
    override val frame = SimFrame.PHONE
    override val logical = SimLogical(1080, 1920)
    override val ready = true
    override val defaultScript = ""

    override fun reset() = TyperStore.reset()
    override val tabLabel = "Typer"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = TyperBuilder(ctx)

    override fun toJson() = JSONObject().apply {
        put("textScale", TyperStore.textScale.toDouble()); put("color", TyperStore.color); put("bg", TyperStore.bg)
        put("prompt", TyperStore.prompt)
        put("typeSpeed", TyperStore.typeSpeed.toDouble()); put("pacing", TyperStore.pacing.toDouble())
        put("keySound", TyperStore.keySound.name); put("holdMs", TyperStore.holdMs)
        put("cards", JSONArray().apply { TyperStore.cards.forEach { put(JSONObject().apply { put("c", it.command); put("o", it.output) }) } })
    }

    override fun fromJson(o: JSONObject) {
        TyperStore.textScale = o.optDouble("textScale", 1.0).toFloat(); TyperStore.color = o.optLong("color", 0xFFEAEAEA)
        TyperStore.bg = o.optLong("bg", 0xFF0C0C0C); TyperStore.prompt = o.optString("prompt", TyperStore.prompt)
        TyperStore.typeSpeed = o.optDouble("typeSpeed", 1.0).toFloat(); TyperStore.pacing = o.optDouble("pacing", 0.4).toFloat()
        TyperStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.KEYBOARD)
        TyperStore.holdMs = o.optInt("holdMs", 650)
        o.optJSONArray("cards")?.let { a ->
            TyperStore.setAll(
                (0 until a.length()).map { idx ->
                    when (val el = a.get(idx)) {
                        is JSONObject -> TermCmd(el.optString("c"), el.optString("o"))
                        else -> TermCmd(el.toString(), "")
                    }
                },
            )
        }
    }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale * TyperStore.textScale
        val done = remember { mutableStateListOf<TermCmd>() } // entries already run
        var curCmd by remember { mutableStateOf("") }          // command being typed now
        var curFull by remember { mutableStateOf("") }         // its full text (reserves the line)
        var curOut by remember { mutableStateOf("") }          // its result, printing now
        var showActive by remember { mutableStateOf(false) }   // is there a live prompt line?
        var revision by remember { mutableIntStateOf(0) }
        val scroll = rememberScrollState()

        fun buildPlan(): List<TypeStep> {
            val s = TyperStore
            val steps = mutableListOf<TypeStep>()
            val speed = s.typeSpeed.coerceAtLeast(0.1f)
            steps.add(TypeStep.Reveal({ done.clear(); curCmd = ""; curOut = ""; showActive = false; revision++; rt.audio.profile = s.keySound }))
            s.cards.forEachIndexed { i, card ->
                steps.add(TypeStep.Reveal({ curCmd = ""; curFull = card.command; curOut = ""; showActive = true; revision++ }, delay = if (i > 0) 200 else 0))
                // type the command character-by-character
                steps.add(TypeStep.Reveal({ rt.beginNote(NoteTiming(speed, s.pacing, 0.45f, 0.6f, 0f, card.command.length.coerceAtLeast(1), emptyMap())) }))
                steps.add(TypeStep.Type(card.command, { curCmd = it; revision++ }))
                steps.add(TypeStep.Pause(s.holdMs))
                // print the result; whole lines (no reflow). A line "@bar Label" animates a
                // progress bar filling 0→100%, like a build/transfer.
                if (card.output.isNotEmpty()) {
                    val acc = mutableListOf<String>()
                    for (raw in card.output.split("\n")) {
                        if (raw.startsWith("@bar")) {
                            for (step in 0..16) {
                                val pct = step * 100 / 16
                                steps.add(TypeStep.Reveal({ curOut = (acc + progressBar(pct)).joinToString("\n"); revision++; if (pct < 100 && step % 3 == 0) rt.audio.key() }))
                                steps.add(TypeStep.Pause(if (pct >= 100) 150 else 55))
                            }
                            steps.add(TypeStep.Reveal({ acc.add(progressBar(100)); curOut = acc.joinToString("\n"); revision++ }))
                        } else {
                            steps.add(TypeStep.Reveal({ acc.add(raw); curOut = acc.joinToString("\n"); revision++ }))
                            steps.add(TypeStep.Pause(55))
                        }
                    }
                    steps.add(TypeStep.Pause(s.holdMs))
                }
                steps.add(TypeStep.Reveal({ done.add(card); curCmd = ""; curFull = ""; curOut = ""; showActive = false; revision++ }))
            }
            steps.add(TypeStep.Reveal({ showActive = true; curCmd = ""; curFull = ""; revision++ })) // final blinking prompt
            steps.add(TypeStep.Pause(900))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }
        LaunchedEffect(revision) { scroll.scrollTo(scroll.maxValue) }

        val caretOn = run {
            val t = rememberInfiniteTransition(label = "caret")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
        }
        val ink = Color(TyperStore.color)
        val promptCol = Color(0xFFD9D9D9)
        val outCol = Color(TyperStore.color).copy(alpha = 0.74f) // printed result, a touch dimmer
        val fontPx = 28f * fs
        val style = TextStyle(fontSize = fontPx.sp, lineHeight = (fontPx * 1.5f).sp, fontFamily = FontFamily.Monospace)

        Column(
            Modifier.fillMaxSize().background(Color(TyperStore.bg))
                .verticalScroll(scroll).padding(start = 40.dp, end = 32.dp, top = 52.dp, bottom = 120.dp),
        ) {
            if (preview) {
                TyperStore.cards.forEach { e -> CmdEntry(TyperStore.prompt, e, ink, promptCol, outCol, style) }
                PromptLine(TyperStore.prompt, "", ink, promptCol, style, caret = ink)
            } else {
                done.forEach { e -> CmdEntry(TyperStore.prompt, e, ink, promptCol, outCol, style) }
                if (showActive) {
                    ActiveLine(TyperStore.prompt, curCmd, curFull, if (caretOn && curOut.isEmpty()) ink else Color.Transparent, ink, promptCol, style)
                    if (curOut.isNotEmpty()) OutputText(curOut, outCol, style)
                }
            }
        }
    }
}

/** A completed entry: the typed command, then its printed result below it. */
@Composable
private fun CmdEntry(prompt: String, e: TermCmd, ink: Color, promptCol: Color, outCol: Color, style: TextStyle) {
    PromptLine(prompt, e.command, ink, promptCol, style)
    if (e.output.isNotEmpty()) OutputText(e.output, outCol, style)
}

/** Printed command result — plain monospace, no prompt, slightly dimmer. */
@Composable
private fun OutputText(text: String, col: Color, style: TextStyle) {
    Text(text, color = col, style = style, modifier = Modifier.padding(bottom = 8.dp))
}

/** A geeky progress/transfer bar: filled blocks + percent + a (fake) transfer rate.
 *  Fixed width, so it animates in place without ever reflowing. */
private fun progressBar(pct: Int): String {
    val w = 14
    val filled = (pct * w / 100).coerceIn(0, w)
    val bar = "█".repeat(filled) + "░".repeat(w - filled)
    val rate = if (pct >= 100) "done" else "${"%.1f".format(0.9 + (pct % 6) * 0.4)}M/s"
    return "$bar ${pct.toString().padStart(3)}%  $rate"
}

/** The live prompt line being typed. The FULL command is laid out invisibly to fix where
 *  it wraps, and the typed text is broken at those same points — so the line reserves its
 *  height and never reflows or "dances" as it types or as the caret blinks. */
@Composable
private fun ActiveLine(prompt: String, typed: String, full: String, caretColor: Color, ink: Color, promptCol: Color, style: TextStyle) {
    var breaks by remember(prompt, full, style) { mutableStateOf<List<Int>>(emptyList()) }
    Box(Modifier.padding(bottom = 2.dp)) {
        Text(
            prompt + full, color = Color.Transparent, style = style,
            onTextLayout = { lr -> breaks = (1 until lr.lineCount).map { lr.getLineStart(it) } },
        )
        val s = prompt + typed
        val sb = StringBuilder()
        var prev = 0
        for (b in breaks) {
            if (b in (prev + 1)..s.length) { sb.append(s, prev, b); sb.append('\n'); prev = b }
        }
        sb.append(s, prev.coerceAtMost(s.length), s.length)
        val pLen = prompt.length.coerceAtMost(sb.length)
        val cmdEnd = sb.length
        Text(
            buildAnnotatedString {
                append(sb)
                withStyle(SpanStyle(color = caretColor)) { append("▏") }
                addStyle(SpanStyle(color = promptCol), 0, pLen)
                addStyle(SpanStyle(color = ink), pLen, cmdEnd)
            },
            style = style,
        )
    }
}

/** One terminal prompt line: prompt (dim) then the command, left-aligned monospace. The
 *  caret is always part of the layout (its cell is reserved) and only its colour blinks, so
 *  the line never reflows/"dances" as the cursor toggles on and off. */
@Composable
private fun PromptLine(prompt: String, cmd: String, ink: Color, promptCol: Color, style: TextStyle, caret: Color? = null) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = promptCol)) { append(prompt) }
            withStyle(SpanStyle(color = ink)) { append(cmd) }
            if (caret != null) withStyle(SpanStyle(color = caret)) { append("▏") }
        },
        style = style, modifier = Modifier.padding(bottom = 2.dp),
    )
}
