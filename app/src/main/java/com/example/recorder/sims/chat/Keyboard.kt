package com.example.recorder.sims.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ROW1 = "qwertyuiop".toList()
private val ROW2 = "asdfghjkl".toList()
private val ROW3 = "zxcvbnm".toList()

/**
 * A faux phone keyboard whose keys light up as text types ([pressed] = the last
 * char typed). In [emojiMode] it shows the emoji grid and auto-scrolls to
 * [emojiTarget] (a real "go find the emoji, then tap it" motion for reactions).
 */
@Composable
fun PhoneKeyboard(pressed: Char?, emojiMode: Boolean, emojiTarget: Int, dark: Boolean, accent: Color) {
    val bg = if (dark) Color(0xFF1C1C1E) else Color(0xFFD3D6DC)
    Column(Modifier.fillMaxWidth().background(bg).padding(horizontal = 10.dp, vertical = 16.dp)) {
        if (emojiMode) EmojiBoard(emojiTarget, dark, accent)
        else LetterBoard(pressed, dark, accent)
    }
}

@Composable
private fun LetterBoard(pressed: Char?, dark: Boolean, accent: Color) {
    val keyBg = if (dark) Color(0xFF48484A) else Color.White
    val specBg = if (dark) Color(0xFF3A3A3C) else Color(0xFFAEB3BD)
    val txt = if (dark) Color.White else Color(0xFF111111)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ROW1.forEach { LetterKey(it, pressed, keyBg, txt, accent, Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ROW2.forEach { LetterKey(it, pressed, keyBg, txt, accent, Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyBox("⇧", specBg, txt, 40.sp, Modifier.weight(1.5f))
            ROW3.forEach { LetterKey(it, pressed, keyBg, txt, accent, Modifier.weight(1f)) }
            KeyBox("⌫", specBg, txt, 40.sp, Modifier.weight(1.5f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyBox("123", specBg, txt, 28.sp, Modifier.weight(1.6f))
            KeyBox("😊", specBg, txt, 40.sp, Modifier.weight(1f))
            KeyBox("space", if (pressed == ' ') accent else keyBg, if (pressed == ' ') Color.White else txt, 30.sp, Modifier.weight(5f))
            KeyBox("return", specBg, txt, 28.sp, Modifier.weight(2f))
        }
    }
}

@Composable
private fun LetterKey(c: Char, pressed: Char?, keyBg: Color, txt: Color, accent: Color, modifier: Modifier) {
    val on = pressed?.lowercaseChar() == c
    KeyBox(c.uppercase(), if (on) accent else keyBg, if (on) Color.White else txt, 46.sp, modifier)
}

@Composable
private fun KeyBox(label: String, bg: Color, txt: Color, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier) {
    Box(modifier.height(118.dp).clip(RoundedCornerShape(11.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(label, color = txt, fontSize = fontSize, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmojiBoard(target: Int, dark: Boolean, accent: Color) {
    val state = rememberLazyGridState()
    LaunchedEffect(target) { if (target >= 0) state.animateScrollToItem(target.coerceAtLeast(0)) }
    val txt = if (dark) Color.White else Color(0xFF111111)
    Column {
        Text("  Reactions", color = txt.copy(alpha = 0.6f), fontSize = 28.sp, modifier = Modifier.padding(bottom = 8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(8), state = state, modifier = Modifier.fillMaxWidth().height(540.dp)) {
            itemsIndexed(REACTIONS_FULL) { i, e ->
                val on = i == target
                Box(
                    Modifier.padding(6.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) accent.copy(alpha = 0.35f) else Color.Transparent)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e, fontSize = if (on) 62.sp else 50.sp)
                }
            }
        }
    }
}
