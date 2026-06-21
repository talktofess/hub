package com.example.recorder.sims.typewriter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recorder.sims.BuilderContext
import com.example.recorder.ui.LabeledSlider
import com.example.recorder.ui.SectionLabel
import com.example.recorder.ui.SoundProfilePicker

@Composable
fun TypewriterBuilder(ctx: BuilderContext) {
    val store = TypewriterStore
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Page")
        OutlinedTextField(store.text, { store.text = it }, label = { Text("Text (each new line = carriage return)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp))
        LabeledSlider("Text size", store.textScale, 0.6f..1.6f, "%.2f×") { store.textScale = it }

        SectionLabel("Typing & sound")
        LabeledSlider("Typing speed", store.typeSpeed, 0.3f..3f, "%.2f×") { store.typeSpeed = it }
        LabeledSlider("Pacing arc", store.pacing, 0f..1f) { store.pacing = it }
        SoundProfilePicker("Keystroke sound", store.keySound) { store.keySound = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Carriage-return bell", Modifier.weight(1f))
            Switch(checked = store.bell, onCheckedChange = { store.bell = it })
        }
    }
}
