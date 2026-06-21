package com.example.recorder.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Universal settings that apply to every sim and every take (recording quality),
 * edited in the hub's Settings screen and persisted to SharedPreferences.
 */
object AppSettings {
    private var prefs: android.content.SharedPreferences? = null

    var fps by mutableIntStateOf(30)            // recording frame rate
        private set
    var recordScale by mutableFloatStateOf(1f)  // supersample: 1× = 1080p, 2× ≈ 4K
        private set

    fun load(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs = p
        fps = p.getInt("fps", 30)
        recordScale = p.getFloat("recordScale", 1f)
    }

    fun updateFps(v: Int) { fps = v.coerceIn(15, 60); prefs?.edit()?.putInt("fps", fps)?.apply() }
    fun updateRecordScale(v: Float) { recordScale = v.coerceIn(1f, 3f); prefs?.edit()?.putFloat("recordScale", recordScale)?.apply() }
}
