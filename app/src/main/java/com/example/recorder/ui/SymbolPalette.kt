package com.example.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SYMBOLS = (
    "★ ☆ ♥ ♡ ❤ ✓ ✔ ✗ ✘ ☑ ☐ ❗ ❓ → ← ↑ ↓ ↻ ☺ ☹ ❀ ✿ ❄ ☀ ☾ ♪ ♫ ⚡ ✦ ✧ ➜ ✎ ✏ § ¶ ❝ ❞ … — • ◦ ‣ ✷"
    ).split(" ")

/** An entry field with a cursor-aware symbol palette below it. Symbols insert at the
 *  caret. When [marks] is on, mark chips wrap the selection ([o]/[u]/[x] + divider) —
 *  used by the Journal, whose renderer draws those marks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymbolTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    marks: Boolean = false,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) { if (value != tfv.text) tfv = TextFieldValue(value, TextRange(value.length)) }
    fun set(v: TextFieldValue) { tfv = v; onValue(v.text) }
    fun insert(str: String) {
        val a = tfv.selection.min; val b = tfv.selection.max
        val nt = tfv.text.substring(0, a) + str + tfv.text.substring(b)
        set(TextFieldValue(nt, TextRange(a + str.length)))
    }
    fun wrap(pre: String, post: String) {
        val a = tfv.selection.min; val b = tfv.selection.max
        val sel = tfv.text.substring(a, b)
        val nt = tfv.text.substring(0, a) + pre + sel + post + tfv.text.substring(b)
        set(TextFieldValue(nt, TextRange(a + pre.length + sel.length)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(tfv, { set(it) }, label = { Text(label) }, modifier = modifier.fillMaxWidth())
        if (marks) {
            Text("Select a word, then tap a mark to draw it on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SymChip("◯ circle") { wrap("[o]", "[/o]") }
                SymChip("U̲ underline") { wrap("[u]", "[/u]") }
                SymChip("✗ cross out") { wrap("[x]", "[/x]") }
                SymChip("— divider") { insert("\n---\n") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SYMBOLS.forEach { sym -> SymChip(sym) { insert(sym) } }
        }
    }
}

@Composable
fun SymChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
