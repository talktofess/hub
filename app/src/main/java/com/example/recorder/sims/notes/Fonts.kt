package com.example.recorder.sims.notes

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.example.recorder.R

/** Google Fonts downloadable provider (verified by the bundled cert hashes). */
private val GF_PROVIDER = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

/** A downloadable Google font family (loads on first use; cached after). */
private fun gfont(name: String): FontFamily =
    FontFamily(androidx.compose.ui.text.googlefonts.Font(GoogleFont(name), GF_PROVIDER))

/**
 * Per-note typefaces. The first block is bundled in res/font (always available);
 * the "✦" block streams from Google Fonts on first use; the last three are the
 * built-in system families.
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
    // ✦ downloadable Google Fonts (handwriting / script)
    CAVEAT("Caveat ✦", gfont("Caveat")),
    DANCING("Dancing Script ✦", gfont("Dancing Script")),
    SHADOWS("Shadows ✦", gfont("Shadows Into Light")),
    HOMEMADE("Homemade Apple ✦", gfont("Homemade Apple")),
    REENIE("Reenie Beanie ✦", gfont("Reenie Beanie")),
    ROCKSALT("Rock Salt ✦", gfont("Rock Salt")),
    GLORIA("Gloria ✦", gfont("Gloria Hallelujah")),
    COVERED("Covered ✦", gfont("Covered By Your Grace")),
    JUSTHAND("Just Another Hand ✦", gfont("Just Another Hand")),
    NANUM("Nanum Pen ✦", gfont("Nanum Pen Script")),
    GOCHI("Gochi Hand ✦", gfont("Gochi Hand")),
    SCHOOLBELL("Schoolbell ✦", gfont("Schoolbell")),
    COMINGSOON("Coming Soon ✦", gfont("Coming Soon")),
    CAVEATBRUSH("Caveat Brush ✦", gfont("Caveat Brush")),
    NEUCHA("Neucha ✦", gfont("Neucha")),
    SATISFY("Satisfy ✦", gfont("Satisfy")),
    GREATVIBES("Great Vibes ✦", gfont("Great Vibes")),
    PRINT("Print", FontFamily.SansSerif),
    SERIF("Serif", FontFamily.Serif),
    MONO("Mono", FontFamily.Monospace),
}
