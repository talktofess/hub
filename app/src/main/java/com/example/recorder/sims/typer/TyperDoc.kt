package com.example.recorder.sims.typer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile

/** One terminal entry: a command that gets typed, then its printed result. */
data class TermCmd(val command: String, val output: String = "")

/** Typer — a terminal that types a command, prints its result, then moves to the
 *  next prompt, like a real PowerShell session. */
object TyperStore {
    val cards = mutableStateListOf<TermCmd>()
    var prompt by mutableStateOf("PS C:\\Users\\dev> ")
    var textScale by mutableStateOf(1f)
    var color by mutableStateOf(0xFFEAEAEAL)    // terminal text colour
    var bg by mutableStateOf(0xFF0C0C0CL)       // terminal background
    // typing + sound
    var typeSpeed by mutableStateOf(1f)
    var pacing by mutableStateOf(0.4f)
    var keySound by mutableStateOf(SoundProfile.KEYBOARD)
    var holdMs by mutableStateOf(650)           // pause after a command / its result

    init { reset() }

    fun add() { cards.add(TermCmd("echo \"hello\"", "hello")) }
    fun updateCommand(i: Int, s: String) { if (i in cards.indices) cards[i] = cards[i].copy(command = s) }
    fun updateOutput(i: Int, s: String) { if (i in cards.indices) cards[i] = cards[i].copy(output = s) }
    fun remove(i: Int) { if (i in cards.indices) cards.removeAt(i) }
    fun move(i: Int, d: Int) { val j = (i + d).coerceIn(0, cards.size - 1); if (i in cards.indices && i != j) cards.add(j, cards.removeAt(i)) }
    fun setAll(list: List<TermCmd>) { cards.clear(); cards.addAll(list) }

    fun reset() {
        prompt = "PS C:\\Users\\dev> "; textScale = 1f; color = 0xFFEAEAEAL; bg = 0xFF0C0C0CL
        typeSpeed = 1f; pacing = 0.4f; keySound = SoundProfile.KEYBOARD; holdMs = 650
        cards.clear()
        cards.add(TermCmd("cd C:\\projects\\sim-hub", ""))
        cards.add(TermCmd("git status", "On branch main\nworking tree clean"))
        cards.add(TermCmd("npm run build", "vite v5.0.0  building...\nbundling 48 modules\n@bar\noptimizing assets\n@bar\n✓ built in 1.24s"))
        cards.add(TermCmd("./deploy.sh --prod", "connecting to edge ord1...\nuploading build\n@bar\ninvalidating cache\n@bar\n✓ live → sim-hub.app"))
    }
}
