package com.example.recorder.sims.journal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.SimFrame
import com.example.recorder.sims.SimLogical
import com.example.recorder.sims.notes.NoteFont
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private const val DRAW_MS = 70f // time to drag one letter on

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
    override val tabLabel = "Canvas"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = JournalBuilder(ctx)

    override fun toJson() = JSONObject().apply {
        put("fill", JournalStore.fill); put("widthPct", JournalStore.widthPct.toDouble())
        put("paper", JournalStore.paper); put("backdrop", JournalStore.backdrop)
        put("defFont", JournalStore.defFont.name); put("defColor", JournalStore.defColor)
        put("typeSpeed", JournalStore.typeSpeed.toDouble()); put("keySound", JournalStore.keySound.name)
        put("els", JSONArray().apply {
            JournalStore.elements.forEach { e ->
                put(JSONObject().apply {
                    put("t", e.text); put("x", e.xPct.toDouble()); put("y", e.yPct.toDouble())
                    put("f", e.font.name); put("c", e.color); put("s", e.size.toDouble())
                    put("r", e.rotation.toDouble()); put("o", e.order)
                })
            }
        })
    }

    override fun fromJson(o: JSONObject) {
        JournalStore.fill = o.optBoolean("fill", true)
        JournalStore.widthPct = o.optDouble("widthPct", 0.78).toFloat()
        JournalStore.paper = o.optLong("paper", 0xFFFCFCFE)
        JournalStore.backdrop = o.optLong("backdrop", 0xFF0E0F12)
        JournalStore.defFont = noteFont(o.optString("defFont"), NoteFont.MARKER)
        JournalStore.defColor = o.optLong("defColor", 0xFF1E2026)
        JournalStore.typeSpeed = o.optDouble("typeSpeed", 0.85).toFloat()
        JournalStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.STYLUS)
        val a = o.optJSONArray("els")
        if (a != null) {
            JournalStore.elements.clear(); JournalStore.nextId = 1L
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                JournalStore.elements.add(
                    JElement(
                        JournalStore.nextId++, e.optString("t", "text"),
                        e.optDouble("x", 0.5).toFloat(), e.optDouble("y", 0.3).toFloat(),
                        noteFont(e.optString("f"), JournalStore.defFont), e.optLong("c", JournalStore.defColor),
                        e.optDouble("s", 1.0).toFloat(), e.optDouble("r", 0.0).toFloat(), e.optInt("o", i),
                    ),
                )
            }
        }
    }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale
        val s = JournalStore

        val doneIds = remember { mutableStateListOf<Long>() }
        var activeId by remember { mutableStateOf<Long?>(null) }
        var activeLen by remember { mutableIntStateOf(0) }
        var charKey by remember { mutableIntStateOf(0) }
        var revision by remember { mutableIntStateOf(0) }

        fun buildPlan(): List<TypeStep> {
            val steps = mutableListOf<TypeStep>()
            val drawMs = (DRAW_MS / s.typeSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(14)
            steps.add(TypeStep.Reveal({ doneIds.clear(); activeId = null; activeLen = 0; revision++; rt.audio.profile = s.keySound }))
            s.elements.sortedBy { it.order }.forEach { el ->
                steps.add(TypeStep.Reveal({ activeId = el.id; activeLen = 0; revision++ }, delay = 180))
                for (j in 1..el.text.length) {
                    val ch = el.text[j - 1]
                    steps.add(TypeStep.Reveal({ activeId = el.id; activeLen = j; charKey++; revision++; if (!ch.isWhitespace()) rt.audio.key() }))
                    steps.add(TypeStep.Pause(drawMs))
                }
                steps.add(TypeStep.Reveal({ doneIds.add(el.id); activeId = null; revision++ }))
                steps.add(TypeStep.Pause(220))
            }
            steps.add(TypeStep.Pause(800))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }

        val charDraw = remember { Animatable(1f) }
        LaunchedEffect(charKey) {
            if (!preview && activeLen > 0) {
                val ms = (DRAW_MS / s.typeSpeed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(14)
                charDraw.snapTo(0f); charDraw.animateTo(1f, tween(ms, easing = LinearEasing))
            }
        }

        // how much of an element to reveal: -1 hidden, else chars shown (last dragged by penFrac)
        fun shownOf(el: JElement): Int = when {
            preview || el.id in doneIds -> el.text.length
            el.id == activeId -> activeLen
            else -> -1
        }
        val penFrac: (JElement) -> Float = { el -> if (!preview && el.id == activeId) charDraw.value else 1f }

        if (s.fill) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color(s.paper))) {
                PlaceElements(s.elements, fs, ::shownOf, penFrac)
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color(s.backdrop)), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxWidth(s.widthPct.coerceIn(0.2f, 1f)).fillMaxHeight().background(Color(s.paper))) {
                    PlaceElements(s.elements, fs, ::shownOf, penFrac)
                }
            }
        }
    }
}

private fun noteFont(name: String, fallback: NoteFont): NoteFont =
    runCatching { NoteFont.valueOf(name) }.getOrDefault(fallback)

/** Lay every visible element on the surface, each centred at its (x,y) fraction and rotated. */
@Composable
fun BoxWithConstraintsScope.PlaceElements(
    elements: List<JElement>,
    fs: Float,
    shownOf: (JElement) -> Int,
    penFrac: (JElement) -> Float,
) {
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    elements.forEach { el ->
        val shown = shownOf(el)
        if (shown >= 0) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(((el.xPct - 0.5f) * w).roundToInt(), ((el.yPct - 0.5f) * h).roundToInt()) }
                    .rotate(el.rotation),
            ) {
                JElementText(el, shown, penFrac(el), fs)
            }
        }
    }
}

/** One element's text, revealed left-to-right up to [shown] characters (the last dragged on
 *  by [penFrac]). One Text → steady baseline; the hand look is the font. */
@Composable
fun JElementText(el: JElement, shown: Int, penFrac: Float, fs: Float) {
    val style = TextStyle(fontSize = (62f * el.size * fs).sp, fontFamily = el.font.family)
    var layout by remember(el.text, style) { mutableStateOf<TextLayoutResult?>(null) }
    Box {
        Text(el.text, color = Color.Transparent, style = style, softWrap = false, maxLines = 1, onTextLayout = { layout = it })
        val lr = layout
        val n = shown.coerceIn(0, el.text.length)
        val revealX = when {
            lr == null -> 0f
            n >= el.text.length -> lr.size.width.toFloat() + 8f
            else -> {
                val a = lr.getHorizontalPosition((n - 1).coerceAtLeast(0), usePrimaryDirection = true)
                val b = lr.getHorizontalPosition(n, usePrimaryDirection = true)
                a + (b - a) * penFrac
            }
        }
        Text(
            el.text, color = Color(el.color), style = style, softWrap = false, maxLines = 1,
            modifier = Modifier.drawWithContent { clipRect(right = revealX) { this@drawWithContent.drawContent() } },
        )
    }
}
