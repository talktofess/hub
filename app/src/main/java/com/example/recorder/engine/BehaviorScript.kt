package com.example.recorder.engine

/**
 * The "behavior script" — a small text language that controls TYPING RHYTHM
 * (speed, pacing, hesitation, mistakes, per-word mistypes and dwells). Visual
 * styling stays in the Notes builder; this only governs how the typing feels.
 *
 * Lines (`#` starts a comment):
 *   speed 1.2                         # global default
 *   pacing 0.5                        # 0..1 slow-start→build→slow-end arc
 *   hesitation 0.4
 *   mistakes 0.05                     # 0..0.15 random auto-typos
 *   jitter 0.5
 *   start 600                         # ms before typing begins
 *   hold 1200                         # ms held at the end (for loop)
 *   loop on|off
 *   note 2: speed 0.8                 # per-note override (note number, 1-based)
 *   reconsider banana -> bamana hold 1000     # type wrong, hold, then fix (any note)
 *   note 1: reconsider morning -> mprning hold 800
 *   dwell morning 700                 # pause before the word (any note)
 *   note 3: dwell heron 500
 */

data class TimingSpec(
    val speed: Float? = null,
    val pacing: Float? = null,
    val hesitation: Float? = null,
    val mistakes: Float? = null,
    val jitter: Float? = null,
)

data class TakeSpec(val startDelay: Int = 600, val holdEnd: Int = 1200, val loop: Boolean = false)

data class WordDirective(val word: String, val reconsider: Boolean, val wrong: String, val holdMs: Int)

data class ResolvedTiming(
    val speed: Float, val pacing: Float, val hesitation: Float, val mistakes: Float, val jitter: Float,
)

class BehaviorSpec(
    val global: TimingSpec,
    val take: TakeSpec,
    private val perNote: Map<Int, TimingSpec>,
    private val noteWords: Map<Int, List<WordDirective>>, // key 0 = "any note"
) {
    fun timingFor(note: Int): ResolvedTiming {
        val p = perNote[note]
        return ResolvedTiming(
            speed = p?.speed ?: global.speed ?: 1f,
            pacing = p?.pacing ?: global.pacing ?: 0.4f,
            hesitation = p?.hesitation ?: global.hesitation ?: 0.5f,
            mistakes = p?.mistakes ?: global.mistakes ?: 0f,
            jitter = p?.jitter ?: global.jitter ?: 0.5f,
        )
    }

    /** Word directives that apply to a note: the "any note" ones plus its own. */
    fun wordsFor(note: Int): List<WordDirective> = (noteWords[0].orEmpty()) + (noteWords[note].orEmpty())

    val globalSpeed: Float get() = (global.speed ?: 1f).let { if (it > 0f) it else 1f }
}

fun parseBehavior(text: String): BehaviorSpec {
    var global = TimingSpec()
    var take = TakeSpec()
    val perNote = HashMap<Int, TimingSpec>()
    val noteWords = HashMap<Int, MutableList<WordDirective>>()

    fun applyTiming(spec: TimingSpec, key: String, v: Float?): TimingSpec = when (key) {
        "speed" -> spec.copy(speed = v)
        "pacing", "humanize" -> spec.copy(pacing = v)
        "hesitation" -> spec.copy(hesitation = v)
        "mistakes", "typos" -> spec.copy(mistakes = v)
        "jitter" -> spec.copy(jitter = v)
        else -> spec
    }

    for (raw in text.lines()) {
        var line = raw.substringBefore('#').trim()
        if (line.isEmpty()) continue

        var note = 0 // 0 = global / "any note"
        if (line.startsWith("note ", ignoreCase = true)) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                note = line.substring(5, colon).trim().toIntOrNull() ?: 0
                line = line.substring(colon + 1).trim()
            }
        }
        if (line.isEmpty()) continue
        val t = line.split(Regex("\\s+"))
        when (t[0].lowercase()) {
            "reconsider" -> {
                // reconsider <word> -> <wrong> hold <ms>
                val arrow = t.indexOfFirst { it == "->" || it == "→" }
                val word = t.getOrNull(1) ?: continue
                val wrong = if (arrow >= 0) t.getOrNull(arrow + 1).orEmpty() else ""
                val holdIdx = t.indexOfFirst { it.equals("hold", true) }
                val ms = t.getOrNull(holdIdx + 1)?.toIntOrNull() ?: 800
                noteWords.getOrPut(note) { mutableListOf() }.add(WordDirective(word, true, wrong, ms))
            }
            "dwell" -> {
                // dwell <word> <ms>
                val word = t.getOrNull(1) ?: continue
                val ms = t.getOrNull(2)?.toIntOrNull() ?: 700
                noteWords.getOrPut(note) { mutableListOf() }.add(WordDirective(word, false, "", ms))
            }
            "start" -> if (note == 0) take = take.copy(startDelay = t.getOrNull(1)?.toIntOrNull() ?: take.startDelay)
            "hold" -> if (note == 0) take = take.copy(holdEnd = t.getOrNull(1)?.toIntOrNull() ?: take.holdEnd)
            "loop" -> if (note == 0) take = take.copy(loop = t.getOrNull(1)?.lowercase() in setOf("on", "true", "yes", "1"))
            else -> {
                val key = t[0].lowercase()
                val v = t.getOrNull(1)?.toFloatOrNull()
                if (note == 0) global = applyTiming(global, key, v)
                else perNote[note] = applyTiming(perNote[note] ?: TimingSpec(), key, v)
            }
        }
    }
    return BehaviorSpec(global, take, perNote, noteWords)
}
