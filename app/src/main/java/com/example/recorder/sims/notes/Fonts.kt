package com.example.recorder.sims.notes

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.recorder.R

/**
 * Per-note typefaces. Most are real fonts bundled in res/font (Google Fonts);
 * the last three are the built-in system families.
 */
enum class NoteFont(val label: String, val family: FontFamily) {
    HANDWRITING("Handwriting", FontFamily(Font(R.font.patrick_hand))),
    ARCHITECT("Architect", FontFamily(Font(R.font.architects_daughter))),
    LOOSE("Loose hand", FontFamily(Font(R.font.indie_flower))),
    BRUSH("Brush", FontFamily(Font(R.font.kalam))),
    MARKER("Marker", FontFamily(Font(R.font.permanent_marker))),
    SCRIPT("Script", FontFamily(Font(R.font.sacramento))),
    ELEGANT("Elegant", FontFamily(Font(R.font.pacifico))),
    TYPEWRITER("Typewriter", FontFamily(Font(R.font.special_elite))),
    COURIER("Courier", FontFamily(Font(R.font.courier_prime))),
    PRINT("Print", FontFamily.SansSerif),
    SERIF("Serif", FontFamily.Serif),
    MONO("Mono", FontFamily.Monospace),
}
