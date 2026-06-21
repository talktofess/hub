package com.example.recorder.sims.lists

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.NoteTiming
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.SimFrame
import com.example.recorder.sims.SimLogical
import com.example.recorder.ui.rememberUriBitmap

private val BG = listOf(Color(0xFF15203A), Color(0xFF0C1020), Color(0xFF08080D))

private class Live(val block: Block) {
    var title by mutableStateOf("")
    var text by mutableStateOf("")
    var leaving by mutableStateOf(false)
}

object ListsSim : SimDef {
    override val id = "lists"
    override val label = "Lists"
    override val glyph = "🏆"
    override val accent = Color(0xFF5B8DEF)
    override val frame = SimFrame.PHONE
    override val logical = SimLogical(1080, 1920)
    override val ready = true
    override val defaultScript = ""

    override fun reset() = ListsStore.reset()

    override val tabLabel = "List"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = ListsBuilder(ctx)

    override fun onPickedImage(uri: String) { ListsStore.selectedId?.let { id -> ListsStore.update(id) { it.copy(imageUri = uri) } } }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale * ListsStore.textScale
        val accent = Color(ListsStore.accent)
        val items = ListsStore.items

        var typedTitle by remember { mutableStateOf("") }
        val lives = remember { mutableStateListOf<Live>() }
        var active by remember { mutableStateOf("") }
        var revision by remember { mutableIntStateOf(0) }
        val scroll = rememberScrollState()

        fun live(id: Long) = lives.firstOrNull { it.block.id == id }

        fun buildPlan(): List<TypeStep> {
            val s = ListsStore
            val steps = mutableListOf<TypeStep>()
            fun bn(len: Int) = rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.4f, 0.5f, 0f, len.coerceAtLeast(1), emptyMap()))
            steps.add(TypeStep.Reveal({
                typedTitle = ""; lives.clear(); active = "title"; revision++
                rt.audio.profile = s.keySound; bn(1)
            }))
            if (s.heading.isNotEmpty()) {
                steps.add(TypeStep.Reveal({ bn(s.heading.length) }))
                steps.add(TypeStep.Type(s.heading, { typedTitle = it; active = "title"; revision++ }))
                steps.add(TypeStep.Pause(360))
            }
            var i = 0
            while (i < items.size) {
                val group = mutableListOf(items[i]); var j = i + 1
                while (j < items.size && items[j].start == StartMode.WITH) { group.add(items[j]); j++ }
                // enter the whole group together
                group.forEachIndexed { gi, b ->
                    steps.add(TypeStep.Reveal({ lives.add(Live(b)); revision++ }, delay = (if (gi == 0) b.startDelay else 0) + 160))
                }
                // type each block's content in order
                group.forEach { b ->
                    if (b.title.isNotEmpty()) {
                        steps.add(TypeStep.Reveal({ bn(b.title.length) }))
                        steps.add(TypeStep.Type(b.title, { live(b.id)?.title = it; active = "${b.id}:t"; revision++ }))
                    }
                    if (b.text.isNotEmpty() && (b.type == BlockType.CARD || b.type == BlockType.NOTE)) {
                        steps.add(TypeStep.Pause(140))
                        steps.add(TypeStep.Reveal({ bn(b.text.length) }))
                        steps.add(TypeStep.Type(b.text, { live(b.id)?.text = it; active = "${b.id}:x"; revision++ }))
                    }
                }
                steps.add(TypeStep.Pause(s.cardGap))
                // exits for any block that has a hold
                group.filter { it.hold > 0 }.forEach { b ->
                    steps.add(TypeStep.Pause(b.hold))
                    steps.add(TypeStep.Reveal({ live(b.id)?.leaving = true; revision++ }))
                    steps.add(TypeStep.Pause(360))
                    steps.add(TypeStep.Reveal({ lives.removeAll { it.block.id == b.id }; revision++ }))
                }
                i = j
            }
            steps.add(TypeStep.Reveal({ active = ""; revision++ }))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }
        LaunchedEffect(revision) { scroll.scrollTo(scroll.maxValue) }

        val caretOn = run {
            val t = rememberInfiniteTransition(label = "caret")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
        }
        fun caret(key: String) = if (!preview && active == key && caretOn) "▏" else ""

        val titleText = if (preview) ListsStore.heading else typedTitle

        Column(Modifier.fillMaxSize().background(Brush.linearGradient(BG))) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 80.dp, end = 80.dp, top = 110.dp, bottom = 220.dp),
                verticalArrangement = Arrangement.spacedBy(44.dp),
            ) {
                if (titleText.isNotEmpty() || active == "title") {
                    Text(titleText + caret("title"), color = Color(0xFFF4F6FB), fontSize = (84f * fs).sp, lineHeight = (90f * fs).sp, fontWeight = FontWeight.ExtraBold)
                }
                if (preview) {
                    items.forEach { b -> BlockContent(b, b.title, b.text, accent, fs) }
                } else {
                    lives.forEach { lv ->
                        BlockView(lv) {
                            BlockContent(lv.block, lv.title + caret("${lv.block.id}:t"), lv.text + caret("${lv.block.id}:x"), accent, fs)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockView(live: Live, content: @Composable () -> Unit) {
    val b = live.block
    val anim = remember(b.id) { Animatable(if (b.enter == Trans.CUT) 1f else 0f) }
    LaunchedEffect(live.leaving) {
        if (live.leaving) { if (b.exit != Trans.CUT) anim.animateTo(0f, tween(320, easing = FastOutSlowInEasing)) else anim.snapTo(0f) }
        else if (anim.value < 1f) anim.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    val trans = if (live.leaving) b.exit else b.enter
    val p = anim.value
    Box(Modifier.fillMaxWidth().graphicsLayer {
        alpha = if (trans == Trans.CUT) 1f else p
        when (trans) {
            Trans.SLIDE_UP -> translationY = (1f - p) * 90.dp.toPx()
            Trans.SLIDE_LEFT -> translationX = (1f - p) * 160.dp.toPx()
            Trans.POP -> { scaleX = 0.9f + 0.1f * p; scaleY = 0.9f + 0.1f * p }
            else -> {}
        }
    }) { content() }
}

@Composable
private fun BlockContent(b: Block, title: String, text: String, accent: Color, fs: Float) {
    when (b.type) {
        BlockType.CARD -> CardBlock(b, title, text, accent, fs, rank = true)
        BlockType.NOTE -> CardBlock(b, title, text, accent, fs, rank = false)
        BlockType.IMAGE -> MediaBlock(b, title, fs, video = false)
        BlockType.VIDEO -> MediaBlock(b, title, fs, video = true)
    }
}

@Composable
private fun CardBlock(b: Block, title: String, text: String, accent: Color, fs: Float, rank: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(32.dp)).padding(horizontal = 48.dp, vertical = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank && b.rank.isNotEmpty()) {
            Text(b.rank, color = accent, fontSize = (96f * fs).sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.widthIn(min = 120.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Text(title, color = Color(0xFFF4F6FB), fontSize = (56f * fs).sp, lineHeight = (60f * fs).sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                if (b.badge.isNotEmpty()) {
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFD23F), Color(0xFFFF9D2F)))).padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text(b.badge.uppercase(), color = Color(0xFF1A0D06), fontSize = (28f * fs).sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            if (text.isNotEmpty()) Text(text, color = Color(0xFFC4CBDB), fontSize = (40f * fs).sp, lineHeight = (56f * fs).sp, modifier = Modifier.padding(top = 14.dp))
            if (b.score.isNotEmpty() || b.tier.isNotEmpty()) {
                Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (b.tier.isNotEmpty()) {
                        Box(Modifier.size((64f * fs).dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(tierColors(b.tier))), contentAlignment = Alignment.Center) {
                            Text(b.tier.uppercase(), color = Color.White, fontSize = (34f * fs).sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    if (b.score.isNotEmpty()) Text(b.score, color = Color(0xFFAEB8CC), fontSize = (36f * fs).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MediaBlock(b: Block, caption: String, fs: Float, video: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Color(0x0DFFFFFF)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(32.dp))) {
        Box(Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 900.dp), contentAlignment = Alignment.Center) {
            val bmp = rememberUriBitmap(b.imageUri)
            if (bmp != null) {
                Image(bmp, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 900.dp).clip(RoundedCornerShape(32.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxWidth().heightIn(min = 420.dp).background(Color(0x11FFFFFF)), contentAlignment = Alignment.Center) {
                    Text(if (video) "🎞" else "🖼", fontSize = (110f * fs).sp)
                }
            }
            if (video) {
                Box(Modifier.size((130f * fs).dp).clip(RoundedCornerShape(50)).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    Text("▶", color = Color.White, fontSize = (60f * fs).sp)
                }
                if (b.duration.isNotEmpty()) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(b.duration, color = Color.White, fontSize = (28f * fs).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (caption.isNotEmpty()) {
            Text(caption, color = Color(0xFFF4F6FB), fontSize = (44f * fs).sp, lineHeight = (52f * fs).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 44.dp, vertical = 30.dp))
        }
    }
}

private fun tierColors(tier: String): List<Color> = when (tier.lowercase()) {
    "s" -> listOf(Color(0xFFFF5D8F), Color(0xFFFF2D55))
    "a" -> listOf(Color(0xFFFFB547), Color(0xFFFF8A00))
    "b" -> listOf(Color(0xFF5BD0A0), Color(0xFF2BB673))
    "c" -> listOf(Color(0xFF6AA8FF), Color(0xFF4A78E0))
    "d" -> listOf(Color(0xFF9AA0B0), Color(0xFF6A7080))
    else -> listOf(Color(0xFF5B8DEF), Color(0xFF5B8DEF))
}
