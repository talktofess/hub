package com.example.recorder.sims.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reaction (tapback) emoji offered in the builders. */
val REACTIONS = listOf("❤️", "👍", "👎", "😂", "😮", "🙏")

/** The full emoji lineup for reactions + the on-screen emoji keyboard. */
val REACTIONS_FULL = listOf(
    "❤️", "😂", "👍", "👎", "‼️", "❓", "😍", "🥰", "😘", "😎", "🤔", "😮", "😢", "😭", "😡", "🥺",
    "🙏", "👏", "🙌", "🔥", "💯", "🎉", "✨", "💀", "👀", "🤣", "😅", "😊", "🙂", "😉", "😏", "😴",
    "🤯", "🤩", "😱", "🤗", "🤭", "😬", "🙄", "😤", "💪", "🫶", "🤝", "✌️", "🤞", "🫡", "💖", "💔",
    "⭐", "⚡", "☀️", "🌙", "💩", "🐐", "🍕", "☕", "🎂", "🍻", "🚀", "💸", "📈", "✅", "❌", "💬",
)

/** True if the text is only emoji / symbols (rendered large, without a bubble). */
fun isEmojiOnly(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.length > 8) return false
    return t.none { it.isLetterOrDigit() } && t.any { it.code > 0x2000 }
}

/** A faux phone status bar (clock + signal + battery) for realistic recordings. */
@Composable
fun StatusBar(clock: String, onDark: Boolean) {
    val c = if (onDark) Color.White else Color(0xFF111111)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clock, color = c, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(width = 38.dp, height = 24.dp)) {
                val bars = 4; val gap = size.width * 0.12f
                val bw = (size.width - gap * (bars - 1)) / bars
                for (i in 0 until bars) {
                    val h = size.height * (0.45f + 0.18f * i)
                    drawRoundRect(c, topLeft = Offset(i * (bw + gap), size.height - h), size = Size(bw, h), cornerRadius = CornerRadius(2f, 2f))
                }
            }
            Canvas(Modifier.size(width = 50.dp, height = 24.dp)) {
                val bodyW = size.width * 0.86f
                drawRoundRect(c, topLeft = Offset(0f, 0f), size = Size(bodyW, size.height), cornerRadius = CornerRadius(6f, 6f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                drawRoundRect(c, topLeft = Offset(3f, 3f), size = Size((bodyW - 6f) * 0.8f, size.height - 6f), cornerRadius = CornerRadius(3f, 3f))
                drawRoundRect(c, topLeft = Offset(bodyW + 2f, size.height * 0.3f), size = Size(size.width - bodyW - 2f, size.height * 0.4f), cornerRadius = CornerRadius(2f, 2f))
            }
        }
    }
}

/** A centered date/time separator chip. */
@Composable
fun DateChip(text: String, bg: Color, fg: Color) {
    if (text.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text(text, color = fg, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** A small reaction badge that overlaps a bubble's top corner. */
@Composable
fun ReactionBadge(emoji: String, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(emoji, fontSize = 26.sp)
    }
}
