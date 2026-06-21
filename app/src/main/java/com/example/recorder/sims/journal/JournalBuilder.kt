package com.example.recorder.sims.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.sims.BuilderContext
import com.example.recorder.sims.notes.NoteFont
import com.example.recorder.ui.BubbleColorRow
import com.example.recorder.ui.EnumPicker
import com.example.recorder.ui.LabeledSlider
import com.example.recorder.ui.SectionLabel
import com.example.recorder.ui.SoundProfilePicker

private val SYMBOLS = (
    "★ ☆ ♥ ♡ ❤ ✓ ✔ ✗ ✘ ☑ ☐ ❗ ❓ → ← ↑ ↓ ↻ ☺ ☹ ❀ ✿ ❄ ☀ ☾ ♪ ♫ ⚡ ✦ ✧ ➜ ✎ ✏ § ¶ ❝ ❞ … — • ◦ ‣ ✦"
    ).split(" ")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun JournalBuilder(ctx: BuilderContext) {
    val s = JournalStore
    // cursor-aware entry so symbols/marks insert where you are (and wrap selections)
    var tfv by remember { mutableStateOf(TextFieldValue(s.text, TextRange(s.text.length))) }
    LaunchedEffect(s.text) { if (s.text != tfv.text) tfv = TextFieldValue(s.text, TextRange(s.text.length)) }
    fun set(v: TextFieldValue) { tfv = v; s.text = v.text }
    fun insert(str: String) {
        val a = tfv.selection.min; val b = tfv.selection.max
        val nt = tfv.text.substring(0, a) + str + tfv.text.substring(b)
        set(TextFieldValue(nt, TextRange(a + str.length)))
    }
    fun wrap(pre: String, post: String) {
        val a = tfv.selection.min; val b = tfv.selection.max
        val sel = tfv.text.substring(a, b)
        val nt = tfv.text.substring(0, a) + pre + sel + post + tfv.text.substring(b)
        set(TextFieldValue(nt, TextRange(a + pre.length + sel.length)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Entry")
        OutlinedTextField(tfv, { set(it) }, label = { Text("Journal entry (one line per ruling)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp))
        OutlinedTextField(s.date, { s.date = it }, label = { Text("Date corner (blank = none)") }, modifier = Modifier.fillMaxWidth())

        SectionLabel("Symbols & marks")
        Text("Select a word then tap a mark to draw it on; symbols insert at the cursor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("◯ circle") { wrap("[o]", "[/o]") }
            Chip("U̲ underline") { wrap("[u]", "[/u]") }
            Chip("✗ cross out") { wrap("[x]", "[/x]") }
            Chip("— divider") { insert("\n---\n") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SYMBOLS.forEach { sym -> Chip(sym) { insert(sym) } }
        }

        SectionLabel("Hand & page")
        EnumPicker("Handwriting", s.font.label, NoteFont.values().map { it.label }) { s.font = NoteFont.values()[it] }
        LabeledSlider("Text size", s.textScale, 0.6f..1.6f, "%.2f×") { s.textScale = it }
        LabeledSlider("Messiness (crooked / sizes / ink)", s.messiness, 0f..1f) { s.messiness = it }
        BubbleColorRow("Ink", s.ink) { s.ink = it }
        BubbleColorRow("Paper", s.paper) { s.paper = it }

        SectionLabel("Writing & sound")
        LabeledSlider("Writing speed", s.typeSpeed, 0.3f..3f, "%.2f×") { s.typeSpeed = it }
        SoundProfilePicker("Writing sound", s.keySound) { s.keySound = it }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
