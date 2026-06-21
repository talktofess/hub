package com.example.recorder.model

import androidx.compose.ui.graphics.Color

/**
 * Universal settings — global across every sim. Port of src/recording/settings.ts.
 * The active sim's script plus these are everything a take needs.
 */

enum class SoundProfile(val id: String, val label: String) {
    MECHANICAL("mechanical", "Mechanical (clicky)"),
    BLUE("blue", "Blue switch (sharp)"),
    TYPEWRITER("typewriter", "Typewriter"),
    VINTAGE("vintage", "Vintage keyboard"),
    TACTILE("tactile", "Tactile (thock)"),
    SOFT("soft", "Soft (laptop)"),
    KEYBOARD("keyboard", "Phone keyboard (tap)"),
    MUSH("mush", "Marshmallow (muted)"),
    BUBBLE("bubble", "Bubble (poppy)"),
    PENCIL("pencil", "Pencil (graphite)"),
    CREAMY("creamy", "Creamy (deep thock)"),
    CLACKY("clacky", "Clacky (sharp click)"),
    GLASS("glass", "Glass (ping)"),
    THUD("thud", "Thud (spacebar)"),
    PEN("pen", "Ballpoint pen"),
    FOUNTAIN("fountain", "Fountain pen (wet ink)"),
    GEL("gel", "Gel pen (smooth)"),
    MARKER("marker", "Marker (squeaky)"),
    FELT("felt", "Felt-tip (soft)"),
    CHALK("chalk", "Chalk (dusty)"),
    CRAYON("crayon", "Crayon (waxy)"),
    BRUSHPEN("brushpen", "Brush pen (inky)"),
    NONE("none", "No sound");

    companion object {
        fun from(id: String?): SoundProfile = entries.firstOrNull { it.id == id } ?: MECHANICAL
    }
}

enum class CaretStyle { BAR, BLOCK, UNDERLINE }
enum class ThemeMode { AUTO, LIGHT, DARK }

enum class BgKind { NONE, IMAGE, VIDEO }
enum class BgScale(val label: String) {
    COVER("Cover (fill, crop)"),
    CONTAIN("Contain (whole image)"),
    FILL("Stretch"),
    CENTER("Center (actual size)"),
}

/** Universal background placed behind the sim content (behind the notes). */
data class BgMedia(
    val uri: String? = null,
    val kind: BgKind = BgKind.NONE,
    val scale: BgScale = BgScale.COVER,
    val opacity: Float = 1f,
    /** Seconds into the take to fade in; < 0 means always on. */
    val appearAtSec: Float = -1f,
)

data class SpeedPreset(val label: String, val value: Float)
val SPEED_PRESETS = listOf(
    SpeedPreset("Slow", 0.7f),
    SpeedPreset("Natural", 1f),
    SpeedPreset("Fast", 1.5f),
    SpeedPreset("Turbo", 2.2f),
)

/**
 * Immutable settings snapshot. Defaults mirror DEFAULT_SETTINGS in settings.ts.
 * Per-sim settings live in [sim], keyed by sim id (each sim defines its own shape).
 */
data class Settings(
    // sound
    val sound: SoundProfile = SoundProfile.MECHANICAL,
    val liveVolume: Float = 0.85f,   // 0..1 speaker monitor (0 = play silently)
    val recordVolume: Float = 0.85f, // 0..1 keystroke level baked into the recording
    val speed: Float = 1f,       // typing-rate multiplier
    // typing realism
    val startDelay: Int = 600,   // ms before typing begins
    val thinkPauses: Float = 0.5f, // 0..1 hesitation amount
    val jitter: Float = 0.5f,    // 0..1 per-char timing variance
    val autoTypo: Float = 0f,    // 0..0.15 chance/char to fumble + self-correct
    // human pacing arc: 0 = robotic constant speed; higher = slow-start→build→
    // slow-finish + gentle speed "breathing".
    val humanize: Float = 0.4f,
    // explicit counts per take (you choose how many, placed on random words):
    val dwellCount: Int = 1,       // pauses to "think" on a word
    val reconsiderCount: Int = 1,  // type-a-few-letters-then-delete moments
    val loop: Boolean = false,
    val holdEnd: Int = 1200,     // ms to hold the finished take before loop/stop
    // caret
    val showCaret: Boolean = true,
    val caretStyle: CaretStyle = CaretStyle.BAR,
    val caretColor: Color = Color.White,
    val caretBlink: Boolean = true,
    // look & feel
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Color = Color(0xFF5B8DEF),
    val fontScale: Float = 1f,   // 0.8..1.3
    val grain: Boolean = false,
    val vignette: Boolean = false,
    // recording quality — supersample factor (1x = 1080×1920; 2x = 2160×3840).
    // Higher = crisper but heavier; raise it only on a powerful device.
    val recordScale: Float = 1f,
    // universal background (behind the sim content)
    val bg: BgMedia = BgMedia(),
    // background audio bed (music/ambience), mixed into playback + recording
    val bgAudioUri: String? = null,
    val bgAudioVolume: Float = 0.5f,
    // per-sim settings, keyed by sim id
    val sim: Map<String, Map<String, Any?>> = emptyMap(),
) {
    /** Read a per-sim numeric setting (e.g. Notes card width). */
    fun simFloat(simId: String, key: String, default: Float): Float =
        (sim[simId]?.get(key) as? Number)?.toFloat() ?: default

    /** Return a copy with one per-sim numeric setting changed. */
    fun withSimFloat(simId: String, key: String, value: Float): Settings =
        copy(sim = sim + (simId to ((sim[simId] ?: emptyMap()) + (key to value))))
}
