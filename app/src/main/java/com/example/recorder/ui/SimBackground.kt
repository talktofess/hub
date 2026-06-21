package com.example.recorder.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.recorder.engine.SimRuntime
import com.example.recorder.model.BgKind
import com.example.recorder.model.BgScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Universal background drawn behind the sim content (behind the notes). Honors the
 * [com.example.recorder.model.BgMedia] settings: fit mode, opacity, and timing
 * (always on, or fade in N seconds into the take). Rendered inside the logical
 * stage so it's captured in recordings.
 *
 * @param previewMode true in the editor (always show so the user can position it),
 *   false in present/record mode (honor the appear-at timing).
 */
@Composable
fun SimBackground(rt: SimRuntime, previewMode: Boolean) {
    val bg = rt.settings.bg
    if (bg.uri == null || bg.kind != BgKind.IMAGE) return // video bg: coming next

    val ctx = LocalContext.current
    var image by remember(bg.uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(bg.uri) {
        image = withContext(Dispatchers.IO) { loadBitmap(ctx, bg.uri) }
    }
    val bmp = image ?: return

    var appeared by remember { mutableStateOf(bg.appearAtSec < 0f) }
    LaunchedEffect(rt.playing, bg.appearAtSec) {
        appeared = when {
            bg.appearAtSec < 0f -> true
            !rt.playing -> false
            else -> { delay((bg.appearAtSec * 1000).toLong()); true }
        }
    }

    val visible = bg.appearAtSec < 0f || appeared || (previewMode && !rt.playing)
    if (!visible) return

    Image(
        bitmap = bmp,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = when (bg.scale) {
            BgScale.COVER -> ContentScale.Crop
            BgScale.CONTAIN -> ContentScale.Fit
            BgScale.FILL -> ContentScale.FillBounds
            BgScale.CENTER -> ContentScale.None
        },
        alpha = bg.opacity,
    )
}

private fun loadBitmap(ctx: Context, uriStr: String): ImageBitmap? = try {
    ctx.contentResolver.openInputStream(Uri.parse(uriStr)).use { input ->
        BitmapFactory.decodeStream(input)?.asImageBitmap()
    }
} catch (_: Throwable) {
    null
}
