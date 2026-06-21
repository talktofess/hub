package com.example.recorder.sims.email

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.recorder.sims.BuilderContext
import com.example.recorder.sims.notes.NoteFont
import com.example.recorder.ui.BubbleColorRow
import com.example.recorder.ui.EnumPicker
import com.example.recorder.ui.LabeledSlider
import com.example.recorder.ui.SectionLabel
import com.example.recorder.ui.SoundProfilePicker

/** Email (desktop Gmail) builder — the compose message + the inbox behind it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailBuilder(ctx: BuilderContext) {
    val store = EmailStore
    val smallPad = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Compose")
        OutlinedTextField(store.from, { store.from = it }, label = { Text("Your name (avatar initials)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(store.account, { store.account = it }, label = { Text("From (your email)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(store.to, { store.to = it }, label = { Text("To") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show Cc", Modifier.weight(1f)); Switch(checked = store.showCc, onCheckedChange = { store.showCc = it })
        }
        if (store.showCc) OutlinedTextField(store.cc, { store.cc = it }, label = { Text("Cc") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(store.subject, { store.subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(store.body, { store.body = it }, label = { Text("Body (types out)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
        OutlinedTextField(store.attachment, { store.attachment = it }, label = { Text("Attachment filename (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SectionLabel("Gmail look")
        BubbleColorRow("Accent", store.accent) { store.accent = it }
        LabeledSlider("Text size", store.textScale, 0.9f..1.8f, "%.2f×") { store.textScale = it }
        EnumPicker("Body font", store.font.label, NoteFont.entries.map { it.label }) { i -> store.font = NoteFont.entries[i] }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark mode", Modifier.weight(1f)); Switch(checked = store.dark, onCheckedChange = { store.dark = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show sidebar", Modifier.weight(1f)); Switch(checked = store.sidebar, onCheckedChange = { store.sidebar = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Center the compose popup", Modifier.weight(1f)); Switch(checked = store.composeCenter, onCheckedChange = { store.composeCenter = it })
        }

        SectionLabel("Typing & sound")
        LabeledSlider("Typing speed", store.typeSpeed, 0.3f..3f, "%.2f×") { store.typeSpeed = it }
        SoundProfilePicker("Keystroke sound", store.keySound) { store.keySound = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Send / sent chime", Modifier.weight(1f)); Switch(checked = store.soundsOn, onCheckedChange = { store.soundsOn = it })
        }

        SectionLabel("Inbox (behind the popup)")
        store.inbox.forEachIndexed { i, r ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilterChip(selected = r.unread, onClick = { store.updateRow(i, r.copy(unread = !r.unread)) }, label = { Text("Unread") })
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { store.removeRow(i) }) { Icon(Icons.Filled.Delete, contentDescription = "delete", modifier = Modifier.size(18.dp)) }
                }
                OutlinedTextField(r.from, { store.updateRow(i, r.copy(from = it)) }, label = { Text("Sender") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(r.subject, { store.updateRow(i, r.copy(subject = it)) }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(r.snippet, { store.updateRow(i, r.copy(snippet = it)) }, label = { Text("Preview") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(r.time, { store.updateRow(i, r.copy(time = it)) }, label = { Text("Time") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        OutlinedButton(onClick = { store.addRow() }, contentPadding = smallPad) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" Add inbox row", style = MaterialTheme.typography.labelMedium)
        }
    }
}
