package com.example.recorder.sims.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.ui.EnumPicker
import com.example.recorder.ui.LabeledSlider
import kotlin.math.roundToInt

/** A push notification from another conversation that slides in during the take. */
class ChatNotif(sender0: String, text0: String, after0: Int, sound0: String) {
    var sender by mutableStateOf(sender0)
    var text by mutableStateOf(text0)
    var after by mutableIntStateOf(after0)  // appears after this message index (0-based)
    var sound by mutableStateOf(sound0)
}

/** Cue names available for notification sounds (see AudioEngine.cue). */
val NOTIF_CUES = listOf("tritone", "pop", "ding", "whoosh")

/** A heads-up notification banner (iOS-ish when [ios], else Android-ish). */
@Composable
fun NotificationBanner(sender: String, text: String, ios: Boolean, fs: Float, modifier: Modifier = Modifier) {
    val bg = if (ios) Color(0xF7FFFFFF) else Color(0xFF2C2C30)
    val title = if (ios) Color(0xFF111114) else Color.White
    val sub = if (ios) Color(0xFF55555C) else Color(0xFFCFCFD6)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(36.dp)).background(bg).padding(horizontal = 26.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(if (ios) Color(0xFF34C759) else Color(0xFF25D366)), contentAlignment = Alignment.Center) {
            Text("💬", fontSize = (38f * fs).sp)
        }
        Column(Modifier.weight(1f)) {
            Text(sender, color = title, fontSize = (32f * fs).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text, color = sub, fontSize = (29f * fs).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("now", color = sub, fontSize = (24f * fs).sp)
    }
}

/** Builder UI for the cross-thread notifications. */
@Composable
fun NotifEditor(notifs: SnapshotStateList<ChatNotif>, msgCount: Int) {
    notifs.forEachIndexed { i, n ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pop-up ${i + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { if (i in notifs.indices) notifs.removeAt(i) }) { Text("Remove") }
            }
            OutlinedTextField(n.sender, { n.sender = it }, label = { Text("From") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(n.text, { n.text = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
            val maxAfter = (msgCount - 1).coerceAtLeast(0)
            LabeledSlider("Pops in after message #${n.after + 1}", n.after.toFloat().coerceIn(0f, maxAfter.toFloat()), 0f..maxAfter.toFloat().coerceAtLeast(1f), "%.0f") { n.after = it.roundToInt() }
            EnumPicker("Sound", n.sound, NOTIF_CUES) { n.sound = NOTIF_CUES[it] }
        }
    }
    OutlinedButton(onClick = { notifs.add(ChatNotif("Mom", "Call me when you're free ❤️", (msgCount / 2).coerceAtLeast(0), "tritone")) }) {
        Text("+ Add notification")
    }
}
