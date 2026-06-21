package com.example.recorder.sims.typer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.NoteTiming
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.engine.settledText
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
        put("textScale", TyperStore.textScale.toDouble()); put("color", TyperStore.color)
        put("typeSpeed", TyperStore.typeSpeed.toDouble()); put("pacing", TyperStore.pacing.toDouble())
        put("keySound", TyperStore.keySound.name); put("holdMs", TyperStore.holdMs)
        put("cards", JSONArray().apply { TyperStore.cards.forEach { put(it) } })
    }

    override fun fromJson(o: JSONObject) {
        TyperStore.textScale = o.optDouble("textScale", 1.0).toFloat(); TyperStore.color = o.optLong("color", 0xFFF4F5F7)
        TyperStore.typeSpeed = o.optDouble("typeSpeed", 1.0).toFloat(); TyperStore.pacing = o.optDouble("pacing", 0.4).toFloat()
        TyperStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.SOFT)
        TyperStore.holdMs = o.optInt("holdMs", 1500)
        o.optJSONArray("cards")?.let { a -> TyperStore.setAll((0 until a.length()).map { a.getString(it) }) }
    }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale * TyperStore.textScale
        var shown by remember { mutableStateOf("") }
        var revision by remember { mutableIntStateOf(0) }

        fun buildPlan(): List<TypeStep> {
            val s = TyperStore
            val steps = mutableListOf<TypeStep>()
            fun bn(len: Int) = rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.5f, 0.6f, 0f, len.coerceAtLeast(1), emptyMap()))
            steps.add(TypeStep.Reveal({ shown = ""; revision++; rt.audio.profile = s.keySound; bn(1) }))
            s.cards.forEachIndexed { i, card ->
                if (i > 0) steps.add(TypeStep.Reveal({ shown = ""; revision++ }, delay = 260))
                steps.add(TypeStep.Reveal({ bn(card.length) }))
                steps.add(TypeStep.Type(card, { shown = it; revision++ }))
                steps.add(TypeStep.Pause(s.holdMs))
            }
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }

        val caretOn = run {
            val t = rememberInfiniteTransition(label = "caret")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
        }
        val text = if (preview) settledText(TyperStore.cards.lastOrNull() ?: "") else shown
        val caret = if (!preview && caretOn) "▏" else ""

        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFF20232C), Color(0xFF0D0E12)), center = Offset(540f, 560f), radius = 1300f),
            ).padding(horizontal = 120.dp, vertical = 140.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text + caret,
                color = Color(TyperStore.color),
                fontSize = (96f * fs).sp, lineHeight = (114f * fs).sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
        }
    }
}
