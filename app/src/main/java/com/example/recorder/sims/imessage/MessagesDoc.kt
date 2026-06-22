package com.example.recorder.sims.imessage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile
import com.example.recorder.sims.chat.ChatNotif
import com.example.recorder.sims.notes.NoteFont

/** One chat message. [fromMe] = a sent (blue, right) bubble; else received (gray, left). */
data class Message(
    val id: Long,
    val text: String = "",
    val fromMe: Boolean = true,
    val imageUri: String? = null, // optional image attachment
    val reaction: String? = null, // optional tapback emoji
    val delayMs: Int = 0,         // extra pause before this message
)

/** The iMessage conversation document — contact + style + ordered messages. */
object MessagesStore {
    val messages = mutableStateListOf<Message>()
    var contactName by mutableStateOf("Alex"); private set
    var avatarUri by mutableStateOf<String?>(null)
    var sentColor by mutableStateOf(0xFF0A84FFL)
    var receivedColor by mutableStateOf(0xFFE9E9EBL)
    var sentTextColor by mutableStateOf(0xFFFFFFFFL)
    var receivedTextColor by mutableStateOf(0xFF000000L)
    var font by mutableStateOf(NoteFont.PRINT)
    var textScale by mutableStateOf(1f)
    // typing + sound (humanized defaults — soft keys, natural pacing)
    var typeSpeed by mutableStateOf(0.7f)
    var pacing by mutableStateOf(0.6f)
    var keySound by mutableStateOf(SoundProfile.KEYBOARD)
    var sendSound by mutableStateOf("whoosh")
    var receiveSound by mutableStateOf("tritone")
    var msgGap by mutableStateOf(620)       // pause after each message (ms)
    var typingDur by mutableStateOf(1300)   // received typing-indicator duration (ms)
    var showKeyboard by mutableStateOf(true)
    var statusBar by mutableStateOf(true)
    var clock by mutableStateOf("9:41")
    var dateLabel by mutableStateOf("iMessage • Today")
    var readReceipt by mutableStateOf(true)
    // push notifications from other chats that pop in during the take
    val notifs = mutableStateListOf<ChatNotif>()

    var selectedId by mutableStateOf<Long?>(null)
        private set

    private var counter = 1L

    init { reset() }

    fun setContact(name: String) { contactName = name }
    fun select(id: Long) { selectedId = id }
    fun selected(): Message? = messages.firstOrNull { it.id == selectedId }

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
        contactName = "Alex"
        avatarUri = null
        sentColor = 0xFF0A84FFL; receivedColor = 0xFFE9E9EBL
        sentTextColor = 0xFFFFFFFFL; receivedTextColor = 0xFF000000L
        font = NoteFont.PRINT; textScale = 1.12f
        typeSpeed = 0.7f; pacing = 0.6f; keySound = SoundProfile.KEYBOARD
        sendSound = "whoosh"; receiveSound = "tritone"; msgGap = 620; typingDur = 1300; showKeyboard = true
        statusBar = true; clock = "9:41"; dateLabel = "iMessage • Today"; readReceipt = true
        messages.clear(); counter = 1L
        messages.add(Message(counter++, "hey, you up?", false))
        messages.add(Message(counter++, "yeah, can't sleep", true))
        messages.add(Message(counter++, "same. wanna grab coffee tmrw?", false))
        messages.add(Message(counter++, "for sure. 9am at the usual spot", true))
        selectedId = messages.firstOrNull()?.id
        notifs.clear()
        notifs.add(ChatNotif("Mom", "Call me when you're free ❤️", 1, "tritone"))
    }
}
