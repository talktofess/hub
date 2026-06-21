package com.example.recorder.store

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.recorder.model.BgKind
import com.example.recorder.model.BgMedia
import com.example.recorder.model.BgScale
import com.example.recorder.model.CaretStyle
import com.example.recorder.model.Settings
import com.example.recorder.model.SoundProfile
import com.example.recorder.model.ThemeMode
import com.example.recorder.sims.email.EmailStore
import com.example.recorder.sims.email.InboxRow
import com.example.recorder.sims.lists.Block
import com.example.recorder.sims.lists.BlockType
import com.example.recorder.sims.lists.ListsStore
import com.example.recorder.sims.lists.StartMode
import com.example.recorder.sims.lists.Trans
import com.example.recorder.sims.imessage.Message
import com.example.recorder.sims.imessage.MessagesStore
import com.example.recorder.sims.whatsapp.WhatsAppStore
import com.example.recorder.sims.notes.NoteAlign
import com.example.recorder.sims.notes.NoteConfig
import com.example.recorder.sims.notes.NoteFont
import com.example.recorder.sims.notes.NoteShape
import com.example.recorder.sims.notes.PaperStyle
import com.example.recorder.sims.notes.WordAction
import com.example.recorder.sims.notes.WordActionKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A saved project = the notes + all settings, serialized to JSON on disk. */
object Projects {
    const val AUTOSAVE = "~autosave"

    private fun dir(ctx: Context) = File(ctx.filesDir, "projects").apply { mkdirs() }
    private fun file(ctx: Context, name: String) = File(dir(ctx), "$name.json")

    /** User-visible project names (excludes the hidden autosave). */
    fun list(ctx: Context): List<String> =
        dir(ctx).listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.filter { it != AUTOSAVE }
            ?.sorted() ?: emptyList()

    fun save(ctx: Context, name: String, notes: List<NoteConfig>, settings: Settings) {
        val root = JSONObject()
        root.put("notes", JSONArray().apply { notes.forEach { put(noteToJson(it)) } })
        root.put("settings", settingsToJson(settings))
        root.put("chat", chatToJson())
        root.put("wa", waToJson())
        root.put("email", emailToJson())
        root.put("lists", listsToJson())
        // generic per-sim persistence (any sim that implements toJson — newer sims)
        root.put("sims", JSONObject().apply { com.example.recorder.sims.SIMS.forEach { s -> s.toJson()?.let { put(s.id, it) } } })
        file(ctx, name).writeText(root.toString())
    }

    fun delete(ctx: Context, name: String) {
        file(ctx, name).delete()
    }

    fun load(ctx: Context, name: String): Pair<List<NoteConfig>, Settings>? {
        val f = file(ctx, name)
        if (!f.exists()) return null
        return try {
            val root = JSONObject(f.readText())
            val arr = root.getJSONArray("notes")
            val notes = (0 until arr.length()).map { noteFromJson(arr.getJSONObject(it)) }
            val settings = settingsFromJson(root.getJSONObject("settings"))
            root.optJSONObject("chat")?.let { applyChat(it) }
            root.optJSONObject("wa")?.let { applyWa(it) }
            root.optJSONObject("email")?.let { applyEmail(it) }
            root.optJSONObject("lists")?.let { applyLists(it) }
            root.optJSONObject("sims")?.let { so -> com.example.recorder.sims.SIMS.forEach { s -> so.optJSONObject(s.id)?.let { s.fromJson(it) } } }
            notes to settings
        } catch (_: Throwable) {
            null
        }
    }

    // ---------- NoteConfig ----------

    private fun noteToJson(n: NoteConfig) = JSONObject().apply {
        put("id", n.id)
        put("shape", n.shape.name)
        put("header", n.header); put("body", n.body); put("footer", n.footer)
        put("color", n.color); put("imageUri", n.imageUri ?: JSONObject.NULL); put("imageRotation", n.imageRotation.toDouble())
        put("paper", n.paper.name); put("lineColor", n.lineColor); put("font", n.font.name)
        put("marginLine", n.marginLine); put("tape", n.tape); put("dogEar", n.dogEar)
        put("pin", n.pin); put("border", n.border); put("sharpCorners", n.sharpCorners)
        put("align", n.align.name); put("alpha", n.alpha.toDouble())
        put("textColor", n.textColor); put("bold", n.bold); put("italic", n.italic); put("underline", n.underline)
        n.sound?.let { put("sound", it.name) }
        n.speed?.let { put("speed", it.toDouble()) }
        n.fontScale?.let { put("fontScale", it.toDouble()) }
        n.rotation?.let { put("rotation", it.toDouble()) }
        n.humanize?.let { put("humanize", it.toDouble()) }
        n.thinkPauses?.let { put("thinkPauses", it.toDouble()) }
        n.dwellCount?.let { put("dwellCount", it) }
        n.reconsiderCount?.let { put("reconsiderCount", it) }
        put("wordActions", JSONArray().apply {
            n.wordActions.forEach {
                put(JSONObject().put("wordIndex", it.wordIndex).put("kind", it.kind.name).put("dwellMs", it.dwellMs).put("wrong", it.wrong))
            }
        })
    }

    private fun noteFromJson(o: JSONObject): NoteConfig {
        fun fOpt(k: String): Float? = if (o.has(k) && !o.isNull(k)) o.getDouble(k).toFloat() else null
        fun iOpt(k: String): Int? = if (o.has(k) && !o.isNull(k)) o.getInt(k) else null
        val actions = o.optJSONArray("wordActions")?.let { arr ->
            (0 until arr.length()).map {
                val w = arr.getJSONObject(it)
                WordAction(w.getInt("wordIndex"), enumOr(w.optString("kind"), WordActionKind.RECONSIDER), w.optInt("dwellMs", 800), w.optString("wrong"))
            }
        } ?: emptyList()
        return NoteConfig(
            id = o.optLong("id", 0),
            shape = enumOr(o.optString("shape"), NoteShape.STICKY),
            header = o.optString("header"), body = o.optString("body"), footer = o.optString("footer"),
            color = o.optLong("color", 0xFFFFE16A),
            imageUri = if (o.isNull("imageUri")) null else o.optString("imageUri").ifEmpty { null },
            imageRotation = o.optDouble("imageRotation", 0.0).toFloat(),
            paper = enumOr(o.optString("paper"), PaperStyle.PLAIN),
            lineColor = o.optLong("lineColor", 0xFF9DB4CC),
            font = enumOr(o.optString("font"), NoteFont.HANDWRITING),
            marginLine = o.optBoolean("marginLine"), tape = o.optBoolean("tape"), dogEar = o.optBoolean("dogEar"),
            pin = o.optBoolean("pin"), border = o.optBoolean("border"), sharpCorners = o.optBoolean("sharpCorners"),
            align = enumOr(o.optString("align"), NoteAlign.START),
            alpha = o.optDouble("alpha", 1.0).toFloat(),
            textColor = o.optLong("textColor", 0xFF1B1B1B),
            bold = o.optBoolean("bold"), italic = o.optBoolean("italic"), underline = o.optBoolean("underline"),
            sound = if (o.has("sound")) enumOr<SoundProfile>(o.optString("sound"), SoundProfile.MECHANICAL) else null,
            speed = fOpt("speed"), fontScale = fOpt("fontScale"), rotation = fOpt("rotation"),
            humanize = fOpt("humanize"), thinkPauses = fOpt("thinkPauses"),
            dwellCount = iOpt("dwellCount"), reconsiderCount = iOpt("reconsiderCount"),
            wordActions = actions,
        )
    }

    // ---------- Settings ----------

    private fun settingsToJson(s: Settings) = JSONObject().apply {
        put("sound", s.sound.name); put("liveVolume", s.liveVolume.toDouble()); put("recordVolume", s.recordVolume.toDouble())
        put("speed", s.speed.toDouble()); put("startDelay", s.startDelay); put("thinkPauses", s.thinkPauses.toDouble())
        put("jitter", s.jitter.toDouble()); put("autoTypo", s.autoTypo.toDouble()); put("humanize", s.humanize.toDouble())
        put("dwellCount", s.dwellCount); put("reconsiderCount", s.reconsiderCount); put("loop", s.loop); put("holdEnd", s.holdEnd)
        put("showCaret", s.showCaret); put("caretStyle", s.caretStyle.name); put("caretColor", s.caretColor.toArgb()); put("caretBlink", s.caretBlink)
        put("theme", s.theme.name); put("accent", s.accent.toArgb()); put("fontScale", s.fontScale.toDouble())
        put("grain", s.grain); put("vignette", s.vignette); put("recordScale", s.recordScale.toDouble())
        put("bg", JSONObject().apply {
            put("uri", s.bg.uri ?: JSONObject.NULL); put("kind", s.bg.kind.name); put("scale", s.bg.scale.name)
            put("opacity", s.bg.opacity.toDouble()); put("appearAtSec", s.bg.appearAtSec.toDouble())
        })
        put("bgAudioUri", s.bgAudioUri ?: JSONObject.NULL); put("bgAudioVolume", s.bgAudioVolume.toDouble())
        put("sim", JSONObject().apply {
            s.sim.forEach { (simId, m) -> put(simId, JSONObject().apply { m.forEach { (k, v) -> put(k, (v as? Number)?.toDouble() ?: 0.0) } }) }
        })
    }

    private fun settingsFromJson(o: JSONObject): Settings {
        val bgO = o.optJSONObject("bg") ?: JSONObject()
        val bg = BgMedia(
            uri = if (bgO.isNull("uri")) null else bgO.optString("uri").ifEmpty { null },
            kind = enumOr(bgO.optString("kind"), BgKind.NONE),
            scale = enumOr(bgO.optString("scale"), BgScale.COVER),
            opacity = bgO.optDouble("opacity", 1.0).toFloat(),
            appearAtSec = bgO.optDouble("appearAtSec", -1.0).toFloat(),
        )
        val sim = HashMap<String, Map<String, Any?>>()
        o.optJSONObject("sim")?.let { so ->
            so.keys().forEach { simId ->
                val mo = so.getJSONObject(simId)
                val m = HashMap<String, Any?>()
                mo.keys().forEach { k -> m[k] = mo.getDouble(k).toFloat() }
                sim[simId] = m
            }
        }
        return Settings(
            sound = enumOr(o.optString("sound"), SoundProfile.MECHANICAL),
            liveVolume = o.optDouble("liveVolume", 0.85).toFloat(),
            recordVolume = o.optDouble("recordVolume", 0.85).toFloat(),
            speed = o.optDouble("speed", 1.0).toFloat(),
            startDelay = o.optInt("startDelay", 600),
            thinkPauses = o.optDouble("thinkPauses", 0.5).toFloat(),
            jitter = o.optDouble("jitter", 0.5).toFloat(),
            autoTypo = o.optDouble("autoTypo", 0.0).toFloat(),
            humanize = o.optDouble("humanize", 0.4).toFloat(),
            dwellCount = o.optInt("dwellCount", 1),
            reconsiderCount = o.optInt("reconsiderCount", 1),
            loop = o.optBoolean("loop"),
            holdEnd = o.optInt("holdEnd", 1200),
            showCaret = o.optBoolean("showCaret", true),
            caretStyle = enumOr(o.optString("caretStyle"), CaretStyle.BAR),
            caretColor = Color(o.optInt("caretColor", Color.White.toArgb())),
            caretBlink = o.optBoolean("caretBlink", true),
            theme = enumOr(o.optString("theme"), ThemeMode.DARK),
            accent = Color(o.optInt("accent", Color(0xFF5B8DEF).toArgb())),
            fontScale = o.optDouble("fontScale", 1.0).toFloat(),
            grain = o.optBoolean("grain"),
            vignette = o.optBoolean("vignette"),
            recordScale = o.optDouble("recordScale", 1.0).toFloat(),
            bg = bg,
            bgAudioUri = if (o.isNull("bgAudioUri")) null else o.optString("bgAudioUri").ifEmpty { null },
            bgAudioVolume = o.optDouble("bgAudioVolume", 0.5).toFloat(),
            sim = sim,
        )
    }

    // ---------- iMessage chat ----------

    private fun chatToJson() = JSONObject().apply {
        val s = MessagesStore
        put("contactName", s.contactName)
        put("avatarUri", s.avatarUri ?: JSONObject.NULL)
        put("sentColor", s.sentColor); put("receivedColor", s.receivedColor)
        put("sentTextColor", s.sentTextColor); put("receivedTextColor", s.receivedTextColor)
        put("font", s.font.name); put("textScale", s.textScale.toDouble())
        put("typeSpeed", s.typeSpeed.toDouble()); put("pacing", s.pacing.toDouble()); put("keySound", s.keySound.name)
        put("sendSound", s.sendSound); put("receiveSound", s.receiveSound); put("msgGap", s.msgGap); put("typingDur", s.typingDur); put("showKeyboard", s.showKeyboard)
        put("statusBar", s.statusBar); put("clock", s.clock); put("dateLabel", s.dateLabel); put("readReceipt", s.readReceipt)
        put("messages", JSONArray().apply { s.messages.forEach { put(messageToJson(it)) } })
    }

    private fun applyChat(o: JSONObject) {
        val s = MessagesStore
        s.avatarUri = if (o.isNull("avatarUri")) null else o.optString("avatarUri").ifEmpty { null }
        s.sentColor = o.optLong("sentColor", 0xFF0A84FF)
        s.receivedColor = o.optLong("receivedColor", 0xFFE9E9EB)
        s.sentTextColor = o.optLong("sentTextColor", 0xFFFFFFFF)
        s.receivedTextColor = o.optLong("receivedTextColor", 0xFF000000)
        s.font = enumOr(o.optString("font"), NoteFont.PRINT)
        s.textScale = o.optDouble("textScale", 1.0).toFloat()
        s.typeSpeed = o.optDouble("typeSpeed", 1.0).toFloat(); s.pacing = o.optDouble("pacing", 0.3).toFloat()
        s.keySound = enumOr(o.optString("keySound"), SoundProfile.MECHANICAL)
        s.sendSound = o.optString("sendSound", "whoosh"); s.receiveSound = o.optString("receiveSound", "tritone")
        s.msgGap = o.optInt("msgGap", 620); s.typingDur = o.optInt("typingDur", 1300); s.showKeyboard = o.optBoolean("showKeyboard", true)
        s.statusBar = o.optBoolean("statusBar", true); s.clock = o.optString("clock", "9:41")
        s.dateLabel = o.optString("dateLabel", "iMessage • Today"); s.readReceipt = o.optBoolean("readReceipt", true)
        s.setAll(o.optString("contactName", "Alex"), messagesFromJson(o.optJSONArray("messages")))
    }

    private fun messageToJson(m: Message) = JSONObject()
        .put("id", m.id).put("text", m.text).put("fromMe", m.fromMe)
        .put("imageUri", m.imageUri ?: JSONObject.NULL).put("reaction", m.reaction ?: JSONObject.NULL)
        .put("delayMs", m.delayMs)

    private fun messagesFromJson(arr: JSONArray?): List<Message> =
        if (arr == null) emptyList() else (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            Message(
                m.optLong("id"), m.optString("text"), m.optBoolean("fromMe", true),
                if (m.isNull("imageUri")) null else m.optString("imageUri").ifEmpty { null },
                if (m.isNull("reaction")) null else m.optString("reaction").ifEmpty { null },
                m.optInt("delayMs", 0),
            )
        }

    // ---------- WhatsApp chat ----------

    private fun waToJson() = JSONObject().apply {
        val s = WhatsAppStore
        put("contactName", s.contactName); put("status", s.status); put("stamp", s.stamp)
        put("avatarUri", s.avatarUri ?: JSONObject.NULL); put("wallpaper", s.wallpaper)
        put("sentColor", s.sentColor); put("receivedColor", s.receivedColor)
        put("sentTextColor", s.sentTextColor); put("receivedTextColor", s.receivedTextColor)
        put("font", s.font.name); put("textScale", s.textScale.toDouble())
        put("typeSpeed", s.typeSpeed.toDouble()); put("pacing", s.pacing.toDouble()); put("keySound", s.keySound.name)
        put("sendSound", s.sendSound); put("receiveSound", s.receiveSound); put("msgGap", s.msgGap); put("typingDur", s.typingDur); put("showKeyboard", s.showKeyboard)
        put("statusBar", s.statusBar); put("clock", s.clock); put("dateLabel", s.dateLabel); put("readReceipt", s.readReceipt)
        put("messages", JSONArray().apply { s.messages.forEach { put(messageToJson(it)) } })
    }

    private fun applyWa(o: JSONObject) {
        val s = WhatsAppStore
        s.status = o.optString("status", "online"); s.stamp = o.optString("stamp", "9:41")
        s.avatarUri = if (o.isNull("avatarUri")) null else o.optString("avatarUri").ifEmpty { null }
        s.wallpaper = o.optLong("wallpaper", 0xFFE5DDD5)
        s.sentColor = o.optLong("sentColor", 0xFFDCF8C6)
        s.receivedColor = o.optLong("receivedColor", 0xFFFFFFFF)
        s.sentTextColor = o.optLong("sentTextColor", 0xFF111B21)
        s.receivedTextColor = o.optLong("receivedTextColor", 0xFF111B21)
        s.font = enumOr(o.optString("font"), NoteFont.PRINT)
        s.textScale = o.optDouble("textScale", 1.0).toFloat()
        s.typeSpeed = o.optDouble("typeSpeed", 1.0).toFloat(); s.pacing = o.optDouble("pacing", 0.3).toFloat()
        s.keySound = enumOr(o.optString("keySound"), SoundProfile.SOFT)
        s.sendSound = o.optString("sendSound", "whoosh"); s.receiveSound = o.optString("receiveSound", "tritone")
        s.msgGap = o.optInt("msgGap", 620); s.typingDur = o.optInt("typingDur", 1300); s.showKeyboard = o.optBoolean("showKeyboard", true)
        s.statusBar = o.optBoolean("statusBar", true); s.clock = o.optString("clock", "9:41")
        s.dateLabel = o.optString("dateLabel", "TODAY"); s.readReceipt = o.optBoolean("readReceipt", true)
        s.setAll(o.optString("contactName", "Sam"), messagesFromJson(o.optJSONArray("messages")))
    }

    // ---------- Email ----------

    private fun emailToJson() = JSONObject().apply {
        val s = EmailStore
        put("account", s.account); put("from", s.from); put("to", s.to); put("cc", s.cc); put("showCc", s.showCc)
        put("subject", s.subject); put("body", s.body)
        put("accent", s.accent); put("dark", s.dark); put("textScale", s.textScale.toDouble()); put("font", s.font.name)
        put("sidebar", s.sidebar); put("composeCenter", s.composeCenter); put("attachment", s.attachment)
        put("typeSpeed", s.typeSpeed.toDouble()); put("keySound", s.keySound.name); put("soundsOn", s.soundsOn)
        put("inbox", JSONArray().apply {
            s.inbox.forEach { put(JSONObject().put("from", it.from).put("subject", it.subject).put("snippet", it.snippet).put("time", it.time).put("unread", it.unread)) }
        })
    }

    private fun applyEmail(o: JSONObject) {
        val s = EmailStore
        s.account = o.optString("account", "you@gmail.com"); s.from = o.optString("from", "You")
        s.to = o.optString("to", "team@company.com"); s.cc = o.optString("cc", ""); s.showCc = o.optBoolean("showCc", false)
        s.subject = o.optString("subject", "Project update"); s.body = o.optString("body", s.body)
        s.accent = o.optLong("accent", 0xFF0B57D0)
        s.dark = o.optBoolean("dark", true); s.textScale = o.optDouble("textScale", 1.3).toFloat()
        s.font = enumOr(o.optString("font"), NoteFont.PRINT)
        s.sidebar = o.optBoolean("sidebar", true); s.composeCenter = o.optBoolean("composeCenter", false)
        s.attachment = o.optString("attachment", "")
        s.typeSpeed = o.optDouble("typeSpeed", 1.0).toFloat(); s.keySound = enumOr(o.optString("keySound"), SoundProfile.MECHANICAL)
        s.soundsOn = o.optBoolean("soundsOn", true)
        o.optJSONArray("inbox")?.let { arr ->
            s.inbox.clear()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                s.inbox.add(InboxRow(r.optString("from"), r.optString("subject"), r.optString("snippet"), r.optString("time"), r.optBoolean("unread")))
            }
        }
    }

    // ---------- Lists ----------

    private fun listsToJson() = JSONObject().apply {
        val s = ListsStore
        put("heading", s.heading); put("accent", s.accent); put("textScale", s.textScale.toDouble())
        put("typeSpeed", s.typeSpeed.toDouble()); put("pacing", s.pacing.toDouble()); put("keySound", s.keySound.name); put("cardGap", s.cardGap)
        put("items", JSONArray().apply {
            s.items.forEach {
                put(
                    JSONObject().put("id", it.id).put("type", it.type.name).put("rank", it.rank).put("title", it.title)
                        .put("text", it.text).put("score", it.score).put("tier", it.tier).put("badge", it.badge)
                        .put("imageUri", it.imageUri ?: JSONObject.NULL).put("duration", it.duration)
                        .put("start", it.start.name).put("startDelay", it.startDelay).put("enter", it.enter.name)
                        .put("hold", it.hold).put("exit", it.exit.name),
                )
            }
        })
    }

    private fun applyLists(o: JSONObject) {
        val s = ListsStore
        s.accent = o.optLong("accent", 0xFF5B8DEF); s.textScale = o.optDouble("textScale", 1.0).toFloat()
        s.typeSpeed = o.optDouble("typeSpeed", 1.0).toFloat(); s.pacing = o.optDouble("pacing", 0.3).toFloat()
        s.keySound = enumOr(o.optString("keySound"), SoundProfile.MECHANICAL); s.cardGap = o.optInt("cardGap", 280)
        val arr = o.optJSONArray("items")
        val list = if (arr == null) emptyList() else (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            Block(
                m.optLong("id"), enumOr(m.optString("type"), BlockType.CARD), m.optString("rank"), m.optString("title"),
                m.optString("text"), m.optString("score"), m.optString("tier"), m.optString("badge"),
                if (m.isNull("imageUri")) null else m.optString("imageUri").ifEmpty { null }, m.optString("duration"),
                enumOr(m.optString("start"), StartMode.AFTER), m.optInt("startDelay"), enumOr(m.optString("enter"), Trans.FADE),
                m.optInt("hold"), enumOr(m.optString("exit"), Trans.FADE),
            )
        }
        s.setAll(o.optString("heading", "Editing tricks, ranked"), list)
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        try { if (name.isNullOrEmpty()) default else enumValueOf(name) } catch (_: Throwable) { default }
}
