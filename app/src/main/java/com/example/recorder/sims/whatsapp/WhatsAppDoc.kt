package com.example.recorder.sims.whatsapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile
import com.example.recorder.sims.chat.ChatNotif
import com.example.recorder.sims.imessage.Message
import com.example.recorder.sims.notes.NoteFont

/** The WhatsApp conversation document — contact + style + ordered messages (reuses [Message]). */
object WhatsAppStore {
    val messages = mutableStateListOf<Message>()
    var contactName by mutableStateOf("Sam"); private set
    var status by mutableStateOf("online")
    var avatarUri by mutableStateOf<String?>(null)
    var wallpaper by mutableStateOf(0xFFE5DDD5L)      // chat background (classic beige)
    var sentColor by mutableStateOf(0xFFDCF8C6L)      // light green
    var receivedColor by mutableStateOf(0xFFFFFFFFL)
    var sentTextColor by mutableStateOf(0xFF111B21L)
    var receivedTextColor by mutableStateOf(0xFF111B21L)
    var font by mutableStateOf(NoteFont.PRINT)
    var textScale by mutableStateOf(1f)
    // typing + sound (humanized defaults — soft keys, natural pacing)
    var typeSpeed by mutableStateOf(0.7f)
    var pacing by mutableStateOf(0.6f)
    var keySound by mutableStateOf(SoundProfile.KEYBOARD)
    var sendSound by mutableStateOf("pop")
    var receiveSound by mutableStateOf("pop")
    var msgGap by mutableStateOf(620)
    var typingDur by mutableStateOf(1300)
    var showKeyboard by mutableStateOf(true)
    var stamp by mutableStateOf("9:41")               // timestamp shown on bubbles
    var statusBar by mutableStateOf(true)
    var clock by mutableStateOf("9:41")
    var dateLabel by mutableStateOf("TODAY")
    var readReceipt by mutableStateOf(true)           // blue vs gray ticks
    val notifs = mutableStateListOf<ChatNotif>()

    var selectedId by mutableStateOf<Long?>(null)
        private set

    private var counter = 1L

    init { reset() }

    fun setContact(name: String) { contactName = name }
    fun select(id: Long) { selectedId = id }

    fun add(fromMe: Boolean = true) {
        val m = Message(counter++, "", fromMe)
        messages.add(m); selectedId = m.id
    }

    fun update(id: Long, transform: (Message) -> Message) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = transform(messages[i])
    }

    fun remove(id: Long) {
        val i = messages.indexOfFirst { it.id == id }
        if (i < 0) return
        messages.removeAt(i)
        if (selectedId == id) selectedId = messages.getOrNull(i)?.id ?: messages.lastOrNull()?.id
    }

    fun move(id: Long, delta: Int) {
        val i = messages.indexOfFirst { it.id == id }
        if (i < 0) return
        val j = (i + delta).coerceIn(0, messages.size - 1)
        if (i != j) messages.add(j, messages.removeAt(i))
    }

    fun setAll(name: String, list: List<Message>) {
        contactName = name
        messages.clear(); messages.addAll(list)
        counter = (list.maxOfOrNull { it.id } ?: 0L) + 1
        selectedId = messages.firstOrNull()?.id
    }

    fun reset() {
        contactName = "Sam"; status = "online"; avatarUri = null
        wallpaper = 0xFFE5DDD5L
        sentColor = 0xFFDCF8C6L; receivedColor = 0xFFFFFFFFL
        sentTextColor = 0xFF111B21L; receivedTextColor = 0xFF111B21L
        font = NoteFont.PRINT; textScale = 1.12f; stamp = "9:41"
        typeSpeed = 0.7f; pacing = 0.6f; keySound = SoundProfile.KEYBOARD
        sendSound = "pop"; receiveSound = "pop"; msgGap = 620; typingDur = 1300; showKeyboard = true
        statusBar = true; clock = "9:41"; dateLabel = "TODAY"; readReceipt = true
        messages.clear(); counter = 1L
        messages.add(Message(counter++, "did you send the file?", false))
        messages.add(Message(counter++, "yep, just emailed it 👍", true))
        messages.add(Message(counter++, "got it, thanks!", false))
        messages.add(Message(counter++, "lmk if anything's missing", true))
        selectedId = messages.firstOrNull()?.id
        notifs.clear()
        notifs.add(ChatNotif("Work group", "Standup in 5 — joining?", 1, "pop"))
    }
}
