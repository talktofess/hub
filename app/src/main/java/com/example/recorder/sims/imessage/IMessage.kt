package com.example.recorder.sims.imessage

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.recorder.ui.rememberUriBitmap

private val SENT = Color(0xFF0A84FF)
private val RECEIVED = Color(0xFFE9E9EB)
private val HEADER_BG = Color(0xFFF6F6F6)

object IMessageSim : SimDef {
    override val id = "imessage"
    override val label = "iMessage"
    override val glyph = "💬"
    override val accent = Color(0xFF0A84FF)
    override val frame = SimFrame.PHONE
    override val logical = SimLogical(1080, 1920)
    override val ready = true
    override val defaultScript = ""

    override fun reset() = MessagesStore.reset()

    override val tabLabel = "Chat"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = MessagesBuilder(ctx)

    override fun onPickedImage(uri: String) { MessagesStore.selectedId?.let { id -> MessagesStore.update(id) { it.copy(imageUri = uri) } } }
    override fun onPickedAvatar(uri: String) { MessagesStore.avatarUri = uri }

    @Composable
    override fun Content(rt: SimRuntime) {
        val messages = MessagesStore.messages
        val preview = !rt.playing
        val fs = rt.settings.fontScale * MessagesStore.textScale

        var visible by remember { mutableIntStateOf(0) }
        var composing by remember { mutableStateOf("") }
        var showTyping by remember { mutableStateOf(false) }
        var pressedKey by remember { mutableStateOf<Char?>(null) }
        var kbEmoji by remember { mutableStateOf(false) }
        var emojiTarget by remember { mutableIntStateOf(-1) }
        val reacted = remember { mutableStateListOf<Long>() }
        var notif by remember { mutableStateOf<com.example.recorder.sims.chat.ChatNotif?>(null) }
        var notifVisible by remember { mutableStateOf(false) }
        var revision by remember { mutableIntStateOf(0) }
        val scroll = rememberScrollState()

        fun buildPlan(): List<TypeStep> {
            val s = MessagesStore
            val steps = mutableListOf<TypeStep>()
            steps.add(TypeStep.Reveal({
                visible = 0; composing = ""; showTyping = false; pressedKey = null; kbEmoji = false; emojiTarget = -1; reacted.clear(); notif = null; notifVisible = false; revision++
                rt.audio.profile = s.keySound
                rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.55f, 0.7f, 0f, 1, emptyMap()))
            }))
            // open on the focused input — cursor blinking in the field, keyboard up — before typing
            steps.add(TypeStep.Pause(700))
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
                // react: physically open the emoji board, scroll to the emoji, then set it
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
                // a notification from another chat slides in after this message
                s.notifs.filter { it.after == i }.forEach { n ->
                    steps.add(TypeStep.Reveal({ notif = n; notifVisible = true; revision++; if (n.sound != "off") rt.audio.cue(n.sound) }, delay = 220))
                    steps.add(TypeStep.Pause(2600))
                    steps.add(TypeStep.Reveal({ notifVisible = false; revision++ }))
                    steps.add(TypeStep.Pause(420))
                }
            }
            return steps
        }

        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }
        LaunchedEffect(revision) { scroll.scrollTo(scroll.maxValue) }
        val caretOn = run {
            val t = rememberInfiniteTransition(label = "caret")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
        }

      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            if (MessagesStore.statusBar) StatusBar(MessagesStore.clock, onDark = false)
            HeaderBar(MessagesStore.contactName, MessagesStore.avatarUri, fs)
            Column(
                Modifier.fillMaxSize().weight(1f).verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DateChip(MessagesStore.dateLabel, bg = Color(0x14000000), fg = Color(0xFF8A8A8E))
                messages.forEachIndexed { i, m ->
                    if (preview || i < visible) Bubble(m, fs, reactionShown = preview || reacted.contains(m.id))
                }
                if (!preview && showTyping) TypingBubble()
                val lastV = if (preview) messages.lastOrNull() else messages.getOrNull(visible - 1)
                if (MessagesStore.readReceipt && lastV?.fromMe == true && !(!preview && showTyping)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("Read", color = Color(0xFF8A8A8E), fontSize = (22f * fs).sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            InputBar(composing, fs, caret = !preview && caretOn)
            if (MessagesStore.showKeyboard) {
                PhoneKeyboard(
                    pressed = if (preview) null else pressedKey,
                    emojiMode = if (preview) false else kbEmoji,
                    emojiTarget = if (preview) -1 else emojiTarget,
                    dark = false, accent = SENT,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !preview && notifVisible,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            enter = androidx.compose.animation.slideInVertically { -it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { -it } + androidx.compose.animation.fadeOut(),
        ) {
            notif?.let { com.example.recorder.sims.chat.NotificationBanner(it.sender, it.text, ios = true, fs = fs) }
        }
      }
    }
}

@Composable
private fun HeaderBar(name: String, avatarUri: String?, fs: Float) {
    Column(Modifier.fillMaxWidth().background(HEADER_BG)) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = SENT, fontSize = (64f * fs).sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val avatar = rememberUriBitmap(avatarUri)
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0xFFB8BCC4)), contentAlignment = Alignment.Center) {
                    if (avatar != null) {
                        Image(avatar, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(name.take(1).uppercase(), color = Color.White, fontSize = (44f * fs).sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(name, color = Color.Black, fontSize = (30f * fs).sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(64.dp))
        }
    }
}

@Composable
private fun Bubble(m: Message, fs: Float, reactionShown: Boolean) {
    val fromMe = m.fromMe
    val bubbleColor = Color(if (fromMe) MessagesStore.sentColor else MessagesStore.receivedColor)
    val textColor = Color(if (fromMe) MessagesStore.sentTextColor else MessagesStore.receivedTextColor)
    val font = MessagesStore.font.family
    val showReaction = m.reaction != null && reactionShown
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start) {
        Box {
            Column(
                Modifier.padding(top = if (showReaction) 22.dp else 0.dp),
                horizontalAlignment = if (fromMe) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                m.imageUri?.let { uri ->
                    rememberUriBitmap(uri)?.let { bmp ->
                        Image(
                            bmp, contentDescription = null,
                            modifier = Modifier.widthIn(max = 560.dp).heightIn(max = 760.dp).clip(RoundedCornerShape(28.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                if (m.text.isNotEmpty()) {
                    if (isEmojiOnly(m.text)) {
                        Text(m.text, fontSize = (96f * fs).sp)
                    } else {
                        Box(
                            Modifier.widthIn(max = 740.dp).clip(RoundedCornerShape(34.dp)).background(bubbleColor)
                                .padding(horizontal = 34.dp, vertical = 22.dp),
                        ) {
                            Text(m.text, color = textColor, fontSize = (38f * fs).sp, lineHeight = (48f * fs).sp, fontFamily = font)
                        }
                    }
                }
            }
            if (showReaction) {
                Box(Modifier.align(if (fromMe) Alignment.TopStart else Alignment.TopEnd)) {
                    ReactionBadge(m.reaction!!, bg = Color(0xFFF2F2F7))
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier.clip(RoundedCornerShape(34.dp)).background(RECEIVED).padding(horizontal = 30.dp, vertical = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (k in 0..2) {
                val a = t.animateFloat(
                    0.3f, 0.3f,
                    infiniteRepeatable(keyframes { durationMillis = 1000; 0.3f at 0; 1f at 200 + k * 150; 0.3f at 600 + k * 150 }, RepeatMode.Restart),
                    label = "d$k",
                ).value
                Box(Modifier.size(20.dp).clip(CircleShape).graphicsLayer { alpha = a }.background(Color(0xFF8E8E93)))
            }
        }
    }
}

@Composable
private fun InputBar(composing: String, fs: Float, caret: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().background(HEADER_BG).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(40.dp))
                .background(Color.White)
                .padding(horizontal = 28.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    composing.ifEmpty { if (caret) "" else "iMessage" },
                    color = if (composing.isEmpty()) Color(0xFFB0B0B5) else Color.Black,
                    fontSize = (36f * fs).sp,
                    fontFamily = FontFamily.SansSerif,
                )
                if (caret) Box(Modifier.padding(start = 2.dp).width((4f * fs).dp).height((42f * fs).dp).background(SENT))
            }
        }
        Box(Modifier.size(72.dp).clip(CircleShape).background(if (composing.isNotEmpty()) SENT else Color(0xFFD8D8DD)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "send", tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}
