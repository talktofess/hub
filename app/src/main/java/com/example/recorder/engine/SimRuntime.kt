package com.example.recorder.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.audio.AudioBus
import com.example.recorder.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The resolved typing timing for one note (per-note overrides already folded onto
 * the universal defaults). The plan builder computes this per note and the typing
 * loop reads it, so each note types with its own rhythm.
 */
/** A resolved typing behavior on one word ordinal within a note. */
data class WordAct(val reconsider: Boolean, val dwellMs: Int, val wrong: String)

data class NoteTiming(
    val speed: Float,
    val humanize: Float,
    val thinkPauses: Float,
    val jitter: Float,
    val autoTypo: Float,
    val noteChars: Int,                  // for the per-note pacing arc
    val wordActions: Map<Int, WordAct>,  // word ordinal (within the note) -> behavior
)

/** Pick [n] distinct word ordinals out of [total] (for placing dwells/reconsiders). */
fun pickOrdinals(n: Int, total: Int): Set<Int> {
    if (n <= 0 || total <= 0) return emptySet()
    val k = n.coerceAtMost(total)
    val s = HashSet<Int>()
    var guard = 0
    while (s.size < k && guard++ < total * 6) s.add(Random.nextInt(total))
    return s
}

/**
 * Shared per-sim runtime. Combines RecordingProvider (state) and useTypewriter
 * (the typing loop) from the React app into one holder the UI observes.
 *
 * The UI sets [script] / [settings]; the active sim registers a [planFactory] so
 * the runtime can [play] / [stop] it directly, getting a fresh plan each run.
 */
/** Starter behavior script shown in the Timing tab. */
const val DEFAULT_BEHAVIOR = """# Behavior script — controls typing RHYTHM (styling stays in the Notes tab).
# Global defaults:
speed 1.0
pacing 0.4
hesitation 0.5
mistakes 0
start 600
hold 1200
loop off

# Per note (note <number>, 1-based):
# note 2: speed 0.8
# note 2: pacing 0.7

# Mistype a word then fix it (type wrong, hold ms, correct):
# reconsider banana -> bamana hold 1000
# note 1: reconsider morning -> mprning hold 800

# Pause before a word:
# dwell heron 600
"""

class SimRuntime {
    var script by mutableStateOf("")
    var behaviorScript by mutableStateOf(DEFAULT_BEHAVIOR)
    @Volatile var behavior: BehaviorSpec = parseBehavior(DEFAULT_BEHAVIOR)
    var settings by mutableStateOf(Settings())
    var playing by mutableStateOf(false)
        private set

    /** When true, ignore the loop setting (used for one-shot recording takes). */
    var oneShot = false

    /** The current note's resolved timing; the plan sets it per note via [beginNote]
        and the typing loop reads it live so each note types at its own rhythm. */
    @Volatile var noteTiming: NoteTiming = NoteTiming(1f, 0f, 0.5f, 0.5f, 0f, 1, emptyMap())
    var noteTyped = 0            // chars typed so far within the current note
    var noteWordOrdinal = -1     // word index within the current note

    /** Called by the plan at the start of each note. */
    fun beginNote(t: NoteTiming) {
        noteTiming = t
        noteTyped = 0
        noteWordOrdinal = -1
    }

    /** The active sim registers its plan builder here so the runtime can play it
        directly — no fragile signal indirection. Cleared when the sim disposes. */
    var planFactory: (() -> List<TypeStep>)? = null

    val audio = AudioBus.engine
    // Plain Main (not Main.immediate): continuations post to the message queue so
    // touch input is processed between typing steps — otherwise buttons feel
    // unresponsive (need multiple taps) while a take is animating.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runToken = 0

    /** Play (or restart) the active sim. Deterministic — runs immediately. */
    fun play() {
        val factory = planFactory ?: return
        run(factory)
    }

    fun applyAudioSettings() {
        audio.profile = settings.sound
        audio.monitorGain = settings.liveVolume
        audio.recordGain = settings.recordVolume
        audio.bedGain = settings.bgAudioVolume
    }

    /** Begin running [factory]'s plan with the universal realism settings. */
    fun run(factory: () -> List<TypeStep>) {
        val token = ++runToken
        playing = true
        applyAudioSettings()
        audio.resume()
        scope.launch {
            val st = settings
            val speed = if (st.speed > 0f) st.speed else 1f
            noteTiming = NoteTiming(speed, st.humanize, st.thinkPauses, st.jitter, st.autoTypo, 1, emptyMap())
            do {
                val plan = factory()
                delay((st.startDelay / speed).toLong())
                if (token != runToken) return@launch
                val finished = runOnce(plan, st, speed) { token != runToken }
                if (!finished) return@launch
                if (st.loop && !oneShot) delay(st.holdEnd.toLong())
            } while (st.loop && !oneShot && token == runToken)
            if (token != runToken) return@launch
            playing = false
        }
    }

    fun stop() {
        runToken++
        playing = false
    }

    private suspend fun runOnce(
        plan: List<TypeStep>, st: Settings, speed: Float, aborted: () -> Boolean,
    ): Boolean {
        fun tap() = audio.key()

        // Per-note pacing arc + effective speed, read from the live noteTiming that
        // each note's Reveal sets via beginNote().
        fun pace(): Float {
            val t = noteTiming
            val h = t.humanize.coerceIn(0f, 1f)
            if (h <= 0f) return 1f
            // progress through the note, clamped — a too-small noteChars must never
            // drive this past 1 (that flips `tail` negative → eff() negative → instant).
            val p = (noteTyped.toFloat() / t.noteChars.coerceAtLeast(1)).coerceIn(0f, 1f)
            val warm = if (p < 0.18f) 1f - (1f - p / 0.18f) * 0.45f else 1f
            val tail = if (p > 0.85f) 1f - ((p - 0.85f) / 0.15f) * 0.4f else 1f
            val breathe = 1f + 0.18f * sin(p * (2 * PI.toFloat()) * 3f)
            return 1f + (warm * tail * breathe - 1f) * h
        }
        fun eff(): Float = ((if (noteTiming.speed > 0f) noteTiming.speed else 1f) * pace()).coerceAtLeast(0.05f)
        fun timing() = TimingOpts(noteTiming.jitter, noteTiming.thinkPauses)

        for (step in plan) {
            if (aborted()) return false
            when (step) {
                is TypeStep.Pause -> delay(step.ms.toLong())
                is TypeStep.Reveal -> {
                    step.fn() // may call beginNote() to switch this note's timing
                    step.delay?.let { delay((it / eff()).toLong()) }
                }
                is TypeStep.Type -> {
                    var cur = ""
                    // type one character of the real text
                    suspend fun typeChar(ch: Char) {
                        // auto-typo: occasionally fumble a letter, then self-correct
                        if (noteTiming.autoTypo > 0f && Random.nextDouble() < noteTiming.autoTypo) {
                            val w = fumbleFor(ch.toString())
                            if (w != null) {
                                cur += w; step.onUpdate(cur); tap()
                                delay((charDelay(ch.toString(), timing()) / eff()).toLong())
                                delay(((180 + Random.nextDouble() * 160) / eff()).toLong())
                                cur = cur.dropLast(w.length); step.onUpdate(cur); tap()
                                delay(((60 + Random.nextDouble() * 40) / eff()).toLong())
                            }
                        }
                        cur += ch; step.onUpdate(cur); tap(); noteTyped++
                        delay((charDelay(ch.toString(), timing()) / eff()).toLong())
                    }
                    suspend fun backspace() {
                        if (cur.isNotEmpty()) cur = cur.dropLast(1)
                        step.onUpdate(cur); tap()
                        delay(((45 + Random.nextDouble() * 35) / eff()).toLong())
                    }

                    // process the field word-by-word so scripted reconsiders can act on a whole word
                    for ((seg, isWord) in splitWords(step.text)) {
                        if (aborted()) return false
                        if (!isWord) { for (ch in seg) typeChar(ch); continue }
                        noteWordOrdinal++
                        val act = noteTiming.wordActions[noteWordOrdinal]
                        if (act != null && !act.reconsider) {
                            delay((act.dwellMs / eff()).toLong()) // dwell before the word
                        }
                        if (act != null && act.reconsider && act.wrong.isNotEmpty()) {
                            for (ch in act.wrong) {
                                cur += ch; step.onUpdate(cur); tap()
                                delay((charDelay(ch.toString(), timing()) / eff()).toLong())
                            }
                            delay((act.dwellMs / eff()).toLong()) // hold before reconsidering
                            val common = act.wrong.commonPrefixWith(seg).length
                            repeat(act.wrong.length - common) { backspace() }
                            for (i in common until seg.length) typeChar(seg[i])
                        } else {
                            for (ch in seg) typeChar(ch)
                        }
                    }
                }
            }
        }
        return true
    }
}
