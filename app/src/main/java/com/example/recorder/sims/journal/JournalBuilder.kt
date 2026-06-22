package com.example.recorder.sims.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recorder.sims.BuilderContext
import com.example.recorder.sims.notes.NoteFont
import com.example.recorder.ui.BubbleColorRow
import com.example.recorder.ui.EnumPicker
import com.example.recorder.ui.LabeledSlider
import com.example.recorder.ui.SectionLabel
import com.example.recorder.ui.SymbolTextField
import com.example.recorder.ui.WritingSoundPicker

@Composable
fun JournalBuilder(ctx: BuilderContext) {
    val s = JournalStore
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Entry")
        SymbolTextField(s.text, { s.text = it }, "Journal entry (one line per ruling)", Modifier.heightIn(min = 220.dp), marks = true)
        OutlinedTextField(s.date, { s.date = it }, label = { Text("Date corner (blank = none)") }, modifier = Modifier.fillMaxWidth())

        SectionLabel("Hand & page")
        EnumPicker("Handwriting", s.font.label, NoteFont.values().map { it.label }) { s.font = NoteFont.values()[it] }
        LabeledSlider("Text size", s.textScale, 0.6f..1.6f, "%.2f×") { s.textScale = it }
        LabeledSlider("Messiness (crooked / sizes / ink)", s.messiness, 0f..1f) { s.messiness = it }
        LabeledSlider("Scatter (random spots / tilt / flips / order)", s.scatter, 0f..1f) { s.scatter = it }
        BubbleColorRow("Ink", s.ink) { s.ink = it }
        BubbleColorRow("Paper", s.paper) { s.paper = it }

        SectionLabel("Writing & sound")
        LabeledSlider("Writing speed", s.typeSpeed, 0.3f..3f, "%.2f×") { s.typeSpeed = it }
        WritingSoundPicker("Writing sound", s.keySound) { s.keySound = it }
    }
}
