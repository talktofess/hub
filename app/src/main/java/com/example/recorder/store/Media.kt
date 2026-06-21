package com.example.recorder.store

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies a picked content:// URI into the app's own storage and returns a stable
 * file:// URI. Photo/file-picker grants aren't always persistable, so copying is
 * what makes picked images/music survive app restarts (and project save/load).
 */
object Media {
    fun copyIntoApp(ctx: Context, uriStr: String, prefix: String = "m"): String? {
        return try {
            val dir = File(ctx.filesDir, "media").apply { mkdirs() }
            val out = File(dir, "${prefix}_${System.nanoTime()}")
            val ok = ctx.contentResolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                out.outputStream().use { input.copyTo(it) }; true
            } ?: false
            if (ok) Uri.fromFile(out).toString() else null
        } catch (_: Throwable) {
            null
        }
    }
}
