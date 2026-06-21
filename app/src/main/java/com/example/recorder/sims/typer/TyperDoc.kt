package com.example.recorder.sims.typer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile

/** Typer — one block of large centred text typed onto a calm backdrop. Each
 *  "card" types, holds, then clears for the next (great for quotes / openers). */
object TyperStore {
    val cards = mutableStateListOf<String>()
    var textScale by mutableStateOf(1f)
    var color by mutableStateOf(0xFFF4F5F7L)
    // typing + sound
    var typeSpeed by mutableStateOf(0.8f)
    var pacing by mutableStateOf(0.4f)
    var keySound by mutableStateOf(SoundProfile.SOFT)
    var holdMs by mutableStateOf(1500)   // hold each finished card before clearing

    init { reset() }

    fun add() { cards.add("New line."); }
    fun update(i: Int, s: String) { if (i in cards.indices) cards[i] = s }
    fun remove(i: Int) { if (i in cards.indices) cards.removeAt(i) }
    fun move(i: Int, d: Int) { val j = (i + d).coerceIn(0, cards.size - 1); if (i in cards.indices && i != j) cards.add(j, cards.removeAt(i)) }
    fun setAll(list: List<String>) { cards.clear(); cards.addAll(list) }

    fun reset() {
        textScale = 1f; color = 0xFFF4F5F7L; typeSpeed = 0.8f; pacing = 0.4f; keySound = SoundProfile.SOFT; holdMs = 1500
        cards.clear()
        cards.add("Stop scrolling.\nRead this.")
        cards.add("Your next idea\nstarts here.")
    }
}
