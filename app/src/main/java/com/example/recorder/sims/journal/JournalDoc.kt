package com.example.recorder.sims.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile
import com.example.recorder.sims.notes.NoteFont

/**
 * The Journal sim — a pencil scratches an entry across a ruled page, line by
 * line, in a loose hand, with a date in the corner. Port of src/sims/journal.
 */
object JournalStore {
    var text by mutableStateOf(
        "Today I [o]shipped[/o] it!\n\nThe little app that\ntypes itself and\nrecords the take.\n---\n[u]Small ideas[/u] become\nthe ones worth\n[x]keening[/x] keeping.\n\nMore tomorrow.",
    )
    var date by mutableStateOf("Tuesday")
    var font by mutableStateOf(NoteFont.HANDWRITING)
    var ink by mutableStateOf(0xFF2B2620L)
    var paper by mutableStateOf(0xFFF4ECE0L)
    var textScale by mutableStateOf(1f)
    // human-hand imperfection: per-letter crookedness, size variation + ink weight
    var messiness by mutableStateOf(0.5f)
    // typing + sound
    var typeSpeed by mutableStateOf(0.8f)
    var pacing by mutableStateOf(0.6f)
    var keySound by mutableStateOf(SoundProfile.PENCIL)

    fun reset() {
        text = "Today I [o]shipped[/o] it!\n\nThe little app that\ntypes itself and\nrecords the take.\n---\n[u]Small ideas[/u] become\nthe ones worth\n[x]keening[/x] keeping.\n\nMore tomorrow."
        date = "Tuesday"; font = NoteFont.HANDWRITING; ink = 0xFF2B2620L; paper = 0xFFF4ECE0L; textScale = 1f
        messiness = 0.5f; typeSpeed = 0.8f; pacing = 0.6f; keySound = SoundProfile.STYLUS
    }
}
