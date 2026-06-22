package com.example.recorder.sims.typewriter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile
import com.example.recorder.sims.notes.NoteFont

/** Typewriter — monospace strikes on a paper sheet; every newline is a carriage
 *  return (bell + the carriage guide swipes home). */
object TypewriterStore {
    var text by mutableStateOf("Dear diary,\n\nit still works.\nthe bell rings.\n\n— me")
    var textScale by mutableStateOf(1f)
    var typeSpeed by mutableStateOf(0.55f)
    var pacing by mutableStateOf(0.35f)
    var keySound by mutableStateOf(SoundProfile.TYPEWRITER)
    var bell by mutableStateOf(true)
    var font by mutableStateOf(NoteFont.TYPEWRITER)   // typewriter typeface
    var ink by mutableStateOf(0xFF241B12L)            // ribbon colour
    var paper by mutableStateOf(0xFFFCF8EEL)          // sheet colour
    var vignette by mutableStateOf(true)              // soft page edges

    fun reset() {
        text = "Dear diary,\n\nit still works.\nthe bell rings.\n\n— me"
        textScale = 1f; typeSpeed = 0.55f; pacing = 0.35f; keySound = SoundProfile.TYPEWRITER; bell = true
        font = NoteFont.TYPEWRITER; ink = 0xFF241B12L; paper = 0xFFFCF8EEL; vignette = true
    }
}
