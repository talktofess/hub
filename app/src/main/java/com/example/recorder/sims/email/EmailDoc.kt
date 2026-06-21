package com.example.recorder.sims.email

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile
import com.example.recorder.sims.notes.NoteFont

/** One row in the desktop inbox list (behind the compose popup). */
data class InboxRow(
    val from: String,
    val subject: String,
    val snippet: String,
    val time: String,
    val unread: Boolean,
)

/**
 * The Email (desktop Gmail) document — the full web app: account chrome, an inbox,
 * and the compose popup the typewriter fills (To / Cc / Subject / Body → Send).
 * Renders at 1920×1080 like the original hub.html OBS layout.
 */
object EmailStore {
    var account by mutableStateOf("you@gmail.com")
    var from by mutableStateOf("You")
    var to by mutableStateOf("team@company.com")
    var cc by mutableStateOf("")
    var showCc by mutableStateOf(false)
    var subject by mutableStateOf("Project update")
    var body by mutableStateOf(
        "Hi team,\n\nQuick update — the new build is ready for review and everything's on track for Friday.\n\nLet me know if you spot anything.\n\nThanks,",
    )
    var accent by mutableStateOf(0xFF0B57D0L)   // Gmail blue
    var dark by mutableStateOf(true)
    var textScale by mutableStateOf(1.3f)       // enlarges all Gmail text for visibility
    var font by mutableStateOf(NoteFont.PRINT)
    var sidebar by mutableStateOf(true)
    var composeCenter by mutableStateOf(false)  // br vs center dock
    var attachment by mutableStateOf("")        // optional attachment filename chip
    // typing + sound
    var typeSpeed by mutableStateOf(0.8f)
    var keySound by mutableStateOf(SoundProfile.MECHANICAL)
    var soundsOn by mutableStateOf(true)        // send chime

    val inbox = mutableStateListOf<InboxRow>()

    init { reset() }

    fun reset() {
        account = "you@gmail.com"; from = "You"
        to = "team@company.com"; cc = ""; showCc = false
        subject = "Project update"
        body = "Hi team,\n\nQuick update — the new build is ready for review and everything's on track for Friday.\n\nLet me know if you spot anything.\n\nThanks,"
        accent = 0xFF0B57D0L; dark = true; textScale = 1.3f; font = NoteFont.PRINT; sidebar = true; composeCenter = false
        attachment = ""; typeSpeed = 0.8f; keySound = SoundProfile.MECHANICAL; soundsOn = true
        inbox.clear(); inbox.addAll(DEFAULT_INBOX)
    }

    fun addRow() = inbox.add(InboxRow("Sender", "Subject line", "A short preview of the message…", "9:00 AM", false))
    fun removeRow(i: Int) { if (i in inbox.indices) inbox.removeAt(i) }
    fun updateRow(i: Int, r: InboxRow) { if (i in inbox.indices) inbox[i] = r }
}

val DEFAULT_INBOX = listOf(
    InboxRow("GitHub", "Your CI run passed", "All checks have passed on main — deploy is green.", "9:02 AM", true),
    InboxRow("Linear", "ENG-482 moved to In Review", "Sarah moved “OBS recording pipeline” to In Review.", "8:41 AM", true),
    InboxRow("Figma", "Maya commented on Hub v2", "“Love the new compose flow — can we tighten the…”", "Yesterday", false),
    InboxRow("Calendar", "Demo with Meridian — Thu 2pm", "Reminder: 30 minute walkthrough of the rollout plan.", "Yesterday", false),
    InboxRow("Vercel", "Deployment ready", "sim-hub-2 deployed to production in 38s.", "Mon", false),
)
