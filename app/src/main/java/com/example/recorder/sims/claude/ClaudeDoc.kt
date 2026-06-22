package com.example.recorder.sims.claude

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile

/** One exchange in the conversation: the user's prompt and Claude's reply. */
class ClaudeTurn(prompt0: String, reply0: String) {
    var prompt by mutableStateOf(prompt0)
    var reply by mutableStateOf(reply0)
}

/**
 * The Claude Code CLI sim — launches `claude`, fades in the welcome box, then runs a
 * conversation: types a prompt, thinks, streams the reply, and the user can ask follow-ups
 * that each get their own thinking + answer.
 */
object ClaudeStore {
    var model by mutableStateOf("Claude Opus 4.8")
    var name by mutableStateOf("Dev")
    var account by mutableStateOf("claude.dev@gmx.ch")
    var cwd by mutableStateOf("C:\\Users\\atjul")
    var version by mutableStateOf("2.1.180")
    var thinkMs by mutableStateOf(2800)
    var tokenTarget by mutableStateOf(2400) // tokens counted up to during the thinking beat
    var verb by mutableStateOf("")          // blank = cycle through the whimsical verbs

    // the conversation — first turn plus any follow-ups
    val turns = mutableStateListOf<ClaudeTurn>()

    // typing + sound
    var typeSpeed by mutableStateOf(0.85f)
    var pacing by mutableStateOf(0.5f)
    var keySound by mutableStateOf(SoundProfile.KEYBOARD)
    var streamSpeed by mutableStateOf(1f)  // reply stream speed
    var welcomeBox by mutableStateOf(true)

    init { reset() }

    fun addTurn() { turns.add(ClaudeTurn("and then?", "Then ship it and watch what people actually do — that tells you the next thing to build.")) }
    fun removeTurn(i: Int) { if (turns.size > 1 && i in turns.indices) turns.removeAt(i) }

    fun reset() {
        model = "Claude Opus 4.8"; name = "Dev"; account = "claude.dev@gmx.ch"
        cwd = "C:\\Users\\atjul"; version = "2.1.180"; thinkMs = 2800; tokenTarget = 2400; verb = ""
        typeSpeed = 0.85f; pacing = 0.5f; keySound = SoundProfile.KEYBOARD; streamSpeed = 1f; welcomeBox = true
        turns.clear()
        turns.add(ClaudeTurn("what should I build next?", "Ship the thing you'd demo first. Pick the smallest version that proves the idea, record a 20-second take of it working, and let that pull the rest of the roadmap forward."))
        turns.add(ClaudeTurn("how do I keep it small?", "Cut anything that wouldn't show up in that 20-second demo. If a feature doesn't earn its place on screen, it waits for v2."))
    }
}
