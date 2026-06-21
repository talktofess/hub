package com.example.recorder.recording

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Publishes a finished MP4 into the gallery under Movies/SimHub via MediaStore.
 * Requires API 29+ (scoped-storage insert). Returns the display name.
 */
object Gallery {
    fun save(ctx: Context, file: File): String {
        val name = "SimHub_${file.lastModified()}.mp4"
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SimHub")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val uri = resolver.insert(collection, values) ?: error("no MediaStore uri")
        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out)
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return name
    }

    /** Gallery save needs scoped-storage MediaStore (API 29+). */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
