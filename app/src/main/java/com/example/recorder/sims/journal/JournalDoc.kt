package com.example.recorder.sims.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile
import com.example.recorder.sims.notes.NoteFont

/** One piece of writing placed freely on the surface: its text, where it sits (as a
 *  fraction of the surface), how it looks (font / colour / size / rotation), and WHEN it is
 *  written (order in the sequence). Everything is observable so the editor updates live. */
class JElement(
    val id: Long,
    text0: String,
    x0: Float,
    y0: Float,
    font0: NoteFont,
    color0: Long,
    size0: Float,
    rot0: Float,
    order0: Int,
) {
    var text by mutableStateOf(text0)
    var xPct by mutableStateOf(x0)          // centre x, 0..1 of the surface
    var yPct by mutableStateOf(y0)          // centre y, 0..1 of the surface
    var font by mutableStateOf(font0)
    var color by mutableStateOf(color0)
    var size by mutableStateOf(size0)       // size multiplier
    var rotation by mutableStateOf(rot0)    // degrees (90 = sideways)
    var order by mutableStateOf(order0)     // when it's written (lower = earlier)
}

/** The Journal is now a free canvas: drop text anywhere, style each piece, choose the order
 *  it's written, and shape the surface itself. */
object JournalStore {
    val elements = mutableStateListOf<JElement>()
    var nextId = 1L

    // surface
    var fill by mutableStateOf(true)            // fill the whole screen vs a centred surface
    var widthPct by mutableStateOf(0.78f)       // surface width (fraction of screen) when not filling
    var paper by mutableStateOf(0xFFFCFCFEL)
    var backdrop by mutableStateOf(0xFF0E0F12L) // behind a centred surface

    // defaults applied to new pieces
    var defFont by mutableStateOf(NoteFont.MARKER)
    var defColor by mutableStateOf(0xFF1E2026L)

    // writing
    var typeSpeed by mutableStateOf(0.85f)
    var keySound by mutableStateOf(SoundProfile.STYLUS)

    init { reset() }

    fun add(x: Float, y: Float): JElement {
        val maxOrder = (elements.maxOfOrNull { it.order } ?: -1) + 1
        val el = JElement(nextId++, "text", x.coerceIn(0.04f, 0.96f), y.coerceIn(0.03f, 0.97f), defFont, defColor, 1f, 0f, maxOrder)
        elements.add(el)
        return el
    }

    fun remove(id: Long) { elements.removeAll { it.id == id } }

    fun reset() {
        elements.clear(); nextId = 1L
        fill = true; widthPct = 0.78f; paper = 0xFFFCFCFEL; backdrop = 0xFF0E0F12L
        defFont = NoteFont.MARKER; defColor = 0xFF1E2026L
        typeSpeed = 0.85f; keySound = SoundProfile.STYLUS
        // a starter layout that shows the idea
        elements.add(JElement(nextId++, "Today", 0.30f, 0.13f, NoteFont.MARKER, 0xFF1E2026L, 1.7f, -3f, 0))
        elements.add(JElement(nextId++, "shipped it!", 0.55f, 0.30f, NoteFont.MARKER, 0xFF2B6CB0L, 1.15f, 2f, 1))
        elements.add(JElement(nextId++, "side note", 0.84f, 0.55f, NoteFont.MARKER, 0xFFC0392BL, 0.85f, 90f, 2))
        elements.add(JElement(nextId++, "more tomorrow", 0.36f, 0.84f, NoteFont.MARKER, 0xFF1E2026L, 1f, 5f, 3))
    }
}
