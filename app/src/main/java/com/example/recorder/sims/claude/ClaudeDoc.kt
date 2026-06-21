package com.example.recorder.sims.claude

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile

/**
 * The Claude Code CLI sim — a terminal that launches `claude`, fades in the
 * welcome box, types the user's prompt, ticks a thinking spinner, then streams
 * the reply. Port of src/sims/claude.
 */
object ClaudeStore {
    var model by mutableStateOf("Claude Opus 4.8")
    var name by mutableStateOf("Dev")
    var account by mutableStateOf("claude.dev@gmx.ch")
    var cwd by mutableStateOf("C:\\Users\\atjul")
    var version by mutableStateOf("2.1.180")
    var thinkMs by mutableStateOf(2800)
    var tokenTarget by mutableStateOf(2400) // tokens counted up to during the thinking beat
    var verb by mutableStateOf("")        // blank = cycle through the whimsical verbs
    var prompt by mutableStateOf("what should I build next?")
    var reply by mutableStateOf(
        "Ship the thing you'd demo first. Pick the smallest version that proves the idea, record a 20-second take of it working, and let that pull the rest of the roadmap forward.",
    )
    // typing + sound
    var typeSpeed by mutableStateOf(0.85f)
    var pacing by mutableStateOf(0.5f)
    var keySound by mutableStateOf(SoundProfile.KEYBOARD)
    var streamSpeed by mutableStateOf(1f)  // reply stream speed
    var welcomeBox by mutableStateOf(true)

    fun reset() {
        model = "Claude Opus 4.8"; name = "Dev"; account = "claude.dev@gmx.ch"
        cwd = "C:\\Users\\atjul"; version = "2.1.180"; thinkMs = 2800; tokenTarget = 2400; verb = ""
        prompt = "what should I build next?"
        reply = "Ship the thing you'd demo first. Pick the smallest version that proves the idea, record a 20-second take of it working, and let that pull the rest of the roadmap forward."
        typeSpeed = 0.85f; pacing = 0.5f; keySound = SoundProfile.KEYBOARD; streamSpeed = 1f; welcomeBox = true
    }
}
