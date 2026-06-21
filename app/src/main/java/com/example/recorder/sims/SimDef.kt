package com.example.recorder.sims

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.recorder.engine.SimRuntime
import org.json.JSONObject

enum class SimFrame { PHONE, DESKTOP, FREE }

data class SimLogical(val w: Int, val h: Int)

/** What a sim's [SimDef.Builder] needs from the host screen. */
class BuilderContext(
    val rt: SimRuntime,
    val pickImage: () -> Unit,   // launch the gallery picker for the selected item's image
    val pickAvatar: () -> Unit,  // launch the gallery picker for an avatar/profile image
)

/**
 * A sim — a typed-animation recreation of an app. Port of the SimDef contract in
 * src/sims/types.ts. Each sim is self-contained: it renders at its fixed [logical]
 * size and owns its editor ([Builder]), persistence ([toJson]/[fromJson]), reset,
 * and picked-media handling. Adding a sim = a new package + one Registry line.
 */
interface SimDef {
    val id: String
    val label: String
    /** Launcher glyph (emoji or letter). */
    val glyph: String
    /** Accent color used in the launcher + active state. */
    val accent: Color
    val frame: SimFrame
    /** Logical render size (the sim lays out at this; the stage scales it). */
    val logical: SimLogical
    /** Built and usable, vs. a roadmap placeholder. */
    val ready: Boolean
    val defaultScript: String

    /** The sim's live stage. Observes [rt] (script, settings, play/stop signals). */
    @Composable
    fun Content(rt: SimRuntime)

    /** Label for the first ("content") config tab. */
    val tabLabel: String get() = "Content"

    /** The sim's editor, shown in the first config tab. Default = nothing. */
    @Composable
    fun Builder(ctx: BuilderContext) {}

    /** Reset this sim's content/config back to its defaults. */
    fun reset() {}

    /** Serialize this sim's document for a saved project (null = nothing to save). */
    fun toJson(): JSONObject? = null

    /** Restore this sim's document from a saved project. */
    fun fromJson(o: JSONObject) {}

    /** A gallery image was picked for this sim's selected item. */
    fun onPickedImage(uri: String) {}

    /** A gallery image was picked as this sim's avatar/profile. */
    fun onPickedAvatar(uri: String) {}
}
