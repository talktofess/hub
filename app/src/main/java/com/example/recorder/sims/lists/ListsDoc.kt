package com.example.recorder.sims.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.recorder.model.SoundProfile

enum class BlockType(val label: String) { CARD("Ranked card"), NOTE("Note / text"), IMAGE("Image"), VIDEO("Video") }
enum class Trans(val label: String) { CUT("Cut"), FADE("Fade"), SLIDE_UP("Slide up"), SLIDE_LEFT("Slide in"), POP("Pop") }
enum class StartMode(val label: String) { AFTER("After previous"), WITH("With previous") }

/**
 * One timeline block. A list is an ordered sequence of these; each can be a
 * ranked card, a text note, an image, or a video thumbnail, with its own
 * start timing (after / with the previous + delay), hold/exit, and transitions.
 */
data class Block(
    val id: Long,
    val type: BlockType = BlockType.CARD,
    val rank: String = "",
    val title: String = "",
    val text: String = "",
    val score: String = "",
    val tier: String = "",
    val badge: String = "",
    val imageUri: String? = null,
    val duration: String = "",          // shown on VIDEO blocks (e.g. "0:14")
    val start: StartMode = StartMode.AFTER,
    val startDelay: Int = 0,            // ms extra before this block starts
    val enter: Trans = Trans.FADE,
    val hold: Int = 0,                  // ms to stay before exiting (0 = stays)
    val exit: Trans = Trans.FADE,
)

/** A configurable timeline of blocks (cards / notes / images / videos). */
object ListsStore {
    var heading by mutableStateOf("Editing tricks, ranked")
    var accent by mutableStateOf(0xFF5B8DEFL)
    var textScale by mutableStateOf(1f)
    // typing + sound
    var typeSpeed by mutableStateOf(0.85f)
    var pacing by mutableStateOf(0.3f)
    var keySound by mutableStateOf(SoundProfile.MECHANICAL)
    var cardGap by mutableStateOf(280)

    val items = mutableStateListOf<Block>()
    var selectedId by mutableStateOf<Long?>(null)
        private set

    private var counter = 1L

    init { reset() }

    fun select(id: Long) { selectedId = id }
    fun add() { val b = Block(counter++, title = "New entry", text = "A short blurb."); items.add(b); selectedId = b.id }
    fun update(id: Long, t: (Block) -> Block) { val i = items.indexOfFirst { it.id == id }; if (i >= 0) items[i] = t(items[i]) }
    fun remove(id: Long) { val i = items.indexOfFirst { it.id == id }; if (i >= 0) items.removeAt(i) }
    fun move(id: Long, d: Int) { val i = items.indexOfFirst { it.id == id }; if (i < 0) return; val j = (i + d).coerceIn(0, items.size - 1); if (i != j) items.add(j, items.removeAt(i)) }

    fun setAll(head: String, list: List<Block>) {
        heading = head; items.clear(); items.addAll(list)
        counter = (list.maxOfOrNull { it.id } ?: 0L) + 1
        selectedId = items.firstOrNull()?.id
    }

    fun reset() {
        heading = "Editing tricks, ranked"; accent = 0xFF5B8DEFL; textScale = 1f
        typeSpeed = 0.85f; pacing = 0.3f; keySound = SoundProfile.MECHANICAL; cardGap = 280
        items.clear(); counter = 1L
        items.add(Block(counter++, BlockType.CARD, "3", "J-cuts", "Let the next scene's audio lead.", "9.1", "A"))
        items.add(Block(counter++, BlockType.CARD, "2", "Match cut", "Shapes line up across the cut.", "9.4", "S"))
        items.add(Block(counter++, BlockType.CARD, "1", "Needle drop", "Drop the track on the beat.", "9.8", "S", "VIRAL"))
        selectedId = items.firstOrNull()?.id
    }
}
