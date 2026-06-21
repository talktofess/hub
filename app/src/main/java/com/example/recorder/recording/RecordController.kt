package com.example.recorder.recording

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide, Compose-observable recording state, plus the handle to the live
 * in-process [Recorder]. The Activity creates/stops the recorder; PresentScreen's
 * capture loop pushes frames to [recorder]; the UI reads the flags.
 */
object RecordController {
    var isRecording by mutableStateOf(false)
        internal set

    /** Bumps each time a take is saved — the UI watches it to show a confirmation. */
    var savedCount by mutableIntStateOf(0)
        internal set

    var lastSavedName by mutableStateOf<String?>(null)
        internal set

    var lastError by mutableStateOf<String?>(null)
        internal set

    /** The active recorder — PresentScreen feeds frames here while recording. */
    @Volatile
    var recorder: Recorder? = null
        internal set
}
