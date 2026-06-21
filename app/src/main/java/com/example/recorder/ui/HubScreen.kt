package com.example.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.recorder.sims.SimDef

/** The launcher: a grid of sims. Tap a card to open that sim (its own Activity);
 *  tap its ▶ to open it straight on the stage. */
@Composable
fun HubScreen(
    sims: List<SimDef>,
    onOpenSim: (SimDef) -> Unit,
    onPlaySim: (SimDef) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sim Hub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Pick an app to build & record", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(sims) { sim -> HubCard(sim, onOpen = { onOpenSim(sim) }, onPlay = { onPlaySim(sim) }) }
        }
    }
}

private fun iconFor(id: String): ImageVector = when (id) {
    "notes" -> Icons.Outlined.StickyNote2
    "imessage" -> Icons.Outlined.ChatBubbleOutline
    "whatsapp" -> Icons.Outlined.Chat
    "email" -> Icons.Outlined.MailOutline
    "lists" -> Icons.Outlined.Leaderboard
    "typer" -> Icons.Outlined.TextFields
    "typewriter" -> Icons.Outlined.Keyboard
    "claude" -> Icons.Outlined.Terminal
    "journal" -> Icons.Outlined.MenuBook
    else -> Icons.Outlined.TextFields
}

@Composable
private fun HubCard(sim: SimDef, onOpen: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen() }
            .padding(start = 14.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconFor(sim.id), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(sim.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Build & record", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF45454D)).clickable { onPlay() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "play ${sim.label}", tint = Color(0xFFE7E7EA), modifier = Modifier.size(18.dp))
        }
    }
}
