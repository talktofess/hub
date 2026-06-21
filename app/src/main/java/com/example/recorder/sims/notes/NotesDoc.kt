package com.example.recorder.sims.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile

/** The surface texture of a note (ruled paper, grid, dots, …). */
enum class PaperStyle(val label: String) {
    PLAIN("Plain"),
    RULED("Ruled lines"),
    GRID("Grid"),
    DOTS("Dot grid"),
    GRAPH("Graph (fine)"),
    CORNELL("Cornell"),
    MUSIC("Music staff"),
    ISO("Isometric"),
}

fun defaultPaperFor(shape: NoteShape): PaperStyle = when (shape) {
    NoteShape.NOTEBOOK, NoteShape.INDEX -> PaperStyle.RULED
    else -> PaperStyle.PLAIN
}

enum class NoteAlign(val label: String) { START("Left"), CENTER("Center"), END("Right") }

enum class WordActionKind { DWELL, RECONSIDER }

/**
 * A scripted typing behavior on a specific word of a note (by word index).
 * DWELL = pause [dwellMs] before the word. RECONSIDER = type [wrong] first, hold
 * [dwellMs], then correct to the real word.
 */
data class WordAction(
    val wordIndex: Int,
    val kind: WordActionKind = WordActionKind.RECONSIDER,
    val dwellMs: Int = 800,
    val wrong: String = "",
)

/**
 * One note in the visual builder — its own text, color, image, and per-note
 * setting overrides. An override of `null` means "use the universal default", so
 * unset notes follow the defaults and you only change what you want per note.
 */
data class NoteConfig(
    val id: Long,
    val shape: NoteShape = NoteShape.STICKY,
    val header: String = "",
    val body: String = "",
    val footer: String = "",
    val color: Long = 0xFFFFE16A,   // note background (ARGB)
    val imageUri: String? = null,
    val imageRotation: Float = 0f,
    // surface + type
    val paper: PaperStyle = PaperStyle.PLAIN,
    val lineColor: Long = 0xFF9DB4CC, // color of ruled lines / grid / dots
    val font: NoteFont = NoteFont.HANDWRITING,
    val marginLine: Boolean = false, // a vertical ruled margin (notebook style)
    val tape: Boolean = false,       // washi-tape strips on the top corners
    val dogEar: Boolean = false,     // folded bottom-right corner
    val pin: Boolean = false,        // push-pin at the top center
    val border: Boolean = false,     // outline around the note
    val sharpCorners: Boolean = false, // square vs rounded corners
    val align: NoteAlign = NoteAlign.START,
    val alpha: Float = 1f, // note transparency (1 = opaque, 0 = fully see-through)
    // text styling
    val textColor: Long = 0xFF1B1B1B,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    // per-note overrides (null = use the universal default)
    val sound: SoundProfile? = null,
    val speed: Float? = null,
    val fontScale: Float? = null,
    val rotation: Float? = null, // whole-note tilt in degrees (e.g. 90 = sideways)
    // per-note timing (null = use the universal Timing-tab default)
    val humanize: Float? = null,
    val thinkPauses: Float? = null,
    val dwellCount: Int? = null,
    val reconsiderCount: Int? = null,
    // scripted per-word typing behaviors (e.g. mistype "banana" as "bamana" first)
    val wordActions: List<WordAction> = emptyList(),
)

/** Default whole-note tilt per shape (used when a note has no rotation override). */
fun defaultRotationFor(shape: NoteShape): Float = when (shape) {
    NoteShape.STICKY -> -1.6f
    NoteShape.POLAROID -> 1.4f
    else -> 0f
}

/** Default note color suggested per shape when adding a fresh note. */
fun defaultColorFor(shape: NoteShape): Long = when (shape) {
    NoteShape.STICKY -> 0xFFFFE16A
    NoteShape.NOTEBOOK -> 0xFFFFFFFF
    NoteShape.INDEX -> 0xFFFFFDF8
    NoteShape.POLAROID -> 0xFFFCFCF7
    NoteShape.POSTCARD -> 0xFFF6EFE2
    NoteShape.DOCUMENT -> 0xFFF7F6F3
}

/**
 * The Notes document — an ordered list of [NoteConfig] the user builds in the
 * editor. Both the builder UI and the Notes sim read it; editing it live updates
 * the preview and, on Play, the animated/recorded take. Process-wide singleton
 * (one active sim at a time).
 */
object NotesStore {
    val notes = mutableStateListOf<NoteConfig>()

    var selectedId by mutableStateOf<Long?>(null)
        private set

    private var counter = 1L

    init {
        notes.add(NoteConfig(counter++, NoteShape.STICKY, "Tue · 6:48 AM", "woke up before the alarm again", color = defaultColorFor(NoteShape.STICKY)))
        notes.add(NoteConfig(counter++, NoteShape.NOTEBOOK, "Things I noticed today", "the bakery on Main has a new sign\ntwo old men play chess in the park", color = defaultColorFor(NoteShape.NOTEBOOK), paper = PaperStyle.RULED, font = NoteFont.PRINT))
        selectedId = notes.firstOrNull()?.id
    }

    fun select(id: Long) { selectedId = id }

    fun add(shape: NoteShape = NoteShape.STICKY) {
        val n = NoteConfig(counter++, shape, color = defaultColorFor(shape), paper = defaultPaperFor(shape))
        notes.add(n)
        selectedId = n.id
    }

    fun remove(id: Long) {
        val i = notes.indexOfFirst { it.id == id }
        if (i < 0) return
        notes.removeAt(i)
        if (selectedId == id) selectedId = notes.getOrNull(i)?.id ?: notes.lastOrNull()?.id
    }

    /** Duplicate a note (inserted right after it) and select the copy. */
    fun duplicate(id: Long) {
        val i = notes.indexOfFirst { it.id == id }
        if (i < 0) return
        val copy = notes[i].copy(id = counter++)
        notes.add(i + 1, copy)
        selectedId = copy.id
    }

    /** Move a note within the order by [delta] positions (clamped). */
    fun move(id: Long, delta: Int) {
        val i = notes.indexOfFirst { it.id == id }
        if (i < 0) return
        val j = (i + delta).coerceIn(0, notes.size - 1)
        if (i == j) return
        notes.add(j, notes.removeAt(i))
    }

    fun indexOf(id: Long): Int = notes.indexOfFirst { it.id == id }

    fun selected(): NoteConfig? = notes.firstOrNull { it.id == selectedId }

    /** Replace all notes (loading a project). */
    fun setAll(list: List<NoteConfig>) {
        notes.clear()
        notes.addAll(list)
        counter = (list.maxOfOrNull { it.id } ?: 0L) + 1
        selectedId = notes.firstOrNull()?.id
    }

    /** Reset to the starter notes (new project). */
    fun reset() {
        notes.clear()
        counter = 1L
        notes.add(NoteConfig(counter++, NoteShape.STICKY, "Tue · 6:48 AM", "woke up before the alarm again", color = defaultColorFor(NoteShape.STICKY)))
        notes.add(NoteConfig(counter++, NoteShape.NOTEBOOK, "Things I noticed today", "the bakery on Main has a new sign\ntwo old men play chess in the park", color = defaultColorFor(NoteShape.NOTEBOOK), paper = PaperStyle.RULED, font = NoteFont.PRINT))
        selectedId = notes.firstOrNull()?.id
    }

    fun update(id: Long, transform: (NoteConfig) -> NoteConfig) {
        val i = notes.indexOfFirst { it.id == id }
        if (i >= 0) notes[i] = transform(notes[i])
    }

    /** Add or replace the scripted behavior for one word index. */
    fun setWordAction(id: Long, action: WordAction) = update(id) { n ->
        n.copy(wordActions = (n.wordActions.filter { it.wordIndex != action.wordIndex } + action).sortedBy { it.wordIndex })
    }

    fun removeWordAction(id: Long, wordIndex: Int) = update(id) { n ->
        n.copy(wordActions = n.wordActions.filter { it.wordIndex != wordIndex })
    }
}

/** The words of a note in typing order (header + body + footer), as the engine indexes them. */
fun NoteConfig.words(): List<String> =
    listOf(header, body, footer).filter { it.isNotEmpty() }.joinToString(" ").split(Regex("\\s+")).filter { it.isNotEmpty() }
