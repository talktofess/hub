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
        put("cards", JSONArray().apply { TyperStore.cards.forEach { put(it) } })
    }

    override fun fromJson(o: JSONObject) {
        TyperStore.textScale = o.optDouble("textScale", 1.0).toFloat(); TyperStore.color = o.optLong("color", 0xFFEAEAEA)
        TyperStore.bg = o.optLong("bg", 0xFF0C0C0C); TyperStore.prompt = o.optString("prompt", TyperStore.prompt)
        TyperStore.typeSpeed = o.optDouble("typeSpeed", 1.0).toFloat(); TyperStore.pacing = o.optDouble("pacing", 0.4).toFloat()
        TyperStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.KEYBOARD)
        TyperStore.holdMs = o.optInt("holdMs", 850)
        o.optJSONArray("cards")?.let { a -> TyperStore.setAll((0 until a.length()).map { a.getString(it) }) }
    }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale * TyperStore.textScale
        val done = remember { mutableStateListOf<String>() } // commands already entered
        var current by remember { mutableStateOf("") }        // command being typed now
        var fullCmd by remember { mutableStateOf("") }        // its full text (reserves the line)
        var revision by remember { mutableIntStateOf(0) }
        val scroll = rememberScrollState()

        fun buildPlan(): List<TypeStep> {
            val s = TyperStore
            val steps = mutableListOf<TypeStep>()
            fun bn(len: Int) = rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.45f, 0.6f, 0f, len.coerceAtLeast(1), emptyMap()))
            steps.add(TypeStep.Reveal({ done.clear(); current = ""; fullCmd = ""; revision++; rt.audio.profile = s.keySound; bn(1) }))
            s.cards.forEachIndexed { i, cmd ->
                steps.add(TypeStep.Reveal({ current = ""; fullCmd = cmd; bn(cmd.length); revision++ }, delay = if (i > 0) 220 else 0))
                steps.add(TypeStep.Type(cmd, { current = it; revision++ }))
                steps.add(TypeStep.Pause(s.holdMs))
                steps.add(TypeStep.Reveal({ done.add(cmd); current = ""; fullCmd = ""; revision++ })) // press Enter
            }
            steps.add(TypeStep.Pause(700))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }
        // scroll only when a command is entered (not per keystroke), instantly
        LaunchedEffect(done.size) { scroll.scrollTo(scroll.maxValue) }

        val caretOn = run {
            val t = rememberInfiniteTransition(label = "caret")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
        }
        val ink = Color(TyperStore.color)
        val promptCol = Color(0xFFD9D9D9)
        val fontPx = 40f * fs
        val style = androidx.compose.ui.text.TextStyle(
            fontSize = fontPx.sp, lineHeight = (fontPx * 1.45f).sp, fontFamily = FontFamily.Monospace,
        )
        val history = if (preview) TyperStore.cards.toList() else done.toList()

        Column(
            Modifier.fillMaxSize().background(Color(TyperStore.bg))
                .verticalScroll(scroll).padding(start = 44.dp, end = 36.dp, top = 56.dp, bottom = 120.dp),
        ) {
            history.forEach { cmd -> PromptLine(TyperStore.prompt, cmd, cmd, false, ink, promptCol, style) }
            if (!preview) PromptLine(TyperStore.prompt, fullCmd, current, caretOn, ink, promptCol, style)
        }
    }
}

/** One terminal line: prompt (dim) + command, left-aligned monospace. The FULL command
 *  is laid out invisibly to reserve the wrapped height, and the typed text is revealed
 *  on top — so a wrapping line never reflows/jumps as it types. */
@Composable
private fun PromptLine(prompt: String, full: String, typed: String, caret: Boolean, ink: Color, promptCol: Color, style: TextStyle) {
    Box(Modifier.padding(bottom = 2.dp)) {
        Text(
            buildAnnotatedString { withStyle(SpanStyle(color = Color.Transparent)) { append(prompt + full) } },
            style = style,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = promptCol)) { append(prompt) }
                withStyle(SpanStyle(color = ink)) { append(typed + if (caret) "▏" else "") }
            },
            style = style,
        )
    }
}
