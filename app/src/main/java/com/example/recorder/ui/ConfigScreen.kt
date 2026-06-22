package com.example.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.engine.SimRuntime
import com.example.recorder.engine.settledText
import com.example.recorder.model.BgKind
import com.example.recorder.model.BgScale
import com.example.recorder.model.SoundProfile
import com.example.recorder.sims.BuilderContext
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.notes.NoteAlign
import com.example.recorder.sims.notes.NoteCard
import com.example.recorder.sims.notes.NoteConfig
import com.example.recorder.sims.notes.NoteFont
import com.example.recorder.sims.notes.NoteShape
import com.example.recorder.sims.notes.NotesStore
import com.example.recorder.sims.notes.PaperStyle
import com.example.recorder.sims.notes.WordAction
import com.example.recorder.sims.notes.WordActionKind
import com.example.recorder.sims.notes.defaultColorFor
import com.example.recorder.sims.notes.defaultPaperFor
import com.example.recorder.sims.notes.defaultRotationFor
import com.example.recorder.sims.notes.words
import com.example.recorder.engine.fumbleWord
import kotlin.math.min

private enum class Tab(val label: String, val icon: ImageVector) {
    NOTES("Notes", Icons.AutoMirrored.Filled.Notes),
    SOUND("Sound", Icons.Filled.VolumeUp),
    TIMING("Timing", Icons.Filled.Speed),
    LOOK("Look", Icons.Filled.Palette),
    BACKGROUND("Background", Icons.Filled.Image),
}

/**
 * Landing / configuration page. No live stage here — you choose the sim and tune
 * its settings (in a vertical-tab layout), then open the stage to preview/record.
 */
@Composable
fun ConfigScreen(
    rt: SimRuntime,
    selected: SimDef,
    onBack: () -> Unit,
    onPickBackground: () -> Unit,
    onPickImage: () -> Unit,
    onPickAudio: () -> Unit,
    onPickAvatar: () -> Unit,
    onOpenTypeSheet: () -> Unit,
    onOpenProjects: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetConfirm by remember { mutableStateOf(false) }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset ${selected.label}?") },
            text = { Text("This deletes the current ${if (selected.id == "imessage") "conversation" else "content"} and restores the default starter set. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { selected.reset(); showResetConfirm = false }) { Text("Delete & restore") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } },
        )
    }
    Column(modifier.fillMaxSize()) {
        // header — single sim: back to hub, the sim's name, and its actions
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back to hub") }
                Text(selected.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onOpenProjects) { Icon(Icons.Filled.FolderOpen, contentDescription = "projects", modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { showResetConfirm = true }) { Icon(Icons.Filled.Refresh, contentDescription = "reset", modifier = Modifier.size(20.dp)) }
                if (selected.id == "notes") {
                    IconButton(onClick = onOpenTypeSheet) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "type sheet", modifier = Modifier.size(20.dp)) }
                }
                Button(onClick = onPlay, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Play", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // vertical tabs: rail on the left, content on the right
        var tab by remember { mutableStateOf(Tab.NOTES) }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.width(92.dp).fillMaxSize().background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState()),
            ) {
                Tab.entries.forEach { t ->
                    val lbl = if (t == Tab.NOTES) selected.tabLabel else t.label
                    TabRailItem(t.icon, lbl, t == tab) { tab = t }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when (tab) {
                    Tab.NOTES -> selected.Builder(BuilderContext(rt, onPickImage, onPickAvatar))
                    Tab.SOUND -> SoundTab(rt)
                    Tab.TIMING -> TimingTab(rt)
                    Tab.LOOK -> LookTab(rt)
                    Tab.BACKGROUND -> BackgroundTab(rt, onPickBackground, onPickAudio)
                }
            }
        }
    }
}

@Composable
private fun TabRailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = tint)
    }
}

// ---------- tab contents ----------

private val NOTE_COLORS = listOf(
    0xFFFFE16A, 0xFFFFD23F, 0xFFFFFFFF, 0xFFFFFDF8, 0xFFF6EFE2,
    0xFFBfe3c6, 0xFFB7D6F2, 0xFFF3C7D8, 0xFFE7D8F0, 0xFFCfc7b8,
)
private val LINE_COLORS = listOf(
    0xFF9DB4CC, 0xFFC9D6E4, 0xFFF3A3A3, 0xFFB7D6A8, 0xFFD8C39A, 0xFF999999, 0xFF6B8CAE,
)
private val TEXT_COLORS = listOf(
    0xFF1B1B1B, 0xFF000000, 0xFF3A3A3A, 0xFF5B3A1E, 0xFF1E3A5B, 0xFF7A1C2B, 0xFF1C5A3A, 0xFFFFFFFF,
)

/** The visual note builder: add notes, edit each (text/color/image), live-preview. */
@Composable
fun NotesBuilderTab(rt: SimRuntime, onPickNoteImage: () -> Unit) {
    val notes = NotesStore.notes
    val sel = NotesStore.selected()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // the note list + add (tap a note to edit it)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            notes.forEachIndexed { i, n -> NoteChip(i + 1, n, n.id == NotesStore.selectedId) { NotesStore.select(n.id) } }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { NotesStore.add() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, contentDescription = "add note") }
        }

        // compact live preview of the selected note (static — no need to play)
        if (sel != null) NotePreview(rt, sel)

        // editor for the selected note
        if (sel != null) NoteEditor(rt, sel, onPickNoteImage)
        else Text("Tap + to add your first note.", style = MaterialTheme.typography.bodySmall)
    }
}

val BUBBLE_COLORS = listOf(
    0xFF0A84FF, 0xFF34C759, 0xFFDCF8C6, 0xFFE9E9EB, 0xFFFF453A, 0xFFFF9F0A, 0xFFBF5AF2,
    0xFFE5DDD5, 0xFF202C33, 0xFF0B141A, 0xFF1B1B1B, 0xFFFFFFFF,
)

val CUE_IDS = listOf("off", "whoosh", "tritone", "pop", "ding")
val CUE_LABELS = listOf("Off", "Whoosh", "Tri-tone", "Pop", "Ding")

@Composable
fun CuePicker(label: String, current: String, onPick: (String) -> Unit) {
    EnumPicker(label, CUE_LABELS[CUE_IDS.indexOf(current).coerceAtLeast(0)], CUE_LABELS) { i -> onPick(CUE_IDS[i]) }
}

@Composable
fun SoundProfilePicker(label: String, current: com.example.recorder.model.SoundProfile, onPick: (com.example.recorder.model.SoundProfile) -> Unit) {
    val all = com.example.recorder.model.SoundProfile.entries
    EnumPicker(label, current.label, all.map { it.label }) { i -> onPick(all[i]) }
}

/** Just the writing instruments — for handwriting sims (Journal). */
val WRITING_SOUNDS = listOf(
    com.example.recorder.model.SoundProfile.STYLUS,
    com.example.recorder.model.SoundProfile.PENCIL,
    com.example.recorder.model.SoundProfile.PEN,
    com.example.recorder.model.SoundProfile.FOUNTAIN,
    com.example.recorder.model.SoundProfile.GEL,
    com.example.recorder.model.SoundProfile.MARKER,
    com.example.recorder.model.SoundProfile.FELT,
    com.example.recorder.model.SoundProfile.CHALK,
    com.example.recorder.model.SoundProfile.CRAYON,
    com.example.recorder.model.SoundProfile.BRUSHPEN,
)

@Composable
fun WritingSoundPicker(label: String, current: com.example.recorder.model.SoundProfile, onPick: (com.example.recorder.model.SoundProfile) -> Unit) {
    val opts = if (current in WRITING_SOUNDS) WRITING_SOUNDS else listOf(current) + WRITING_SOUNDS
    EnumPicker(label, current.label, opts.map { it.label }) { i -> onPick(opts[i]) }
}

@Composable
fun ReactionPicker(current: String?, onPick: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("React (the sim scrolls to it on the emoji keyboard)", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = current == null, onClick = { onPick(null) }, label = { Text("none") })
            com.example.recorder.sims.chat.REACTIONS_FULL.forEach { e ->
                FilterChip(selected = current == e, onClick = { onPick(e) }, label = { Text(e) })
            }
        }
    }
}

@Composable
fun BubbleColorRow(label: String, current: Long, onPick: (Long) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BUBBLE_COLORS.forEach { c ->
            val isSel = current == c
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                    .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun NoteChip(index: Int, note: NoteConfig, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
            .background(Color(note.color))
            .border(2.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text("$index", color = Color(0xFF1B1B1B), fontWeight = FontWeight.Bold) }
}

@Composable
private fun NotePreview(rt: SimRuntime, note: NoteConfig) {
    val width = rt.settings.simFloat("notes", "width", 940f)
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14151A)).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // render the note at logical px under Density(1f), scaled to fit the preview
            val availW = constraints.maxWidth.toFloat()
            val availH = constraints.maxHeight.toFloat()
            val scale = min(availW / (width + 120f), availH / 560f)
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                    NoteCard(
                        ni = 0,
                        note = note,
                        text = { _, fallback -> settledText(fallback) },
                        caret = { false },
                        rt = rt,
                        width = width,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(rt: SimRuntime, note: NoteConfig, onPickNoteImage: () -> Unit) {
    val id = note.id
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("This note's settings (Default = use the universal setting)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ShapePicker(note) { NotesStore.update(id) { n -> n.copy(shape = it, color = defaultColorFor(it), paper = defaultPaperFor(it)) } }
        EnumPicker("Paper", note.paper.label, PaperStyle.entries.map { it.label }) { i ->
            NotesStore.update(id) { it.copy(paper = PaperStyle.entries[i]) }
        }
        EnumPicker("Font", note.font.label, NoteFont.entries.map { it.label }) { i ->
            NotesStore.update(id) { it.copy(font = NoteFont.entries[i]) }
        }
        NoteSoundPicker(note) { s -> NotesStore.update(id) { it.copy(sound = s) } }
        NoteOverrideSlider("Text size", note.fontScale, rt.settings.fontScale, 0.7f..1.4f, "%.2f×") { v ->
            NotesStore.update(id) { it.copy(fontScale = v) }
        }
        NoteOverrideSlider("Rotation", note.rotation, defaultRotationFor(note.shape), -180f..180f, "%.0f°") { v ->
            NotesStore.update(id) { it.copy(rotation = v) }
        }
        Text(
            "Timing & per-word behavior live in the Timing tab's behavior script (e.g. " +
                "“note ${NotesStore.indexOf(note.id) + 1}: speed 0.8” or “reconsider <word> -> <wrong> hold 1000”).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = note.header, onValueChange = { v -> NotesStore.update(id) { it.copy(header = v) } },
            label = { Text("Header") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note.body, onValueChange = { v -> NotesStore.update(id) { it.copy(body = v) } },
            label = { Text("Content") }, modifier = Modifier.fillMaxWidth().height(120.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        )
        OutlinedTextField(
            value = note.footer, onValueChange = { v -> NotesStore.update(id) { it.copy(footer = v) } },
            label = { Text("Footer / caption") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        LabeledSlider("Transparency", 1f - note.alpha, 0f..1f, "%.0f%%", scale = 100f) { v ->
            NotesStore.update(id) { it.copy(alpha = 1f - v) }
        }

        Text("Note color", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NOTE_COLORS.forEach { c ->
                val isSel = note.color == c
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                        .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                        .clickable { NotesStore.update(id) { it.copy(color = c) } },
                )
            }
        }

        if (note.paper != PaperStyle.PLAIN) {
            Text("Line / grid color", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LINE_COLORS.forEach { c ->
                    val isSel = note.lineColor == c
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                            .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                            .clickable { NotesStore.update(id) { it.copy(lineColor = c) } },
                    )
                }
            }
        }

        Text("Text color", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TEXT_COLORS.forEach { c ->
                val isSel = note.textColor == c
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                        .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                        .clickable { NotesStore.update(id) { it.copy(textColor = c) } },
                )
            }
        }

        Text("Text style", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = note.bold, onClick = { NotesStore.update(id) { it.copy(bold = !it.bold) } }, label = { Text("Bold") })
            FilterChip(selected = note.italic, onClick = { NotesStore.update(id) { it.copy(italic = !it.italic) } }, label = { Text("Italic") })
            FilterChip(selected = note.underline, onClick = { NotesStore.update(id) { it.copy(underline = !it.underline) } }, label = { Text("Underline") })
            FilterChip(selected = note.marginLine, onClick = { NotesStore.update(id) { it.copy(marginLine = !it.marginLine) } }, label = { Text("Margin line") })
            FilterChip(selected = note.tape, onClick = { NotesStore.update(id) { it.copy(tape = !it.tape) } }, label = { Text("Tape") })
            FilterChip(selected = note.dogEar, onClick = { NotesStore.update(id) { it.copy(dogEar = !it.dogEar) } }, label = { Text("Dog-ear") })
            FilterChip(selected = note.pin, onClick = { NotesStore.update(id) { it.copy(pin = !it.pin) } }, label = { Text("Pin") })
            FilterChip(selected = note.border, onClick = { NotesStore.update(id) { it.copy(border = !it.border) } }, label = { Text("Border") })
            FilterChip(selected = note.sharpCorners, onClick = { NotesStore.update(id) { it.copy(sharpCorners = !it.sharpCorners) } }, label = { Text("Square") })
        }

        Text("Text alignment", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NoteAlign.entries.forEach { a ->
                FilterChip(selected = note.align == a, onClick = { NotesStore.update(id) { it.copy(align = a) } }, label = { Text(a.label) })
            }
        }

        Text("Image", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickNoteImage) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text(if (note.imageUri != null) "  Change" else "  Add image")
            }
            if (note.imageUri != null) {
                TextButton(onClick = { NotesStore.update(id) { it.copy(imageUri = null, imageRotation = 0f) } }) { Text("Remove") }
            }
        }
        if (note.imageUri != null) {
            LabeledSlider("Image rotation", note.imageRotation, -180f..180f, "%.0f°") { v ->
                NotesStore.update(id) { it.copy(imageRotation = v) }
            }
        }

        val idx = NotesStore.indexOf(id)
        val count = NotesStore.notes.size
        Text("Arrange", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { NotesStore.move(id, -1) }, enabled = idx > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null); Text(" Left")
            }
            OutlinedButton(onClick = { NotesStore.move(id, 1) }, enabled = idx in 0 until count - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null); Text(" Right")
            }
            OutlinedButton(onClick = { NotesStore.duplicate(id) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null); Text(" Duplicate")
            }
        }
        TextButton(onClick = { NotesStore.remove(id) }) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text("  Delete this note")
        }
    }
}

/** Scripted per-word timing: pick a word and set a dwell or a specific mistype. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordTimingEditor(note: NoteConfig) {
    val id = note.id
    val words = note.words()
    Text("Per-word typing (mistype a specific word, dwell, …)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

    note.wordActions.filter { it.wordIndex in words.indices }.forEach { wa ->
        val word = words[wa.wordIndex]
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("“$word”", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { NotesStore.removeWordAction(id, wa.wordIndex) }) { Text("Remove") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = wa.kind == WordActionKind.RECONSIDER, onClick = { NotesStore.setWordAction(id, wa.copy(kind = WordActionKind.RECONSIDER, wrong = wa.wrong.ifBlank { fumbleWord(word) })) }, label = { Text("Reconsider") })
                FilterChip(selected = wa.kind == WordActionKind.DWELL, onClick = { NotesStore.setWordAction(id, wa.copy(kind = WordActionKind.DWELL)) }, label = { Text("Dwell") })
            }
            if (wa.kind == WordActionKind.RECONSIDER) {
                OutlinedTextField(
                    value = wa.wrong, onValueChange = { NotesStore.setWordAction(id, wa.copy(wrong = it)) },
                    label = { Text("Mistype as (e.g. bamana)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            LabeledSlider(if (wa.kind == WordActionKind.DWELL) "Pause" else "Hold before fixing", wa.dwellMs.toFloat(), 100f..3000f, "%.0f ms") { v ->
                NotesStore.setWordAction(id, wa.copy(dwellMs = v.toInt()))
            }
        }
    }

    // add a word
    var expanded by remember { mutableStateOf(false) }
    val configured = note.wordActions.map { it.wordIndex }.toSet()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)) {
            Icon(Icons.Filled.Add, contentDescription = null); Text("  Add a word")
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            words.forEachIndexed { i, w ->
                if (i !in configured) {
                    DropdownMenuItem(text = { Text("$i · $w") }, onClick = {
                        NotesStore.setWordAction(id, WordAction(i, WordActionKind.RECONSIDER, 800, fumbleWord(w)))
                        expanded = false
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSoundPicker(note: NoteConfig, onPick: (SoundProfile?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = note.sound?.label ?: "Default", onValueChange = {}, readOnly = true,
            label = { Text("Sound (this note)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Default") }, onClick = { onPick(null); expanded = false })
            SoundProfile.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.label) }, onClick = { onPick(p); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnumPicker(label: String, current: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShapePicker(note: NoteConfig, onPick: (NoteShape) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = note.shape.name.lowercase().replaceFirstChar { it.uppercase() }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label, onValueChange = {}, readOnly = true, label = { Text("Note type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NoteShape.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = { onPick(s); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SoundTab(rt: SimRuntime) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Sound")
        SoundPicker(rt)
        LabeledSlider("Live volume (phone)", rt.settings.liveVolume, 0f..1f) {
            rt.settings = rt.settings.copy(liveVolume = it); rt.applyAudioSettings()
        }
        LabeledSlider("Record volume (in video)", rt.settings.recordVolume, 0f..1f) {
            rt.settings = rt.settings.copy(recordVolume = it); rt.applyAudioSettings()
        }
        Text(
            "Set Live volume to 0 to record silently — the keystroke sound is still baked into the video at Record volume. The mic is never used.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimingTab(rt: SimRuntime) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Timing defaults")
        Text(
            "These apply to every note. For per-word timing (pauses, mistypes), use the " +
                "“Type sheet” button at the top.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LabeledSlider("Speed", rt.settings.speed, 0.3f..3f, "%.2f×") { rt.settings = rt.settings.copy(speed = it) }
        LabeledSlider("Pacing arc", rt.settings.humanize, 0f..1f) { rt.settings = rt.settings.copy(humanize = it) }
        LabeledSlider("Hesitation", rt.settings.thinkPauses, 0f..1f) { rt.settings = rt.settings.copy(thinkPauses = it) }
        LabeledSlider("Auto-mistakes", rt.settings.autoTypo, 0f..0.15f) { rt.settings = rt.settings.copy(autoTypo = it) }
        LabeledSlider("Start delay", rt.settings.startDelay.toFloat(), 0f..2500f, "%.0f ms") { rt.settings = rt.settings.copy(startDelay = it.toInt()) }
        LabeledSlider("Hold at end", rt.settings.holdEnd.toFloat(), 0f..4000f, "%.0f ms") { rt.settings = rt.settings.copy(holdEnd = it.toInt()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Loop", Modifier.weight(1f))
            Switch(checked = rt.settings.loop, onCheckedChange = { rt.settings = rt.settings.copy(loop = it) })
        }
    }
}

/** Projects: save the current notes+settings, load/delete saved ones, start new. */
@Composable
fun ProjectsScreen(rt: SimRuntime, onLoad: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    val projects = remember(refresh) { com.example.recorder.store.Projects.list(ctx) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
            Text("Projects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Your work autosaves and restores on launch. Save named copies here to keep several.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel("Save current as")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val n = name.trim().ifBlank { "project" }.replace(Regex("[^A-Za-z0-9 _-]"), "")
                        com.example.recorder.store.Projects.save(ctx, n, NotesStore.notes.toList(), rt.settings)
                        name = ""; refresh++
                    },
                ) { Text("Save") }
            }
            OutlinedButton(onClick = { NotesStore.reset(); rt.settings = com.example.recorder.model.Settings(); rt.applyAudioSettings() }) {
                Icon(Icons.Filled.Add, contentDescription = null); Text(" New (start fresh)")
            }

            SectionLabel("Saved projects")
            if (projects.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall)
            projects.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p, Modifier.weight(1f))
                    TextButton(onClick = { onLoad(p); onBack() }) { Text("Load") }
                    IconButton(onClick = { com.example.recorder.store.Projects.delete(ctx, p); refresh++ }) { Icon(Icons.Filled.Delete, contentDescription = "delete") }
                }
            }
        }
    }
}

/** Dedicated full-screen type sheet: every note's words, each dial-able. */
@Composable
fun TypeSheetScreen(rt: SimRuntime, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
            Text("Type sheet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Each note's words, in order. Set a word to Pause (wait before it) or Mistype " +
                    "(type it wrong, hold, then fix). Defaults are in the Timing tab.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (NotesStore.notes.isEmpty()) Text("No notes yet — add some in the Notes tab.")
            NotesStore.notes.forEachIndexed { ni, note ->
                HorizontalDivider(Modifier.padding(top = 4.dp))
                val hint = note.header.ifBlank { note.body }.take(24)
                SectionLabel("Note ${ni + 1}${if (hint.isNotEmpty()) " · $hint" else ""}")
                NoteOverrideSlider("Speed", note.speed, rt.settings.speed, 0.3f..3f, "%.2f×") { v -> NotesStore.update(note.id) { it.copy(speed = v) } }
                NoteOverrideSlider("Pacing arc", note.humanize, rt.settings.humanize, 0f..1f, "%.2f") { v -> NotesStore.update(note.id) { it.copy(humanize = v) } }
                val words = note.words()
                if (words.isEmpty()) Text("(no words yet — add content in the Notes tab)", style = MaterialTheme.typography.bodySmall)
                else words.forEachIndexed { i, w -> WordRow(note, i, w) }
            }
        }
    }
}

/** One word's row in the type sheet: Normal / Pause / Mistype, with parameters. */
@Composable
private fun WordRow(note: NoteConfig, wordIndex: Int, word: String) {
    val id = note.id
    val action = note.wordActions.firstOrNull { it.wordIndex == wordIndex }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$wordIndex", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(word, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FilterChip(selected = action == null, onClick = { NotesStore.removeWordAction(id, wordIndex) }, label = { Text("Normal") })
            FilterChip(selected = action?.kind == WordActionKind.DWELL, onClick = { NotesStore.setWordAction(id, WordAction(wordIndex, WordActionKind.DWELL, 700, "")) }, label = { Text("Pause") })
            FilterChip(selected = action?.kind == WordActionKind.RECONSIDER, onClick = { NotesStore.setWordAction(id, WordAction(wordIndex, WordActionKind.RECONSIDER, 900, fumbleWord(word))) }, label = { Text("Mistype") })
        }
        if (action != null) {
            LabeledSlider(if (action.kind == WordActionKind.DWELL) "Pause" else "Hold before fixing", action.dwellMs.toFloat(), 100f..2500f, "%.0f ms") { v ->
                NotesStore.setWordAction(id, action.copy(dwellMs = v.toInt()))
            }
            if (action.kind == WordActionKind.RECONSIDER) {
                OutlinedTextField(
                    value = action.wrong, onValueChange = { NotesStore.setWordAction(id, action.copy(wrong = it)) },
                    label = { Text("Mistype as") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val CARET_COLORS = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFF5B8DEF, 0xFFE5484D, 0xFF35C26B, 0xFFF5C542, 0xFFB07CF0,
)

@Composable
private fun LookTab(rt: SimRuntime) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Look")
        LabeledSlider("Text size", rt.settings.fontScale, 0.7f..1.4f) { rt.settings = rt.settings.copy(fontScale = it) }

        CaretStylePicker(rt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Blinking caret", Modifier.weight(1f))
            Switch(checked = rt.settings.caretBlink, onCheckedChange = { rt.settings = rt.settings.copy(caretBlink = it) })
        }
        Text("Caret color", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CARET_COLORS.forEach { c ->
                val col = Color(c)
                val isSel = rt.settings.caretColor == col
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(col)
                        .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF), CircleShape)
                        .clickable { rt.settings = rt.settings.copy(caretColor = col) },
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        SectionLabel("Layout & output")
        LabeledSlider("Note width", rt.settings.simFloat("notes", "width", 940f), 560f..1040f, "%.0f") {
            rt.settings = rt.settings.withSimFloat("notes", "width", it)
        }
        Text("Recording quality", style = MaterialTheme.typography.labelMedium)
        QualityPicker(rt)
        Text(
            "Higher quality supersamples for a crisper recording but is much heavier — best on a powerful device.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaretStylePicker(rt: SimRuntime) {
    var expanded by remember { mutableStateOf(false) }
    val label = rt.settings.caretStyle.name.lowercase().replaceFirstChar { it.uppercase() }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label, onValueChange = {}, readOnly = true, label = { Text("Caret style") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.example.recorder.model.CaretStyle.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = { rt.settings = rt.settings.copy(caretStyle = s); expanded = false },
                )
            }
        }
    }
}

private data class Quality(val label: String, val scale: Float)
private val QUALITIES = listOf(
    Quality("Standard · 1080×1920", 1f),
    Quality("High · 1620×2880 (1.5×)", 1.5f),
    Quality("Ultra · 2160×3840 (2×)", 2f),
)

@Composable
private fun LayoutTab(rt: SimRuntime) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Layout")
        LabeledSlider("Note width", rt.settings.simFloat("notes", "width", 940f), 560f..1040f, "%.0f") {
            rt.settings = rt.settings.withSimFloat("notes", "width", it)
        }

        SectionLabel("Recording quality")
        QualityPicker(rt)
        Text(
            "Higher quality supersamples the frame for a crisper recording, but is much " +
                "heavier — use it only on a powerful device. Standard is best on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityPicker(rt: SimRuntime) {
    var expanded by remember { mutableStateOf(false) }
    val current = QUALITIES.firstOrNull { it.scale == rt.settings.recordScale } ?: QUALITIES.first()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.label, onValueChange = {}, readOnly = true, label = { Text("Quality") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QUALITIES.forEach { q ->
                DropdownMenuItem(
                    text = { Text(q.label) },
                    onClick = { rt.settings = rt.settings.copy(recordScale = q.scale); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun BackgroundTab(rt: SimRuntime, onPickBackground: () -> Unit, onPickAudio: () -> Unit) {
    val bg = rt.settings.bg
    val hasBg = bg.uri != null && bg.kind == BgKind.IMAGE
    val hasAudio = rt.settings.bgAudioUri != null
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Background audio (music / ambience)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickAudio) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
                Text(if (hasAudio) "  Change track" else "  Add music")
            }
            if (hasAudio) {
                TextButton(onClick = {
                    rt.settings = rt.settings.copy(bgAudioUri = null)
                    com.example.recorder.audio.AudioBus.engine.setBed(null)
                }) { Text("Remove") }
            }
        }
        if (hasAudio) {
            LabeledSlider("Music volume", rt.settings.bgAudioVolume, 0f..1f) {
                rt.settings = rt.settings.copy(bgAudioVolume = it); rt.applyAudioSettings()
            }
        }
        Text(
            "Music plays during the take and is mixed into the recording (looped, up to 90s).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("Background (behind the notes)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickBackground) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text(if (hasBg) "  Change image" else "  Pick image")
            }
            if (hasBg) {
                TextButton(onClick = { rt.settings = rt.settings.copy(bg = bg.copy(uri = null, kind = BgKind.NONE)) }) {
                    Text("Remove")
                }
            }
        }
        if (hasBg) {
            ScalePicker(rt)
            LabeledSlider("Opacity", bg.opacity, 0f..1f) { rt.settings = rt.settings.copy(bg = bg.copy(opacity = it)) }
            val always = bg.appearAtSec < 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Always visible", Modifier.weight(1f))
                Switch(
                    checked = always,
                    onCheckedChange = { on -> rt.settings = rt.settings.copy(bg = bg.copy(appearAtSec = if (on) -1f else 2f)) },
                )
            }
            if (!always) {
                LabeledSlider("Appear after (s)", bg.appearAtSec.coerceAtLeast(0f), 0f..10f, "%.1f") {
                    rt.settings = rt.settings.copy(bg = bg.copy(appearAtSec = it))
                }
            }
        } else {
            Text(
                "Pick an image to place behind the note cards. Video and audio backgrounds are coming next.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- shared bits ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundPicker(rt: SimRuntime) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = rt.settings.sound.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Keystroke sound") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SoundProfile.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label) },
                    onClick = { rt.settings = rt.settings.copy(sound = p); rt.applyAudioSettings(); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScalePicker(rt: SimRuntime) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = rt.settings.bg.scale.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Fit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BgScale.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label) },
                    onClick = { rt.settings = rt.settings.copy(bg = rt.settings.bg.copy(scale = s)); expanded = false },
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** A slider for a nullable per-note override: shows the effective value, falling
    back to [default] when unset; a "Default" button clears the override. */
@Composable
private fun NoteOverrideSlider(
    label: String,
    override: Float?,
    default: Float,
    range: ClosedFloatingPointRange<Float>,
    fmt: String,
    onChange: (Float?) -> Unit,
) {
    val effective = override ?: default
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$label: ${fmt.format(effective)}${if (override == null) "  (default)" else ""}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            if (override != null) TextButton(onClick = { onChange(null) }) { Text("Default") }
        }
        Slider(value = effective, onValueChange = { onChange(it) }, valueRange = range)
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    fmt: String = "%.2f",
    scale: Float = 1f,
    onChange: (Float) -> Unit,
) {
    Column {
        Text("$label: ${fmt.format(value * scale)}", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
