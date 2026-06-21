package com.example.recorder.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decode a content/file URI into an ImageBitmap (off the main thread). */
fun loadUriBitmap(ctx: Context, uriStr: String): ImageBitmap? = try {
    ctx.contentResolver.openInputStream(Uri.parse(uriStr)).use { input ->
        BitmapFactory.decodeStream(input)?.asImageBitmap()
    }
} catch (_: Throwable) {
    null
}

/** Remember the decoded bitmap for [uri], reloading when it changes. */
@Composable
fun rememberUriBitmap(uri: String?): ImageBitmap? {
    val ctx = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = if (uri == null) null else withContext(Dispatchers.IO) { loadUriBitmap(ctx, uri) }
    }
    return bmp
}
