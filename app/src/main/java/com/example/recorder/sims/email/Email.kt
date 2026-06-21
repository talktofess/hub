package com.example.recorder.sims.email

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.NoteTiming
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.TypeStep
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.SimFrame
import com.example.recorder.sims.SimLogical

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("").ifEmpty { "Y" }

object EmailSim : SimDef {
    override val id = "email"
    override val label = "Email"
    override val glyph = "✉️"
    override val accent = Color(0xFF0B57D0)
    override val frame = SimFrame.DESKTOP
    override val logical = SimLogical(1920, 1080)
    override val ready = true
    override val defaultScript = ""

    override fun reset() = EmailStore.reset()

    override val tabLabel = "Email"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = EmailBuilder(ctx)

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val dark = EmailStore.dark
        val accent = Color(EmailStore.accent)
        val c = remember(dark) { palette(dark) }

        var typedTo by remember { mutableStateOf("") }
        var typedCc by remember { mutableStateOf("") }
        var typedSubject by remember { mutableStateOf("") }
        var typedBody by remember { mutableStateOf("") }
        var field by remember { mutableIntStateOf(-1) }
        var sendState by remember { mutableIntStateOf(0) } // 0 idle, 1 sending, 2 sent
        var revision by remember { mutableIntStateOf(0) }

        fun buildPlan(): List<TypeStep> {
            val steps = mutableListOf<TypeStep>()
            fun bn(len: Int) = rt.beginNote(NoteTiming(EmailStore.typeSpeed.coerceAtLeast(0.1f), 0.45f, 0.4f, 0.5f, 0f, len.coerceAtLeast(1), emptyMap()))
            steps.add(TypeStep.Reveal({
                typedTo = ""; typedCc = ""; typedSubject = ""; typedBody = ""; field = -1; sendState = 0; revision++
                rt.audio.profile = EmailStore.keySound; bn(1)
            }))
            steps.add(TypeStep.Reveal({ field = 0; bn(EmailStore.to.length); revision++ }, delay = 140))
            steps.add(TypeStep.Type(EmailStore.to, { typedTo = it; revision++ }))
            steps.add(TypeStep.Pause(360))
            if (EmailStore.showCc) {
                steps.add(TypeStep.Reveal({ field = 1; bn(EmailStore.cc.length); revision++ }, delay = 120))
                steps.add(TypeStep.Type(EmailStore.cc, { typedCc = it; revision++ }))
                steps.add(TypeStep.Pause(320))
            }
            steps.add(TypeStep.Reveal({ field = 2; bn(EmailStore.subject.length); revision++ }, delay = 120))
            steps.add(TypeStep.Type(EmailStore.subject, { typedSubject = it; revision++ }))
            steps.add(TypeStep.Pause(420))
            steps.add(TypeStep.Reveal({ field = 3; bn(EmailStore.body.length); revision++ }, delay = 160))
            steps.add(TypeStep.Type(EmailStore.body, { typedBody = it; revision++ }))
            steps.add(TypeStep.Reveal({ field = -1; sendState = 1; revision++; if (EmailStore.soundsOn) rt.audio.cue("whoosh") }, delay = 1000))
            steps.add(TypeStep.Reveal({ sendState = 2; revision++; if (EmailStore.soundsOn) rt.audio.cue("tritone") }, delay = 1600))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }

        val caretOn = run {
            val t = rememberInfiniteTransition(label = "caret")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
        }
        fun caret(active: Boolean) = if (!preview && active && caretOn) "▏" else ""

        val toV = if (preview) EmailStore.to else typedTo
        val ccV = if (preview) EmailStore.cc else typedCc
        val subjectV = if (preview) EmailStore.subject else typedSubject
        val bodyV = if (preview) EmailStore.body else typedBody
        val sending = if (preview) 0 else sendState

        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * EmailStore.textScale)) {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                Column(Modifier.fillMaxSize()) {
                    TopBar(c, accent)
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        if (EmailStore.sidebar) Sidebar(c, accent, EmailStore.inbox.count { it.unread })
                        Inbox(c, accent, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                ComposePopup(
                    c, accent,
                    to = toV + caret(field == 0), cc = ccV + caret(field == 1),
                    subject = subjectV + caret(field == 2), body = bodyV + caret(field == 3),
                    showCc = EmailStore.showCc || ccV.isNotEmpty(), center = EmailStore.composeCenter, sending = sending,
                    modifier = Modifier.align(if (EmailStore.composeCenter) Alignment.BottomCenter else Alignment.BottomEnd),
                )
            }
        }
    }
}

private class Pal(
    val bg: Color, val line: Color, val surface: Color, val text: Color, val sub: Color,
    val search: Color, val navActive: Color, val navActiveText: Color, val composeHead: Color, val rowUnread: Color,
)

private fun palette(dark: Boolean) = if (dark) Pal(
    bg = Color(0xFF131314), line = Color(0xFF2F3033), surface = Color(0xFF1E1F20), text = Color(0xFFE3E3E3), sub = Color(0xFF9AA0A6),
    search = Color(0xFF1F2023), navActive = Color(0xFF004A77), navActiveText = Color(0xFFC2E7FF), composeHead = Color(0xFF2D2F31), rowUnread = Color(0xFF2D2F31),
) else Pal(
    bg = Color(0xFFF6F8FC), line = Color(0xFFE3E6EA), surface = Color.White, text = Color(0xFF202124), sub = Color(0xFF5F6368),
    search = Color(0xFFEAF1FB), navActive = Color(0xFFD3E3FD), navActiveText = Color(0xFF041E49), composeHead = Color(0xFFF2F6FC), rowUnread = Color.White,
)

@Composable
private fun TopBar(c: Pal, accent: Color) {
    Row(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("≡", color = c.sub, fontSize = 40.sp)
        GmailLogo(); Text("Gmail", color = c.sub, fontSize = 36.sp)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.width(720.dp).height(64.dp).clip(RoundedCornerShape(16.dp)).background(c.search).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🔍", fontSize = 28.sp); Text("Search mail", color = c.sub, fontSize = 28.sp)
        }
        Spacer(Modifier.weight(1f))
        Avatar(initials(EmailStore.from), 64.dp, 28.sp)
    }
}

@Composable
private fun GmailLogo() {
    Canvas(Modifier.size(44.dp, 34.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(Color.White, topLeft = Offset(0f, h * 0.08f), size = androidx.compose.ui.geometry.Size(w, h * 0.84f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f))
        val flap = androidx.compose.ui.graphics.Path().apply { moveTo(0f, h * 0.16f); lineTo(w / 2f, h * 0.62f); lineTo(w, h * 0.16f) }
        drawPath(flap, Color(0xFFEA4335), style = Stroke(width = 7f))
        drawLine(Color(0xFF4285F4), Offset(2f, h * 0.16f), Offset(2f, h * 0.9f), strokeWidth = 7f)
        drawLine(Color(0xFF34A853), Offset(w - 2f, h * 0.16f), Offset(w - 2f, h * 0.9f), strokeWidth = 7f)
    }
}

@Composable
private fun Avatar(text: String, sz: androidx.compose.ui.unit.Dp, fs: androidx.compose.ui.unit.TextUnit) {
    Box(Modifier.size(sz).clip(RoundedCornerShape(50)).background(Brush.linearGradient(listOf(Color(0xFFC2410C), Color(0xFFF59E0B)))), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = fs, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Sidebar(c: Pal, accent: Color, unread: Int) {
    Column(Modifier.width(300.dp).fillMaxHeight().padding(start = 24.dp, end = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFE7EEFB)).padding(horizontal = 30.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("✎", color = Color(0xFF41331C), fontSize = 30.sp); Text("Compose", color = Color(0xFF41331C), fontSize = 28.sp, fontWeight = FontWeight.Medium)
        }
        val items = listOf("📥" to "Inbox", "★" to "Starred", "🕘" to "Snoozed", "➤" to "Sent", "📄" to "Drafts")
        Column {
            items.forEachIndexed { i, (ic, label) ->
                val active = i == 0
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(0.dp, 32.dp, 32.dp, 0.dp))
                        .background(if (active) c.navActive else Color.Transparent).padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Text(ic, fontSize = 26.sp)
                    Text(label, color = if (active) c.navActiveText else c.text, fontSize = 26.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (active && unread > 0) Text("$unread", color = c.navActiveText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Inbox(c: Pal, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 4.dp, end = 24.dp).clip(RoundedCornerShape(22.dp, 22.dp, 0.dp, 0.dp)).background(c.surface)) {
        // toolbar
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Text("▢", color = c.sub, fontSize = 28.sp); Text("⟳", color = c.sub, fontSize = 28.sp); Text("⋮", color = c.sub, fontSize = 28.sp)
            Spacer(Modifier.weight(1f))
            Text("1–${EmailStore.inbox.size} of ${maxOf(EmailStore.inbox.size, 1) * 37}", color = c.sub, fontSize = 22.sp)
            Text("‹", color = c.sub, fontSize = 28.sp); Text("›", color = c.sub, fontSize = 28.sp)
        }
        // tabs
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            listOf("Primary" to true, "Promotions" to false, "Social" to false).forEach { (t, active) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, color = if (active) accent else c.sub, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 30.dp, vertical = 18.dp))
                    Spacer(Modifier.height(4.dp).width(120.dp).background(if (active) accent else Color.Transparent))
                }
            }
        }
        Spacer(Modifier.height(1.dp).fillMaxWidth().background(c.line))
        // rows
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            EmailStore.inbox.forEach { r ->
                Row(Modifier.fillMaxWidth().height(76.dp).background(if (r.unread) c.rowUnread else Color.Transparent).padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Text("▢", color = c.sub, fontSize = 26.sp); Text("☆", color = c.sub, fontSize = 26.sp)
                    Text(r.from, color = if (r.unread) c.text else c.sub, fontSize = 26.sp, fontWeight = if (r.unread) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(280.dp))
                    Row(Modifier.weight(1f)) {
                        Text(r.subject, color = if (r.unread) c.text else c.sub, fontSize = 26.sp, fontWeight = if (r.unread) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(" — ${r.snippet}", color = Color(0xFF80868B), fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(r.time, color = c.sub, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun ComposePopup(
    c: Pal, accent: Color, to: String, cc: String, subject: String, body: String,
    showCc: Boolean, center: Boolean, sending: Int, modifier: Modifier = Modifier,
) {
    val w = if (center) 980.dp else 660.dp
    val h = if (center) 760.dp else 620.dp
    Column(
        modifier.padding(end = if (center) 0.dp else 80.dp).width(w).height(h)
            .clip(RoundedCornerShape(16.dp, 16.dp, 0.dp, 0.dp)).background(c.surface),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).background(c.composeHead).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("New Message", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("—  ⤢  ✕", color = c.sub, fontSize = 24.sp)
        }
        ComposeRow("To", to, c, false)
        if (showCc) ComposeRow("Cc", cc, c, false)
        ComposeRow("", subject.ifEmpty { "" }, c, true, placeholder = "Subject")
        Text(body.ifEmpty { "Compose email" }, color = if (body.isEmpty()) Color(0xFF9AA0A6) else c.text, fontSize = 27.sp, lineHeight = 40.sp, fontFamily = EmailStore.font.family, modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp))
        if (EmailStore.attachment.isNotBlank()) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(c.search).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📎", fontSize = 24.sp)
                Text(EmailStore.attachment, color = c.text, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.clip(RoundedCornerShape(26.dp)).background(accent).padding(horizontal = 40.dp, vertical = 16.dp)) {
                Text(when (sending) { 2 -> "Sent ✓"; 1 -> "Sending…"; else -> "Send" }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("A  📎  🙂  🖼  🔒", color = c.sub, fontSize = 26.sp)
            Spacer(Modifier.weight(1f))
            Text("🗑", fontSize = 28.sp)
        }
    }
}

@Composable
private fun ComposeRow(label: String, value: String, c: Pal, subject: Boolean, placeholder: String = "") {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!subject) Text(label, color = c.sub, fontSize = 26.sp, modifier = Modifier.width(70.dp))
        val empty = value.isBlank()
        Text(if (empty) placeholder else value, color = if (empty) Color(0xFF9AA0A6) else c.text, fontSize = 26.sp, fontWeight = if (subject) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(1.dp).fillMaxWidth().background(c.line))
}
