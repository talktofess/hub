package com.example.recorder.sims.claude

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

private val SPIN = listOf("✻", "✶", "✳", "✺", "✸", "✷")
private val VERBS = listOf(
    "Thinking", "Pondering", "Percolating", "Noodling", "Conjuring", "Cogitating", "Ruminating", "Finessing",
    "Inferring", "Lollygagging", "Musing", "Deliberating", "Marinating", "Simmering", "Brewing", "Churning",
    "Wrangling", "Synthesizing", "Untangling", "Vibing", "Spelunking", "Frolicking", "Moseying", "Schlepping",
    "Reticulating", "Honking", "Puzzling", "Scheming", "Tinkering", "Mulling", "Contemplating", "Galloping",
    "Sleuthing", "Incubating", "Whirring", "Computing", "Divining", "Hatching", "Concocting", "Pontificating",
)
private val TRACE = listOf("parsing the request", "weighing a couple angles", "drafting a first pass", "checking the tone", "trimming the fat", "almost there")

private val BG = Color(0xFF0D0D0F)
private val ACCENT = Color(0xFFD97757)

object ClaudeSim : SimDef {
    override val id = "claude"
    override val label = "Claude Code"
    override val glyph = "✳"
    override val accent = ACCENT
    override val frame = SimFrame.PHONE
    override val logical = SimLogical(1080, 1920)
    override val ready = true
    override val defaultScript = ""

    override fun reset() = ClaudeStore.reset()
    override val tabLabel = "Prompt"

    @Composable
    override fun Builder(ctx: com.example.recorder.sims.BuilderContext) = ClaudeBuilder(ctx)

    override fun toJson() = JSONObject().apply {
        put("model", ClaudeStore.model); put("name", ClaudeStore.name); put("account", ClaudeStore.account)
        put("cwd", ClaudeStore.cwd); put("version", ClaudeStore.version); put("thinkMs", ClaudeStore.thinkMs)
        put("tokenTarget", ClaudeStore.tokenTarget)
        put("verb", ClaudeStore.verb); put("prompt", ClaudeStore.prompt); put("reply", ClaudeStore.reply)
        put("typeSpeed", ClaudeStore.typeSpeed.toDouble()); put("pacing", ClaudeStore.pacing.toDouble())
        put("keySound", ClaudeStore.keySound.name); put("streamSpeed", ClaudeStore.streamSpeed.toDouble())
        put("welcomeBox", ClaudeStore.welcomeBox)
    }

    override fun fromJson(o: JSONObject) {
        ClaudeStore.model = o.optString("model", ClaudeStore.model)
        ClaudeStore.name = o.optString("name", ClaudeStore.name)
        ClaudeStore.account = o.optString("account", ClaudeStore.account)
        ClaudeStore.cwd = o.optString("cwd", ClaudeStore.cwd)
        ClaudeStore.version = o.optString("version", ClaudeStore.version)
        ClaudeStore.thinkMs = o.optInt("thinkMs", ClaudeStore.thinkMs)
        ClaudeStore.tokenTarget = o.optInt("tokenTarget", ClaudeStore.tokenTarget)
        ClaudeStore.verb = o.optString("verb", ClaudeStore.verb)
        ClaudeStore.prompt = o.optString("prompt", ClaudeStore.prompt)
        ClaudeStore.reply = o.optString("reply", ClaudeStore.reply)
        ClaudeStore.typeSpeed = o.optDouble("typeSpeed", 0.85).toFloat()
        ClaudeStore.pacing = o.optDouble("pacing", 0.5).toFloat()
        ClaudeStore.keySound = runCatching { com.example.recorder.model.SoundProfile.valueOf(o.optString("keySound")) }.getOrDefault(com.example.recorder.model.SoundProfile.KEYBOARD)
        ClaudeStore.streamSpeed = o.optDouble("streamSpeed", 1.0).toFloat()
        ClaudeStore.welcomeBox = o.optBoolean("welcomeBox", true)
    }

    @Composable
    override fun Content(rt: SimRuntime) {
        val preview = !rt.playing
        val fs = rt.settings.fontScale
        val s = ClaudeStore

        var psCmd by remember { mutableStateOf("") }
        var welcome by remember { mutableStateOf(false) }
        var userText by remember { mutableStateOf("") }
        var userTyping by remember { mutableStateOf(false) }
        var thinking by remember { mutableStateOf(false) }
        var reply by remember { mutableStateOf("") }
        var revision by remember { mutableIntStateOf(0) }

        fun buildPlan(): List<TypeStep> {
            val steps = mutableListOf<TypeStep>()
            fun bn(len: Int) = rt.beginNote(NoteTiming(s.typeSpeed.coerceAtLeast(0.1f), s.pacing, 0.5f, 0.6f, 0f, len.coerceAtLeast(1), emptyMap()))
            steps.add(TypeStep.Reveal({
                psCmd = ""; welcome = false; userText = ""; userTyping = false; thinking = false; reply = ""; revision++
                rt.audio.profile = s.keySound; bn(1)
            }))
            // 1) launch — type `claude`
            steps.add(TypeStep.Reveal({ bn("claude".length) }))
            steps.add(TypeStep.Type("claude", { psCmd = it; revision++ }))
            steps.add(TypeStep.Pause(340))
            // 2) welcome box fades in
            if (s.welcomeBox) {
                steps.add(TypeStep.Reveal({ welcome = true; revision++ }))
                steps.add(TypeStep.Pause(820))
            } else {
                steps.add(TypeStep.Reveal({ welcome = true; revision++ }))
            }
            // 3) type the prompt
            steps.add(TypeStep.Reveal({ userTyping = true; bn(s.prompt.length); revision++ }))
            steps.add(TypeStep.Type(s.prompt, { userText = it; revision++ }))
            steps.add(TypeStep.Pause(420))
            steps.add(TypeStep.Reveal({ userTyping = false; revision++ }))
            steps.add(TypeStep.Pause(380))
            // 4) thinking beat — stays until the answer is ready
            steps.add(TypeStep.Reveal({ thinking = true; revision++ }))
            steps.add(TypeStep.Pause(s.thinkMs.coerceIn(0, 60000)))
            // 5) stream the reply word by word — the thinking line vanishes the instant the
            // first answer token lands (it doesn't linger).
            val tokens = Regex("""\s+|\S+""").findAll(s.reply).map { it.value }.toList()
            val delay = (58 / s.streamSpeed.coerceAtLeast(0.1f)).toInt()
            tokens.forEachIndexed { i, tok ->
                steps.add(TypeStep.Reveal({
                    if (i == 0) thinking = false
                    reply += tok; revision++
                    if (tok.isNotBlank()) rt.audio.key()
                }))
                steps.add(TypeStep.Pause(delay))
            }
            steps.add(TypeStep.Pause(900))
            return steps
        }
        rt.planFactory = { buildPlan() }
        DisposableEffect(Unit) { onDispose { rt.planFactory = null } }

        val caretOn = run {
            val t = rememberInfiniteTransition(label = "caret")
            t.animateFloat(0f, 1f, infiniteRepeatable(tween(1060, easing = LinearEasing)), label = "b").value < 0.5f
        }
        // spinner + thinking progress
        val spinIdx = run {
            val t = rememberInfiniteTransition(label = "spin")
            t.animateFloat(0f, SPIN.size.toFloat(), infiniteRepeatable(tween(660, easing = LinearEasing)), label = "s").value.toInt() % SPIN.size
        }
        // cycle the whimsical verb every ~1.4s while thinking (unless one is pinned)
        val verbIdx = run {
            val t = rememberInfiniteTransition(label = "verb")
            t.animateFloat(0f, VERBS.size.toFloat(), infiniteRepeatable(tween(VERBS.size * 1400, easing = LinearEasing)), label = "v").value.toInt() % VERBS.size
        }
        val progress by animateFloatAsState(if (thinking) 1f else 0f, tween(s.thinkMs.coerceIn(1, 60000), easing = LinearEasing), label = "think")

        // static preview
        val sPs = if (preview) "claude" else psCmd
        val sWelcome = if (preview) s.welcomeBox else welcome
        val sUser = if (preview) settledText(s.prompt) else userText
        val sReply = if (preview) s.reply else reply
        val showUser = preview || welcome
        val showUserCaret = !preview && userTyping

        val scroll = rememberScrollState()
        LaunchedEffect(revision) { scroll.scrollTo(scroll.maxValue) }

        val base = 34f * fs
        Column(Modifier.fillMaxSize().background(BG).padding(start = 16.dp, end = 16.dp, top = 46.dp)) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
                // PowerShell launch line
                Row(Modifier.padding(bottom = 28.dp)) {
                    Text("PS ${s.cwd}> ", color = Color(0xFFCDCDC9), fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.5f).sp)
                    Text(sPs, color = Color(0xFFF2F2EE), fontWeight = FontWeight.Bold, fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.5f).sp)
                    if (!sWelcome) Caret(caretOn, base)
                }

                if (sWelcome) WelcomeBox(base)

                if (showUser && (sUser.isNotEmpty() || showUserCaret)) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                        Text(">", color = ACCENT, fontWeight = FontWeight.Bold, fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.5f).sp)
                        Spacer(Modifier.width(20.dp))
                        Row(Modifier.weight(1f)) {
                            Text(sUser, color = Color(0xFFD2CFCA), fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.5f).sp)
                            if (showUserCaret) Caret(caretOn, base)
                        }
                    }
                }

                if (thinking) {
                    val verb = s.verb.ifBlank { VERBS[verbIdx] }
                    val secs = (progress * s.thinkMs / 1000f).toInt()
                    val tokens = (s.tokenTarget * (progress * (2 - progress))).toInt()
                    val traceIdx = (progress * TRACE.size).toInt().coerceIn(0, TRACE.size - 1)
                    Column(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(SPIN[spinIdx], color = ACCENT, fontSize = base.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(18.dp))
                            Text("$verb…", color = Color(0xFFD7D6D2), fontSize = base.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(18.dp))
                            Text("(${secs}s · ↑ ${fmtTok(tokens)} tokens · esc to interrupt)", color = Color(0xFF8A8A86), fontSize = (base * 0.8f).sp, fontFamily = FontFamily.Monospace)
                        }
                        Text("${TRACE[traceIdx]}…", color = Color(0xFF75736E), fontSize = (base * 0.8f).sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 52.dp, top = 12.dp))
                    }
                }

                if (sReply.isNotEmpty()) {
                    Row(Modifier.padding(top = 6.dp, bottom = 30.dp), verticalAlignment = Alignment.Top) {
                        Text("⏺", color = ACCENT, fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.5f).sp)
                        Spacer(Modifier.width(20.dp))
                        Text(sReply, color = Color(0xFFE3E2DE), fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.5f).sp, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
            if (!sWelcome && !preview) {
                Text("? for shortcuts  ·  launching…", color = Color(0xFF8A8A86), fontSize = (base * 0.75f).sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 22.dp))
            }
        }
    }

    @Composable
    private fun WelcomeBox(base: Float) {
        val s = ClaudeStore
        val short = s.model.replace(Regex("^(?i)claude\\s+"), "")
        Box(Modifier.fillMaxWidth().padding(bottom = 40.dp)) {
            Column(
                Modifier.fillMaxWidth().border(2.dp, ACCENT, RoundedCornerShape(16.dp))
                    .padding(start = 36.dp, end = 36.dp, top = 44.dp, bottom = 34.dp),
            ) {
                Text("✳", color = ACCENT, fontSize = (base * 2.75f).sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp))
                Text("Welcome back ${s.name}!", color = Color(0xFFF0EFEB), fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.3f).sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp))
                Text(
                    "$short (1M context) · Claude Max\n${s.account}'s Organization\n${s.cwd}",
                    color = Color(0xFF9B9B97), fontSize = (base * 0.8f).sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.3f).sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 28.dp).height(1.dp).background(Color(0x1AFFFFFF)))
                Text("Tips for getting started", color = ACCENT, fontWeight = FontWeight.Bold, fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.3f).sp, modifier = Modifier.padding(bottom = 10.dp))
                Text("Run /init to create a CLAUDE.md with instructions for Claude.", color = Color(0xFFBDBCB7), fontSize = (base * 0.85f).sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.25f).sp, modifier = Modifier.padding(bottom = 16.dp))
                Text("What's new", color = ACCENT, fontWeight = FontWeight.Bold, fontSize = base.sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.3f).sp, modifier = Modifier.padding(bottom = 10.dp))
                Text("• /usage now shows a per-category breakdown of spend", color = Color(0xFFBDBCB7), fontSize = (base * 0.85f).sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.25f).sp)
                Text("• /diff detail view can be scrolled with the keyboard", color = Color(0xFFBDBCB7), fontSize = (base * 0.85f).sp, fontFamily = FontFamily.Monospace, lineHeight = (base * 1.25f).sp)
            }
            // legend tab overlapping the top border
            Text(
                "Claude Code v${s.version}", color = ACCENT, fontSize = (base * 0.85f).sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.offset(x = 28.dp, y = (-14).dp).background(BG).padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun Caret(on: Boolean, base: Float) {
    Box(
        Modifier.padding(start = 2.dp).width((base * 0.5f).dp).height((base * 1.1f).dp)
            .background(if (on) Color(0xFFD7D6D2) else Color.Transparent),
    )
}

private fun fmtTok(n: Int): String = if (n >= 1000) "%.1fk".format(n / 1000f) else n.toString()
