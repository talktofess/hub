package com.example.recorder.sims.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.recorder.sims.BuilderContext
import com.example.recorder.ui.BubbleColorRow
import com.example.recorder.ui.EnumPicker
import com.example.recorder.ui.LabeledSlider
import com.example.recorder.ui.SectionLabel
import com.example.recorder.ui.SoundProfilePicker

/** Lists / timeline builder — each block is a card/note/image/video with timing + transitions. */
@Composable
fun ListsBuilder(ctx: BuilderContext) {
    val store = ListsStore
    val smallPad = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    val types = BlockType.entries
    val transes = Trans.entries
    val starts = StartMode.entries
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("List")
        OutlinedTextField(store.heading, { store.heading = it }, label = { Text("Heading (types first)") }, modifier = Modifier.fillMaxWidth())
        BubbleColorRow("Accent (rank)", store.accent) { store.accent = it }
        LabeledSlider("Text size", store.textScale, 0.7f..1.6f, "%.2f×") { store.textScale = it }

        SectionLabel("Typing & sound")
        LabeledSlider("Typing speed", store.typeSpeed, 0.3f..3f, "%.2f×") { store.typeSpeed = it }
        LabeledSlider("Pacing arc", store.pacing, 0f..1f) { store.pacing = it }
        SoundProfilePicker("Keystroke sound", store.keySound) { store.keySound = it }
        LabeledSlider("Gap between blocks", store.cardGap.toFloat(), 0f..2500f, "%.0f ms") { store.cardGap = it.toInt() }

        SectionLabel("Timeline blocks")
        store.items.forEachIndexed { i, b ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(b.type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { store.move(b.id, -1) }, enabled = i > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "up", modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { store.move(b.id, 1) }, enabled = i < store.items.size - 1) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "down", modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { store.remove(b.id) }) { Icon(Icons.Filled.Delete, contentDescription = "delete", modifier = Modifier.size(18.dp)) }
                }
                EnumPicker("Type", b.type.label, types.map { it.label }) { k -> store.update(b.id) { it.copy(type = types[k]) } }

                when (b.type) {
                    BlockType.CARD -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(b.rank, { v -> store.update(b.id) { m -> m.copy(rank = v) } }, label = { Text("Rank") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(b.tier, { v -> store.update(b.id) { m -> m.copy(tier = v) } }, label = { Text("Tier") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(b.score, { v -> store.update(b.id) { m -> m.copy(score = v) } }, label = { Text("Score") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(b.title, { v -> store.update(b.id) { m -> m.copy(title = v) } }, label = { Text("Title (types)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(b.text, { v -> store.update(b.id) { m -> m.copy(text = v) } }, label = { Text("Blurb (types)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(b.badge, { v -> store.update(b.id) { m -> m.copy(badge = v) } }, label = { Text("Badge (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    BlockType.NOTE -> {
                        OutlinedTextField(b.title, { v -> store.update(b.id) { m -> m.copy(title = v) } }, label = { Text("Title (types)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(b.text, { v -> store.update(b.id) { m -> m.copy(text = v) } }, label = { Text("Body (types)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(b.badge, { v -> store.update(b.id) { m -> m.copy(badge = v) } }, label = { Text("Badge (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { store.select(b.id); ctx.pickImage() }, contentPadding = smallPad) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" ${if (b.imageUri != null) "Change" else "Add"} image", style = MaterialTheme.typography.labelMedium)
                            }
                            if (b.imageUri != null) TextButton(onClick = { store.update(b.id) { it.copy(imageUri = null) } }) { Text("Remove") }
                        }
                        OutlinedTextField(b.title, { v -> store.update(b.id) { m -> m.copy(title = v) } }, label = { Text("Caption (types, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (b.type == BlockType.VIDEO) {
                            OutlinedTextField(b.duration, { v -> store.update(b.id) { m -> m.copy(duration = v) } }, label = { Text("Duration chip (e.g. 0:14)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                Text("Timing & transition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                EnumPicker("Starts", b.start.label, starts.map { it.label }) { k -> store.update(b.id) { it.copy(start = starts[k]) } }
                LabeledSlider("Start delay", b.startDelay.toFloat(), 0f..3000f, "%.0f ms") { v -> store.update(b.id) { it.copy(startDelay = v.toInt()) } }
                EnumPicker("Enter", b.enter.label, transes.map { it.label }) { k -> store.update(b.id) { it.copy(enter = transes[k]) } }
                LabeledSlider("Hold then exit (0 = stays)", b.hold.toFloat(), 0f..6000f, "%.0f ms") { v -> store.update(b.id) { it.copy(hold = v.toInt()) } }
                if (b.hold > 0) EnumPicker("Exit", b.exit.label, transes.map { it.label }) { k -> store.update(b.id) { it.copy(exit = transes[k]) } }
            }
        }
        OutlinedButton(onClick = { store.add() }, contentPadding = smallPad) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" Add block", style = MaterialTheme.typography.labelMedium)
        }
        Text("“With previous” makes a block appear at the same time as the one before it. Hold > 0 makes it exit after that long; 0 keeps it on screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
