package com.example.recorder.sims.whatsapp

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.recorder.sims.BuilderContext
import com.example.recorder.sims.notes.NoteFont
import com.example.recorder.ui.BubbleColorRow
import com.example.recorder.ui.CuePicker
import com.example.recorder.ui.EnumPicker
import com.example.recorder.ui.LabeledSlider
import com.example.recorder.ui.ReactionPicker
import com.example.recorder.ui.SectionLabel
import com.example.recorder.ui.SoundProfilePicker

/** WhatsApp builder: contact, status, chat style, typing/sound, chrome, and the message list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppBuilder(ctx: BuilderContext) {
    val store = WhatsAppStore
    val smallPad = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Conversation")
        OutlinedTextField(store.contactName, { store.setContact(it) }, label = { Text("Contact name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(store.status, { store.status = it }, label = { Text("Status (e.g. online, last seen…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(store.stamp, { store.stamp = it }, label = { Text("Message time stamp") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = ctx.pickAvatar, contentPadding = smallPad) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" ${if (store.avatarUri != null) "Change" else "Profile"} photo", style = MaterialTheme.typography.labelMedium)
            }
            if (store.avatarUri != null) TextButton(onClick = { store.avatarUri = null }) { Text("Remove") }
        }

        SectionLabel("Chat style")
        EnumPicker("Font", store.font.label, NoteFont.entries.map { it.label }) { i -> store.font = NoteFont.entries[i] }
        LabeledSlider("Text size", store.textScale, 0.7f..1.5f, "%.2f×") { store.textScale = it }
        BubbleColorRow("Sent bubble", store.sentColor) { store.sentColor = it }
        BubbleColorRow("Received bubble", store.receivedColor) { store.receivedColor = it }
        BubbleColorRow("Sent text", store.sentTextColor) { store.sentTextColor = it }
        BubbleColorRow("Received text", store.receivedTextColor) { store.receivedTextColor = it }
        BubbleColorRow("Wallpaper", store.wallpaper) { store.wallpaper = it }

        SectionLabel("Typing & sound")
        LabeledSlider("Typing speed", store.typeSpeed, 0.3f..3f, "%.2f×") { store.typeSpeed = it }
        LabeledSlider("Pacing arc", store.pacing, 0f..1f) { store.pacing = it }
        SoundProfilePicker("Keystroke sound", store.keySound) { store.keySound = it }
        CuePicker("Send sound", store.sendSound) { store.sendSound = it }
        CuePicker("Receive sound", store.receiveSound) { store.receiveSound = it }
        LabeledSlider("Gap between messages", store.msgGap.toFloat(), 0f..2500f, "%.0f ms") { store.msgGap = it.toInt() }
        LabeledSlider("Typing indicator", store.typingDur.toFloat(), 200f..3000f, "%.0f ms") { store.typingDur = it.toInt() }

        SectionLabel("Chrome")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("On-screen keyboard (keys type)", Modifier.weight(1f))
            Switch(checked = store.showKeyboard, onCheckedChange = { store.showKeyboard = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Phone status bar", Modifier.weight(1f))
            Switch(checked = store.statusBar, onCheckedChange = { store.statusBar = it })
        }
        OutlinedTextField(store.clock, { store.clock = it }, label = { Text("Status-bar clock") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(store.dateLabel, { store.dateLabel = it }, label = { Text("Date separator") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Blue read ticks", Modifier.weight(1f))
            Switch(checked = store.readReceipt, onCheckedChange = { store.readReceipt = it })
        }

        SectionLabel("Messages")
        store.messages.forEachIndexed { i, m ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(selected = m.fromMe, onClick = { store.update(m.id) { it.copy(fromMe = true) } }, label = { Text("Me") })
                    FilterChip(selected = !m.fromMe, onClick = { store.update(m.id) { it.copy(fromMe = false) } }, label = { Text("Them") })
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { store.move(m.id, -1) }, enabled = i > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "up", modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { store.move(m.id, 1) }, enabled = i < store.messages.size - 1) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "down", modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { store.remove(m.id) }) { Icon(Icons.Filled.Delete, contentDescription = "delete", modifier = Modifier.size(18.dp)) }
                }
                OutlinedTextField(m.text, { v -> store.update(m.id) { it.copy(text = v) } }, label = { Text(if (m.fromMe) "Sent message" else "Received message") }, modifier = Modifier.fillMaxWidth())
                LabeledSlider("Pause before", m.delayMs.toFloat(), 0f..3000f, "%.0f ms") { v -> store.update(m.id) { it.copy(delayMs = v.toInt()) } }
                ReactionPicker(m.reaction) { r -> store.update(m.id) { it.copy(reaction = r) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { store.select(m.id); ctx.pickImage() }, contentPadding = smallPad) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" ${if (m.imageUri != null) "Change" else "Add"} image", style = MaterialTheme.typography.labelMedium)
                    }
                    if (m.imageUri != null) TextButton(onClick = { store.update(m.id) { it.copy(imageUri = null) } }) { Text("Remove") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { store.add(true) }, contentPadding = smallPad) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" Me", style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(onClick = { store.add(false) }, contentPadding = smallPad) { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" Them", style = MaterialTheme.typography.labelMedium) }
        }
    }
}
