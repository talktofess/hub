package com.example.recorder.sims.notes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.recorder.engine.NoteTiming
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.engine.WordAct
import com.example.recorder.engine.fumbleWord
import com.example.recorder.engine.settledText
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.SimFrame
import com.example.recorder.sims.SimLogical
import com.example.recorder.ui.rememberUriBitmap

enum class NoteShape { STICKY, NOTEBOOK, POLAROID, INDEX, POSTCARD, DOCUMENT }

private val handwriting = FontFamily.Cursive
private val sans = FontFamily.SansSerif

object NotesSim : SimDef {
    override val id = "notes"
    override val label = "Notes"
    override val glyph = "🗒️"
    override val accent = Color(0xFFF5C542)
    override val frame = SimFrame.FREE
    override val logical = SimLogical(1080, 1920)
    override val ready = true
    override val defaultScript = "" // notes are built visually now (NotesStore), not via a script

    override fun reset() = NotesStore.reset()

    override val tabLabel = "Notes"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = com.example.recorder.ui.NotesBuilderTab(ctx.rt, ctx.pickImage)

    override fun onPickedImage(uri: String) { NotesStore.selectedId?.let { id -> NotesStore.update(id) { it.copy(imageUri = uri) } } }

    @Composable
    override fun Content(rt: SimRuntime) {
        val notes = NotesStore.notes
        val preview = !rt.playing

        var visible by remember { mutableIntStateOf(0) }
        var active by remember { mutableStateOf<String?>(null) }
        var revision by remember { mutableIntStateOf(0) }
        val fields = remember { SnapshotStateMap<String, String>() }
        val scroll = rememberScrollState()

        fun buildPlan(): List<TypeStep> {
            val steps = mutableListOf<TypeStep>()
            fun set(key: String): (String) -> Unit = { v -> active = key; fields[key] = v; revision++ }
            steps.add(TypeStep.Reveal({ visible = 0; fields.clear(); active = null; revision++ }))
            notes.forEachIndexed { ni, note ->
                val s = rt.settings
                val noteText = listOf(note.header, note.body, note.footer).filter { it.isNotEmpty() }.joinToString(" ")
                val wordList = noteText.split(Regex("\\s+")).filter { it.isNotEmpty() }
                // per-word behaviors from this note's type sheet (NoteConfig.wordActions)
                val actions = HashMap<Int, WordAct>()
                for (wa in note.wordActions) {
                    if (wa.wordIndex !in wordList.indices) continue
                    val reconsider = wa.kind == WordActionKind.RECONSIDER
                    val wrong = if (reconsider) wa.wrong.ifBlank { fumbleWord(wordList[wa.wordIndex]) } else ""
                    actions[wa.wordIndex] = WordAct(reconsider, wa.dwellMs, wrong)
                }
                val timing = NoteTiming(
                    speed = note.speed ?: s.speed,
                    humanize = note.humanize ?: s.humanize,
                    thinkPauses = note.thinkPauses ?: s.thinkPauses,
                    jitter = s.jitter,
                    autoTypo = s.autoTypo,
                    noteChars = noteText.length.coerceAtLeast(1),
                    wordActions = actions,
                )
                steps.add(TypeStep.Reveal({
                    visible = ni + 1; revision++
                    // apply this note's own sound + timing (or fall back to the defaults)
                    rt.audio.profile = note.sound ?: rt.settings.sound
                    rt.beginNote(timing)
                }, delay = 380))
                if (note.header.isNotEmpty()) steps.add(TypeStep.Type(note.header, set("$ni:header")))
                if (note.body.isNotEmpty()) {
                    steps.add(TypeStep.Type(note.body, set("$ni:body")))
                    steps.add(TypeStep.Pause(140))
                }
                if (note.footer.isNotEmpty()) steps.add(TypeStep.Type(note.footer, set("$ni:footer")))
                steps.add(TypeStep.Pause(520))
            }
            steps.add(TypeStep.Reveal({ active = null }))
            return steps
        }

        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }
        // Keep the freshest text in view, but cheaply: an instant scrollTo (not a
        // per-keystroke animateScrollTo, which jammed the main thread).
        LaunchedEffect(revision, visible) { scroll.scrollTo(scroll.maxValue) }

        fun text(key: String, fallback: String): String =
            if (preview) settledText(fallback) else fields[key] ?: ""
        fun caret(key: String): Boolean = !preview && rt.playing && active == key

        val hasBg = rt.settings.bg.uri != null && rt.settings.bg.kind != com.example.recorder.model.BgKind.NONE
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (hasBg) Modifier
                    else Modifier.background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF2A2D36), Color(0xFF14151A)))),
                ),
        ) {
            val cardWidth = rt.settings.simFloat("notes", "width", 940f)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(PaddingValues(start = 20.dp, end = 20.dp, top = 120.dp, bottom = 220.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(96.dp),
            ) {
                notes.forEachIndexed { ni, note ->
                    if (preview || ni < visible) {
                        if (note.shape == NoteShape.DOCUMENT) DocumentNote(ni, note, ::text, ::caret, rt)
                        else NoteCard(ni, note, ::text, ::caret, rt, cardWidth)
                    }
                }
            }
        }
    }
}

/** Renders one note from its [NoteConfig]: color, optional image, header/body/footer. */
@Composable
fun NoteCard(
    ni: Int,
    note: NoteConfig,
    text: (String, String) -> String,
    caret: (String) -> Boolean,
    rt: SimRuntime,
    width: Float,
) {
    val w = width.dp
    val fs = note.fontScale ?: rt.settings.fontScale
    val bg = Color(note.color).copy(alpha = note.alpha)
    val font = note.font.family
    val rotation = note.rotation ?: defaultRotationFor(note.shape)
    val shape = RoundedCornerShape(if (note.sharpCorners) 0.dp else 10.dp)
    Box(
        Modifier
            .width(if (note.shape == NoteShape.POLAROID) (width * 0.82f).dp else w)
            .rotate(rotation)
            // 3D depth: a soft, lifted drop shadow under the card
            .shadow(
                elevation = 30.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .background(bg)
            .then(if (note.border) Modifier.border(3.dp, Color(note.textColor).copy(alpha = 0.55f), shape) else Modifier)
            .drawBehind { drawPaper(note.paper, Color(note.lineColor), note.marginLine) }
            .drawWithContent {
                drawContent()
                if (note.tape) drawTape()
                if (note.dogEar) drawDogEar()
                if (note.pin) drawPin()
            },
    ) {
        Column {
            if (note.shape == NoteShape.INDEX) {
                Box(Modifier.fillMaxWidth().height(26.dp).background(Color(0xFFE4626F)))
            }
            Column(
                Modifier.padding(horizontal = 56.dp, vertical = 60.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val textColor = Color(note.textColor)
                val weight = if (note.bold) FontWeight.Bold else FontWeight.Normal
                val ta = when (note.align) {
                    NoteAlign.START -> TextAlign.Start
                    NoteAlign.CENTER -> TextAlign.Center
                    NoteAlign.END -> TextAlign.End
                }
                if (note.imageUri != null) NoteImage(note)
                if (note.header.isNotEmpty() || caret("$ni:header")) {
                    TypedText(text("$ni:header", note.header), caret("$ni:header"), font, 46f * fs, FontWeight.Bold, textColor, rt, note.italic, note.underline, ta)
                }
                if (note.body.isNotEmpty() || caret("$ni:body")) {
                    TypedText(text("$ni:body", note.body), caret("$ni:body"), font, 48f * fs, weight, textColor, rt, note.italic, note.underline, ta)
                }
                if (note.footer.isNotEmpty() || caret("$ni:footer")) {
                    TypedText(text("$ni:footer", note.footer), caret("$ni:footer"), font, 42f * fs, weight, textColor.copy(alpha = 0.82f), rt, note.italic, note.underline, ta)
                }
            }
        }
    }
}

/** Translucent washi-tape strips across the top corners. */
private fun DrawScope.drawTape() {
    val tape = Color(0x4DBFD4E8)
    val tw = 170f
    val th = 56f
    rotate(-9f, pivot = Offset(tw / 2 + 30f, th)) {
        drawRect(tape, topLeft = Offset(30f, -10f), size = Size(tw, th))
    }
    rotate(9f, pivot = Offset(size.width - tw / 2 - 30f, th)) {
        drawRect(tape, topLeft = Offset(size.width - tw - 30f, -10f), size = Size(tw, th))
    }
}

/** A push-pin at the top center. */
private fun DrawScope.drawPin() {
    val cx = size.width / 2f
    val cy = 34f
    drawCircle(Color(0x33000000), 21f, Offset(cx + 3f, cy + 5f)) // shadow
    drawCircle(Color(0xFFD64550), 19f, Offset(cx, cy))           // head
    drawCircle(Color(0xFFFF99A0), 6f, Offset(cx - 5f, cy - 5f))  // highlight
}

/** A folded "dog-ear" at the bottom-right corner. */
private fun DrawScope.drawDogEar() {
    val s = 96f
    val w = size.width
    val h = size.height
    val fold = Path().apply {
        moveTo(w - s, h); lineTo(w, h - s); lineTo(w, h); close()
    }
    drawPath(fold, Color(0x33000000))
    val lift = Path().apply {
        moveTo(w - s, h); lineTo(w - s * 0.5f, h - s * 0.5f); lineTo(w, h - s); close()
    }
    drawPath(lift, Color(0x22FFFFFF))
}

/** Draw a note's surface texture (ruled lines, grid, or dot grid) behind its text. */
private fun DrawScope.drawPaper(style: PaperStyle, color: Color, marginLine: Boolean) {
    if (marginLine) {
        val mx = 96f
        drawLine(Color(0xFFE7A0A0), Offset(mx, 0f), Offset(mx, size.height), 3f)
    }
    if (style == PaperStyle.PLAIN) return
    val gap = 78f
    val stroke = 2.2f
    when (style) {
        PaperStyle.RULED -> {
            var y = gap * 1.4f
            while (y < size.height) {
                drawLine(color, Offset(36f, y), Offset(size.width - 36f, y), stroke)
                y += gap
            }
        }
        PaperStyle.GRID -> {
            var y = gap
            while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), stroke); y += gap }
            var x = gap
            while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke); x += gap }
        }
        PaperStyle.DOTS -> {
            var y = gap
            while (y < size.height) {
                var x = gap
                while (x < size.width) { drawCircle(color, 3.4f, Offset(x, y)); x += gap }
                y += gap
            }
        }
        PaperStyle.GRAPH -> {
            val g = 38f
            var y = g
            while (y < size.height) { drawLine(color.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.4f); y += g }
            var x = g
            while (x < size.width) { drawLine(color.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1.4f); x += g }
        }
        PaperStyle.CORNELL -> {
            val mx = size.width * 0.28f
            val by = size.height - 200f
            drawLine(color, Offset(mx, 0f), Offset(mx, by), 3f)        // cue column
            drawLine(color, Offset(0f, by), Offset(size.width, by), 3f) // summary line
            var y = gap * 1.4f
            while (y < by) { drawLine(color.copy(alpha = 0.6f), Offset(mx + 12f, y), Offset(size.width - 36f, y), stroke); y += gap }
        }
        PaperStyle.MUSIC -> {
            val line = 26f
            val staffGap = 90f
            var top = gap
            while (top + line * 4 < size.height) {
                for (k in 0..4) drawLine(color, Offset(30f, top + k * line), Offset(size.width - 30f, top + k * line), 2f)
                top += line * 4 + staffGap
            }
        }
        PaperStyle.ISO -> {
            val g = 80f
            val c = color.copy(alpha = 0.45f)
            val dx = size.height / 0.58f // horizontal run for a ~30° line over the full height
            var x0 = -dx
            while (x0 < size.width) { drawLine(c, Offset(x0, 0f), Offset(x0 + dx, size.height), 1.2f); x0 += g } // down-right
            var x1 = 0f
            while (x1 < size.width + dx) { drawLine(c, Offset(x1, 0f), Offset(x1 - dx, size.height), 1.2f); x1 += g } // down-left
        }
        else -> {}
    }
}

@Composable
private fun NoteImage(note: NoteConfig) {
    val bmp = rememberUriBitmap(note.imageUri) ?: return
    Image(
        bitmap = bmp,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .rotate(note.imageRotation)
            .clip(RoundedCornerShape(6.dp)),
    )
}

/** A field of typed text rendered as ONE soft-wrapping Text, with the blinking
    caret flowing INLINE so it wraps to the next line with the text. */
@Composable
private fun TypedText(
    value: String,
    showCaret: Boolean,
    font: FontFamily,
    sizeSp: Float,
    weight: FontWeight,
    color: Color,
    rt: SimRuntime,
    italic: Boolean = false,
    underline: Boolean = false,
    align: TextAlign = TextAlign.Start,
) {
    val caretId = "caret"
    val annotated = buildAnnotatedString {
        append(value)
        if (showCaret) appendInlineContent(caretId, "|")
    }
    val alpha = if (showCaret && rt.settings.caretBlink) {
        val t = rememberInfiniteTransition(label = "caret")
        t.animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "a",
        ).value
    } else 1f
    val inline = mapOf(
        caretId to InlineTextContent(
            Placeholder(
                width = (sizeSp * 0.5f).sp,
                height = (sizeSp * 1.02f).sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width((sizeSp * 0.09f).dp)
                    .background(rt.settings.caretColor.copy(alpha = alpha)),
            )
        },
    )
    Text(
        annotated,
        modifier = Modifier.fillMaxWidth(),
        inlineContent = inline,
        fontFamily = font,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 1.42f).sp,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (underline) TextDecoration.Underline else null,
        textAlign = align,
        color = color,
    )
}

@Composable
private fun Caret(rt: SimRuntime) {
    val alpha = if (rt.settings.caretBlink) {
        val t = rememberInfiniteTransition(label = "caret")
        t.animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
            label = "caretA",
        ).value
    } else 1f
    val (cw, ch, topPad) = when (rt.settings.caretStyle) {
        com.example.recorder.model.CaretStyle.BAR -> Triple(4.dp, 48.dp, 0.dp)
        com.example.recorder.model.CaretStyle.BLOCK -> Triple(26.dp, 50.dp, 0.dp)
        com.example.recorder.model.CaretStyle.UNDERLINE -> Triple(28.dp, 6.dp, 42.dp)
    }
    Box(
        Modifier
            .padding(start = 4.dp, top = topPad)
            .width(cw)
            .height(ch)
            .graphicsLayer { this.alpha = alpha }
            .background(rt.settings.caretColor),
    )
}

// ---- Document note (the "Corporate" editorial layout, blended into Notes) ----

private enum class DocTag { H2, H3, QUOTE, PARA, GAP }
private data class DocBlock(val tag: DocTag, val text: String)

/** Parse a document body into editorial blocks. Line prefixes: `# ` h2, `## ` h3, `> ` quote. */
private fun parseDocBlocks(body: String): List<DocBlock> = body.split("\n").map { line ->
    when {
        line.startsWith("## ") -> DocBlock(DocTag.H3, line.removePrefix("## "))
        line.startsWith("# ") -> DocBlock(DocTag.H2, line.removePrefix("# "))
        line.startsWith("> ") -> DocBlock(DocTag.QUOTE, line.removePrefix("> "))
        line.isBlank() -> DocBlock(DocTag.GAP, "")
        else -> DocBlock(DocTag.PARA, line)
    }
}

/** A clean editorial column that types itself out — header = title, body = article
 *  (with `#`/`##`/`>` markers), footer = byline. Folds the Corporate sim into Notes. */
@Composable
private fun DocumentNote(
    ni: Int,
    note: NoteConfig,
    text: (String, String) -> String,
    caret: (String) -> Boolean,
    rt: SimRuntime,
) {
    val fs = note.fontScale ?: rt.settings.fontScale
    val accent = rt.settings.accent
    val title = text("$ni:header", note.header)
    val bodyStr = text("$ni:body", note.body)
    val byline = text("$ni:footer", note.footer)

    val caretOn = run {
        val t = rememberInfiniteTransition(label = "doc-caret")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
    }
    fun cg(key: String) = if (caret(key) && caretOn) "▏" else ""

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Color(note.color).copy(alpha = note.alpha))
            .padding(horizontal = 64.dp, vertical = 90.dp),
    ) {
        if (title.isNotEmpty() || caret("$ni:header")) {
            Text(title + cg("$ni:header"), color = Color(0xFF1C1B19), fontSize = (92f * fs).sp, lineHeight = (98f * fs).sp, fontWeight = FontWeight.ExtraBold, fontFamily = note.font.family)
        }
        val blocks = parseDocBlocks(bodyStr)
        if (blocks.any { it.text.isNotEmpty() }) Spacer(Modifier.height(40.dp))
        blocks.forEachIndexed { i, b ->
            val c = if (i == blocks.lastIndex) cg("$ni:body") else ""
            DocBlockView(b, c, accent, fs, note.font.family)
        }
        if (byline.isNotEmpty() || caret("$ni:footer")) {
            Spacer(Modifier.height(56.dp))
            Text("— " + byline + cg("$ni:footer"), color = accent, fontSize = (34f * fs).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DocBlockView(b: DocBlock, caret: String, accent: Color, fs: Float, font: androidx.compose.ui.text.font.FontFamily) {
    when (b.tag) {
        DocTag.GAP -> Spacer(Modifier.height((24f * fs).dp))
        DocTag.H2 -> Text(b.text + caret, color = Color(0xFF1C1B19), fontSize = (56f * fs).sp, lineHeight = (64f * fs).sp, fontWeight = FontWeight.Bold, fontFamily = font, modifier = Modifier.padding(top = (40f * fs).dp, bottom = (16f * fs).dp))
        DocTag.H3 -> Text(b.text + caret, color = Color(0xFF3A3936), fontSize = (44f * fs).sp, fontWeight = FontWeight.Bold, fontFamily = font, modifier = Modifier.padding(top = (28f * fs).dp, bottom = (12f * fs).dp))
        DocTag.QUOTE -> Row(Modifier.height(IntrinsicSize.Min).padding(vertical = (36f * fs).dp)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(accent))
            Spacer(Modifier.width(34.dp))
            Text(b.text + caret, color = Color(0xFF1C1B19), fontSize = (56f * fs).sp, lineHeight = (72f * fs).sp, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontFamily = font)
        }
        DocTag.PARA -> Text(b.text + caret, color = Color(0xFF2A2926), fontSize = (44f * fs).sp, lineHeight = (68f * fs).sp, fontFamily = font, modifier = Modifier.padding(bottom = (24f * fs).dp))
    }
}
