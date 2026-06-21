package com.example.recorder.sims.typer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.recorder.sims.BuilderContext
import com.example.recorder.ui.BubbleColorRow
import com.example.recorder.ui.LabeledSlider
import com.example.recorder.ui.SectionLabel
import com.example.recorder.ui.SoundProfilePicker

@Composable
fun TyperBuilder(ctx: BuilderContext) {
    val store = TyperStore
    val smallPad = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Cards (each types, holds, then clears)")
        store.cards.forEachIndexed { i, card ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { store.move(i, -1) }, enabled = i > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "up", modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { store.move(i, 1) }, enabled = i < store.cards.size - 1) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "down", modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { store.remove(i) }) { Icon(Icons.Filled.Delete, contentDescription = "delete", modifier = Modifier.size(18.dp)) }
                }
                OutlinedTextField(card, { store.update(i, it) }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
            }
        }
        OutlinedButton(onClick = { store.add() }, contentPadding = smallPad) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" Add card", style = MaterialTheme.typography.labelMedium)
        }

        SectionLabel("Style")
        LabeledSlider("Text size", store.textScale, 0.6f..1.6f, "%.2f×") { store.textScale = it }
        BubbleColorRow("Text color", store.color) { store.color = it }

        SectionLabel("Typing & sound")
        LabeledSlider("Typing speed", store.typeSpeed, 0.3f..3f, "%.2f×") { store.typeSpeed = it }
        LabeledSlider("Pacing arc", store.pacing, 0f..1f) { store.pacing = it }
        SoundProfilePicker("Keystroke sound", store.keySound) { store.keySound = it }
        LabeledSlider("Hold each card", store.holdMs.toFloat(), 200f..5000f, "%.0f ms") { store.holdMs = it.toInt() }
    }
}
