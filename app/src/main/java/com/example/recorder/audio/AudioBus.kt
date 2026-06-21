package com.example.recorder.audio

/**
 * Process-wide singleton for the keystroke audio engine. The active sim plays
 * through it, and [com.example.recorder.recording.Recorder] taps the same engine
 * so the recording's audio is bit-identical to what the user hears.
 */
object AudioBus {
    val engine = AudioEngine()
}
