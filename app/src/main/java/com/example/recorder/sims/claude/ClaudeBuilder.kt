package com.example.recorder.sims.claude

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
fun ClaudeBuilder(ctx: BuilderContext) {
    val s = ClaudeStore
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Conversation")
        OutlinedTextField(s.prompt, { s.prompt = it }, label = { Text("Your prompt") }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp))
        OutlinedTextField(s.reply, { s.reply = it }, label = { Text("Claude's reply (streams in)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp))

        SectionLabel("Thinking")
        LabeledSlider("Thinking time", s.thinkMs / 1000f, 0.4f..15f, "%.1fs") { s.thinkMs = (it * 1000).toInt() }
        LabeledSlider("Tokens counted (↑)", s.tokenTarget / 1000f, 0.2f..20f, "%.1fk") { s.tokenTarget = (it * 1000).toInt() }
        OutlinedTextField(s.verb, { s.verb = it }, label = { Text("Thinking verb (blank = cycles: Inferring, Lollygagging…)") }, modifier = Modifier.fillMaxWidth())

        SectionLabel("Welcome box")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show welcome box", Modifier.weight(1f))
            Switch(checked = s.welcomeBox, onCheckedChange = { s.welcomeBox = it })
        }
        OutlinedTextField(s.name, { s.name = it }, label = { Text("Name (\"Welcome back …\")") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.model, { s.model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.account, { s.account = it }, label = { Text("Account / org") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.cwd, { s.cwd = it }, label = { Text("Working directory") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.version, { s.version = it }, label = { Text("Version") }, modifier = Modifier.fillMaxWidth())

        SectionLabel("Typing & sound")
        LabeledSlider("Prompt typing speed", s.typeSpeed, 0.3f..3f, "%.2f×") { s.typeSpeed = it }
        LabeledSlider("Pacing arc", s.pacing, 0f..1f) { s.pacing = it }
        LabeledSlider("Reply stream speed", s.streamSpeed, 0.3f..3f, "%.2f×") { s.streamSpeed = it }
        SoundProfilePicker("Keystroke sound", s.keySound) { s.keySound = it }
    }
}
