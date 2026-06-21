package com.example.recorder.sims.typer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile

/** Typer — a terminal prompt that types commands one after another, like a real
 *  PowerShell session: each card is a command typed at the prompt, then it scrolls
 *  up as the next prompt appears. */
object TyperStore {
    val cards = mutableStateListOf<String>()   // each = one command line
    var prompt by mutableStateOf("PS C:\\Users\\dev> ")
    var textScale by mutableStateOf(1f)
    var color by mutableStateOf(0xFFEAEAEAL)    // terminal text colour
    var bg by mutableStateOf(0xFF0C0C0CL)       // terminal background
    // typing + sound
    var typeSpeed by mutableStateOf(1f)
    var pacing by mutableStateOf(0.4f)
    var keySound by mutableStateOf(SoundProfile.KEYBOARD)
    var holdMs by mutableStateOf(850)           // pause after a command before the next prompt

    init { reset() }

    fun add() { cards.add("echo \"hello\"") }
    fun update(i: Int, s: String) { if (i in cards.indices) cards[i] = s }
    fun remove(i: Int) { if (i in cards.indices) cards.removeAt(i) }
    fun move(i: Int, d: Int) { val j = (i + d).coerceIn(0, cards.size - 1); if (i in cards.indices && i != j) cards.add(j, cards.removeAt(i)) }
    fun setAll(list: List<String>) { cards.clear(); cards.addAll(list) }

    fun reset() {
        prompt = "PS C:\\Users\\dev> "; textScale = 1f; color = 0xFFEAEAEAL; bg = 0xFF0C0C0CL
        typeSpeed = 1f; pacing = 0.4f; keySound = SoundProfile.KEYBOARD; holdMs = 850
        cards.clear()
        cards.add("cd C:\\projects\\sim-hub")
        cards.add("git status")
        cards.add("git commit -m \"ship it\"")
        cards.add("git push origin main")
    }
}
