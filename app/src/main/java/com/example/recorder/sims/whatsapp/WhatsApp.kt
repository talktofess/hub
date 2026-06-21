package com.example.recorder.sims.whatsapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.NoteTiming
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.SimFrame
import com.example.recorder.sims.SimLogical
import com.example.recorder.sims.chat.DateChip
import com.example.recorder.sims.chat.PhoneKeyboard
import com.example.recorder.sims.chat.REACTIONS_FULL
import com.example.recorder.sims.chat.ReactionBadge
import com.example.recorder.sims.chat.StatusBar
import com.example.recorder.sims.chat.isEmojiOnly
import com.example.recorder.sims.imessage.Message
import com.example.recorder.ui.rememberUriBitmap

private val HEADER = Color(0xFF075E54)
private val SEND = Color(0xFF00A884)
private val TICK = Color(0xFF34B7F1)

object WhatsAppSim : SimDef {
    override val id = "whatsapp"
    override val label = "WhatsApp"
    override val glyph = "🟢"
    override val accent = Color(0xFF25D366)
    override val frame = SimFrame.PHONE
    override val logical = SimLogical(1080, 1920)
    override val ready = true
    override val defaultScript = ""

    override fun reset() = WhatsAppStore.reset()

    override val tabLabel = "Chat"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = WhatsAppBuilder(ctx)

    override fun onPickedImage(uri: String) { WhatsAppStore.selectedId?.let { id -> WhatsAppStore.update(id) { it.copy(imageUri = uri) } } }
    override fun onPickedAvatar(uri: String) { WhatsAppStore.avatarUri = uri }

    @Composable
    override fun Content(rt: SimRuntime) {
        val messages = WhatsAppStore.messages
        val preview = !rt.playing
        val fs = rt.settings.fontScale * WhatsAppStore.textScale

        var visible by remember { mutableIntStateOf(0) }
        var composing by remember { mutableStateOf("") }
        var showTyping by remember { mutableStateOf(false) }
        var pressedKey by remember { mutableStateOf<Char?>(null) }
        var kbEmoji by remember { mutableStateOf(false) }
        var emojiTarget by remember { mutableIntStateOf(-1) }
        val reacted = remember { mutableStateListOf<Long>() }
        var revision by remember { mutableIntStateOf(0) }
        val scroll = rememberScrollState()

        fun buildPlan(): List<TypeStep> {
            val s = WhatsAppStore
            val steps = mutableListOf<TypeStep>()
            steps.add(TypeStep.Reveal({
                visible = 0; composing = ""; showTyping = false; pressedKey = null; kbEmoji = false; emojiTarget = -1; reacted.clear(); revision++
                rt.audio.profile = s.keySound
                rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.55f, 0.7f, 0f, 1, emptyMap()))
            }))
            messages.forEachIndexed { i, m ->
                if (m.delayMs > 0) steps.add(TypeStep.Pause(m.delayMs))
                if (m.fromMe) {
                    if (m.text.isNotEmpty()) {
                        steps.add(TypeStep.Reveal({ rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.55f, 0.7f, 0f, m.text.length.coerceAtLeast(1), emptyMap())) }))
                        steps.add(TypeStep.Type(m.text, { composing = it; pressedKey = it.lastOrNull(); revision++ }))
                    }
                    steps.add(TypeStep.Reveal({
                        visible = i + 1; composing = ""; pressedKey = null; revision++
                        if (s.sendSound != "off") rt.audio.cue(s.sendSound)
                    }, delay = 140))
                    steps.add(TypeStep.Pause(s.msgGap))
                } else {
                    steps.add(TypeStep.Reveal({ showTyping = true; revision++ }, delay = 320))
                    steps.add(TypeStep.Pause(s.typingDur))
                    steps.add(TypeStep.Reveal({
                        showTyping = false; visible = i + 1; revision++
                        if (s.receiveSound != "off") rt.audio.cue(s.receiveSound)
                    }, delay = 120))
                    steps.add(TypeStep.Pause((s.msgGap * 0.85f).toInt()))
                }
                m.reaction?.let { emoji ->
                    val idx = REACTIONS_FULL.indexOf(emoji)
                    if (s.showKeyboard && idx >= 0) {
                        steps.add(TypeStep.Reveal({ kbEmoji = true; emojiTarget = idx; revision++ }, delay = 260))
                        steps.add(TypeStep.Pause(1050))
                        steps.add(TypeStep.Reveal({ reacted.add(m.id); revision++ }, delay = 100))
                        steps.add(TypeStep.Pause(480))
                        steps.add(TypeStep.Reveal({ kbEmoji = false; emojiTarget = -1; revision++ }))
                    } else {
                        steps.add(TypeStep.Reveal({ reacted.add(m.id); revision++ }))
                    }
                }
            }
            return steps
        }

        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }
        LaunchedEffect(revision) { scroll.scrollTo(scroll.maxValue) }

        Column(Modifier.fillMaxSize().background(Color(WhatsAppStore.wallpaper))) {
            Header(WhatsAppStore.contactName, if (!preview && showTyping) "typing…" else WhatsAppStore.status, WhatsAppStore.avatarUri, fs, WhatsAppStore.statusBar, WhatsAppStore.clock)
            Column(
                Modifier.fillMaxSize().weight(1f).verticalScroll(scroll).padding(horizontal = 26.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateChip(WhatsAppStore.dateLabel, bg = Color(0xCCFFFFFF), fg = Color(0xFF54656F))
                messages.forEachIndexed { i, m ->
                    if (preview || i < visible) Bubble(m, fs, reactionShown = preview || reacted.contains(m.id))
                }
            }
            InputBar(composing, fs)
            if (WhatsAppStore.showKeyboard) {
                PhoneKeyboard(
                    pressed = if (preview) null else pressedKey,
                    emojiMode = if (preview) false else kbEmoji,
                    emojiTarget = if (preview) -1 else emojiTarget,
                    dark = false, accent = SEND,
                )
            }
        }
    }
}

@Composable
private fun Header(name: String, status: String, avatarUri: String?, fs: Float, statusBar: Boolean, clock: String) {
    Column(Modifier.fillMaxWidth().background(HEADER)) {
        if (statusBar) StatusBar(clock, onDark = true) else Spacer(Modifier.height(40.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Color.White, fontSize = (60f * fs).sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(14.dp))
            val avatar = rememberUriBitmap(avatarUri)
            Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0xFF9FC6BE)), contentAlignment = Alignment.Center) {
                if (avatar != null) Image(avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(name.take(1).uppercase(), color = Color.White, fontSize = (40f * fs).sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(name, color = Color.White, fontSize = (34f * fs).sp, fontWeight = FontWeight.SemiBold)
                Text(status, color = Color(0xFFCDE9E3), fontSize = (24f * fs).sp)
            }
        }
    }
}

@Composable
private fun Bubble(m: Message, fs: Float, reactionShown: Boolean) {
    val fromMe = m.fromMe
    val bubbleColor = Color(if (fromMe) WhatsAppStore.sentColor else WhatsAppStore.receivedColor)
    val textColor = Color(if (fromMe) WhatsAppStore.sentTextColor else WhatsAppStore.receivedTextColor)
    val font = WhatsAppStore.font.family
    val shape = if (fromMe) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val tickColor = if (WhatsAppStore.readReceipt) TICK else Color(0xFF8696A0)
    val emojiOnly = m.imageUri == null && isEmojiOnly(m.text)
    val showReaction = m.reaction != null && reactionShown
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start) {
        Box {
            if (emojiOnly) {
                Text(m.text, fontSize = (96f * fs).sp, modifier = Modifier.padding(top = if (showReaction) 22.dp else 0.dp))
            } else {
                Column(
                    Modifier.padding(top = if (showReaction) 22.dp else 0.dp)
                        .widthIn(max = 760.dp).clip(shape).background(bubbleColor).padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    m.imageUri?.let { uri ->
                        rememberUriBitmap(uri)?.let { bmp ->
                            Image(bmp, contentDescription = null, modifier = Modifier.widthIn(max = 620.dp).heightIn(max = 760.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                        }
                    }
                    if (m.text.isNotEmpty()) {
                        Text(m.text, color = textColor, fontSize = (37f * fs).sp, lineHeight = (47f * fs).sp, fontFamily = font, modifier = Modifier.align(Alignment.Start))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(WhatsAppStore.stamp, color = Color(0xFF667781), fontSize = (22f * fs).sp)
                        if (fromMe) Text("✓✓", color = tickColor, fontSize = (24f * fs).sp)
                    }
                }
            }
            if (showReaction) {
                Box(Modifier.align(if (fromMe) Alignment.TopStart else Alignment.TopEnd)) {
                    ReactionBadge(m.reaction!!, bg = Color(0xFFFFFFFF))
                }
            }
        }
    }
}

@Composable
private fun InputBar(composing: String, fs: Float) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF0F0F0)).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.weight(1f).clip(RoundedCornerShape(40.dp)).background(Color.White).padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text(
                composing.ifEmpty { "Message" },
                color = if (composing.isEmpty()) Color(0xFF9AA0A6) else Color(0xFF111B21),
                fontSize = (36f * fs).sp, fontFamily = FontFamily.SansSerif,
            )
        }
        Box(Modifier.size(82.dp).clip(CircleShape).background(SEND), contentAlignment = Alignment.Center) {
            Text(if (composing.isEmpty()) "🎤" else "➤", color = Color.White, fontSize = (38f * fs).sp)
        }
    }
}
