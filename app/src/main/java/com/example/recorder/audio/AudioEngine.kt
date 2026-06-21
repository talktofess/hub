package com.example.recorder.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.recorder.model.SoundProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Keystroke audio engine. Port of src/recording/audio.ts (Web Audio) to a
 * continuous [AudioTrack] mixer with procedural synthesis.
 *
 * The web graph (noise/oscillator nodes + biquad filters + exponential gain
 * envelopes -> master -> destination) is reproduced sample-by-sample: each key()
 * synthesizes a short mono buffer for the active profile and mixes it into a
 * 1-second ring that a playback thread streams out. Because mixes are additive,
 * fast keystrokes overlap their tails exactly like the original.
 */
class AudioEngine {
    private val sr = 44100
    private val ring = FloatArray(sr) // 1 s mix buffer
    private val lock = Object()
    private var playCursor = 0L       // absolute frames handed to the track

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    var profile: SoundProfile = SoundProfile.MECHANICAL

    /** Speaker (live monitor) gain — set to 0 to play silently while still recording. */
    @Volatile var monitorGain: Float = 0.85f
    /** Gain baked into the recording tap, independent of what the speaker plays. */
    @Volatile var recordGain: Float = 0.85f

    /**
     * Optional recording tap. The render thread hands every output block to this
     * sink (scaled by [recordGain], independent of the speaker's [monitorGain]) so
     * a recorder can encode the sim audio even when monitoring is silent. The
     * callback MUST consume synchronously (copy out).
     */
    @Volatile var pcmSink: ((ShortArray, Int) -> Unit)? = null

    /** Looping background music/ambience bed (mono 44.1k PCM); mixed into both the
        speaker and the recording. Null = no bed. */
    @Volatile private var bed: ShortArray? = null
    @Volatile var bedGain: Float = 0.5f
    private var bedPos = 0

    fun setBed(pcm: ShortArray?) {
        bed = pcm
        bedPos = 0
    }

    /** Start the playback thread + AudioTrack. Idempotent. */
    fun resume() {
        if (running) return
        running = true
        val minBuf = AudioTrack.getMinBufferSize(
            sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sr / 10 * 2)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf)
            .build()
        track = t
        t.play()
        thread = Thread({ renderLoop(t) }, "sim-audio").also { it.start() }
    }

    fun release() {
        running = false
        thread?.join(500)
        thread = null
        try { track?.stop() } catch (_: Throwable) {}
        track?.release()
        track = null
        synchronized(lock) { ring.fill(0f); playCursor = 0 }
    }

    private fun pcm(x: Float): Short {
        val c = if (x > 1f) 1f else if (x < -1f) -1f else x
        return (c * 32767f).toInt().toShort()
    }

    private fun renderLoop(t: AudioTrack) {
        val block = 512
        val out = ShortArray(block) // speaker (monitorGain)
        val tap = ShortArray(block) // recorder (recordGain)
        while (running) {
            val sink = pcmSink
            val curBed = bed
            val bg = bedGain
            synchronized(lock) {
                val base = (playCursor % sr).toInt()
                val mg = monitorGain
                val rg = recordGain
                for (i in 0 until block) {
                    val idx = (base + i) % sr
                    val s = ring[idx]
                    ring[idx] = 0f
                    var bedS = 0f
                    if (curBed != null && curBed.isNotEmpty() && bg > 0f) {
                        bedS = (curBed[bedPos] / 32768f) * bg
                        bedPos = (bedPos + 1) % curBed.size
                    }
                    out[i] = pcm(s * mg + bedS)
                    if (sink != null) tap[i] = pcm(s * rg + bedS)
                }
                playCursor += block
            }
            sink?.invoke(tap, block) // recorder gets its own gain, even if speaker is muted
            t.write(out, 0, block)   // blocking; paces the loop
        }
    }

    /** Mix a freshly-synthesized buffer in at the current render position. */
    private fun emit(buf: FloatArray) {
        if (!running) return
        synchronized(lock) {
            val start = playCursor
            val n = minOf(buf.size, sr)
            for (i in 0 until n) {
                val idx = ((start + i) % sr).toInt()
                ring[idx] += buf[i]
            }
        }
    }

    /** Play one keystroke using the active profile. Synthesized at unit level; the
        speaker/record gains are applied at the output stage in [renderLoop]. */
    fun key() {
        if (profile == SoundProfile.NONE) return
        if (monitorGain <= 0f && recordGain <= 0f) return
        val v = 1f
        val buf = when (profile) {
            SoundProfile.TYPEWRITER -> synthTypewriter(v)
            SoundProfile.SOFT -> synthSoft(v)
            SoundProfile.TACTILE -> synthTactile(v)
            SoundProfile.BLUE -> synthBlue(v)
            SoundProfile.VINTAGE -> synthVintage(v)
            SoundProfile.BUBBLE -> synthBubble(v)
            SoundProfile.MUSH -> synthMush(v)
            SoundProfile.PENCIL -> synthPencil(v)
            SoundProfile.CREAMY -> synthCreamy(v)
            SoundProfile.CLACKY -> synthClacky(v)
            SoundProfile.GLASS -> synthGlass(v)
            SoundProfile.THUD -> synthThud(v)
            SoundProfile.PEN -> synthPen(v)
            SoundProfile.KEYBOARD -> synthKeyboard(v)
            SoundProfile.NONE -> return
            else -> synthMechanical(v)
        }
        emit(buf)
    }

    /** A one-off non-keystroke sound (carriage-return bell + swipe, page turn). */
    fun cue(name: String) {
        if (monitorGain <= 0f && recordGain <= 0f) return
        when (name) {
            "return", "ding" -> emit(synthCarriageReturn(1f, name == "return"))
            "whoosh" -> emit(synthWhoosh(1f))
            "tritone" -> emit(synthTritone(1f))
            "pop" -> emit(synthPop(1f))
            else -> emit(synthSoft(1f))
        }
    }

    // iMessage "send" whoosh — a quick upward band of noise + rising tone.
    private fun synthWhoosh(v: Float): FloatArray {
        val dur = 0.2
        val buf = FloatArray(frames(dur + 0.02))
        val n = noise(dur, 1.0)
        filter(n, "bp", 1400.0, 0.8) // mid band; the rising tone carries the sweep feel
        addNoiseEnv(buf, n, 0.02, 0.10 * v, dur)
        addOsc(buf, "sine", 320.0, 1500.0, 0.01, 0.14 * v, 0.18, fRampDur = 0.16)
        return buf
    }

    // Phone keyboard tap — soft, short, slightly woody (iOS-style). Two short
    // overlaid blips: a high tick + a low body, both fast-decaying.
    private fun synthKeyboard(v: Float): FloatArray {
        val buf = FloatArray(frames(0.05))
        val n = noise(0.01, 2.4); filter(n, "bp", 1900 + Random.nextDouble() * 300, 0.9)
        addNoiseEnv(buf, n, 0.001, 0.16 * v, 0.01)
        addOsc(buf, "sine", 430 + Random.nextDouble() * 40, 0.0, 0.001, 0.18 * v, 0.034)
        addOsc(buf, "sine", 1150.0, 0.0, 0.001, 0.07 * v, 0.018)
        return buf
    }

    // iMessage "receive" tri-tone — three quick ascending bell notes (clear + bright).
    private fun synthTritone(v: Float): FloatArray {
        val buf = FloatArray(frames(0.62))
        val notes = doubleArrayOf(784.0, 1047.0, 1319.0) // G5 C6 E6
        notes.forEachIndexed { i, f ->
            val start = (i * 0.085 * sr).toInt()
            val sub = FloatArray(frames(0.34))
            addOsc(sub, "sine", f, 0.0, 0.004, 0.34 * v, 0.3)
            addOsc(sub, "sine", f * 2.0, 0.0, 0.004, 0.10 * v, 0.18) // a little shimmer
            for (j in sub.indices) { val idx = start + j; if (idx < buf.size) buf[idx] += sub[j] }
        }
        return buf
    }

    // A simple "message received" pop — a quick rising two-note bloop.
    private fun synthPop(v: Float): FloatArray {
        val buf = FloatArray(frames(0.2))
        addOsc(buf, "sine", 520.0, 880.0, 0.004, 0.34 * v, 0.16, fRampDur = 0.07)
        return buf
    }

    // ---------- DSP helpers ----------

    /** Exponential gain envelope mirroring Web Audio's exponentialRampToValueAtTime
        chain: ~0 -> peak over [0,a], peak -> ~0 over [a,d]. */
    private fun env(t: Double, a: Double, peak: Double, d: Double): Double {
        val lo = 0.0001
        return when {
            t <= 0.0 -> lo
            t < a -> lo * (peak / lo).pow(t / a)
            t < d -> peak * (lo / peak).pow((t - a) / (d - a))
            else -> 0.0
        }
    }

    private fun frames(dur: Double) = (sr * dur).toInt().coerceAtLeast(1)

    /** Decaying white-noise burst: (rand*2-1) * (1 - i/len)^decay. */
    private fun noise(dur: Double, decay: Double): FloatArray {
        val len = frames(dur)
        val d = FloatArray(len)
        for (i in 0 until len) {
            val k = (1.0 - i.toDouble() / len).pow(decay)
            d[i] = ((Random.nextDouble() * 2 - 1) * k).toFloat()
        }
        return d
    }

    /** RBJ biquad, applied in place. type: "lp" | "hp" | "bp". */
    private fun filter(x: FloatArray, type: String, freq: Double, q: Double) {
        val w0 = 2 * PI * freq / sr
        val cw = cos(w0)
        val sw = sin(w0)
        val alpha = sw / (2 * q)
        val b0: Double; val b1: Double; val b2: Double
        val a0: Double; val a1: Double; val a2: Double
        when (type) {
            "lp" -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha }
            "hp" -> { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha }
            else -> { b0 = alpha; b1 = 0.0; b2 = -alpha; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha } // bandpass (0 dB peak)
        }
        val nb0 = b0 / a0; val nb1 = b1 / a0; val nb2 = b2 / a0; val na1 = a1 / a0; val na2 = a2 / a0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val xn = x[i].toDouble()
            val yn = nb0 * xn + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
            x2 = x1; x1 = xn; y2 = y1; y1 = yn
            x[i] = yn.toFloat()
        }
    }

    /** Add a single oscillator voice (with envelope) into [buf]. */
    private fun addOsc(
        buf: FloatArray, type: String, f0: Double, f1: Double,
        a: Double, peak: Double, d: Double, fRampDur: Double = 0.0,
    ) {
        var phase = 0.0
        for (i in buf.indices) {
            val t = i.toDouble() / sr
            if (t >= d) break
            val f = if (fRampDur > 0 && t < fRampDur) f0 * (f1 / f0).pow(t / fRampDur) else if (f1 != f0) f1 else f0
            phase += 2 * PI * f / sr
            val s = when (type) {
                "square" -> if (sin(phase) >= 0) 1.0 else -1.0
                "triangle" -> 2.0 / PI * Math.asin(sin(phase))
                else -> sin(phase)
            }
            buf[i] += (s * env(t, a, peak, d)).toFloat()
        }
    }

    private fun addNoise(buf: FloatArray, src: FloatArray, gainConst: Double) {
        val n = minOf(buf.size, src.size)
        for (i in 0 until n) buf[i] += (src[i] * gainConst).toFloat()
    }

    private fun addNoiseEnv(buf: FloatArray, src: FloatArray, a: Double, peak: Double, d: Double) {
        val n = minOf(buf.size, src.size)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            buf[i] += (src[i] * env(t, a, peak, d)).toFloat()
        }
    }

    // ---------- profiles (ported 1:1 from audio.ts) ----------

    private fun synthMechanical(v: Float): FloatArray {
        val buf = FloatArray(frames(0.08))
        val n = noise(0.045, 2.5); filter(n, "bp", 1800 + Random.nextDouble() * 1200, 0.8)
        addNoise(buf, n, 0.5 * v)
        addOsc(buf, "sine", 120 + Random.nextDouble() * 50, 0.0, 0.004, 0.22 * v, 0.06)
        return buf
    }

    private fun synthTypewriter(v: Float): FloatArray {
        val buf = FloatArray(frames(0.06))
        val n = noise(0.03, 4.0); filter(n, "hp", 2600.0, 0.707)
        addNoise(buf, n, 0.55 * v)
        addOsc(buf, "square", 180 + Random.nextDouble() * 40, 0.0, 0.003, 0.18 * v, 0.05)
        addOsc(buf, "triangle", 2400 + Random.nextDouble() * 400, 0.0, 0.002, 0.05 * v, 0.04)
        return buf
    }

    private fun synthSoft(v: Float): FloatArray {
        val buf = FloatArray(frames(0.09))
        val n = noise(0.05, 2.0); filter(n, "lp", 500 + Random.nextDouble() * 150, 0.707)
        addNoise(buf, n, 0.28 * v)
        addOsc(buf, "sine", 85 + Random.nextDouble() * 30, 0.0, 0.006, 0.16 * v, 0.07)
        return buf
    }

    private fun synthTactile(v: Float): FloatArray {
        val buf = FloatArray(frames(0.1))
        val n = noise(0.04, 3.0); filter(n, "lp", 1100 + Random.nextDouble() * 300, 0.707)
        addNoise(buf, n, 0.32 * v)
        addOsc(buf, "sine", 95 + Random.nextDouble() * 25, 0.0, 0.005, 0.3 * v, 0.085)
        return buf
    }

    private fun synthBlue(v: Float): FloatArray {
        val buf = FloatArray(frames(0.08))
        // two sharp high ticks (key down + up)
        run {
            val n = noise(0.02, 5.0); filter(n, "hp", 3200.0, 0.707)
            addNoise(buf, n, 0.6 * v)
        }
        run {
            val off = ((0.035 + Random.nextDouble() * 0.01) * sr).toInt()
            val n = noise(0.02, 5.0); filter(n, "hp", 3200.0, 0.707)
            for (i in n.indices) if (off + i < buf.size) buf[off + i] += (n[i] * 0.4 * v).toFloat()
        }
        addOsc(buf, "square", 240.0, 0.0, 0.002, 0.1 * v, 0.03)
        return buf
    }

    private fun synthVintage(v: Float): FloatArray {
        val buf = FloatArray(frames(0.11))
        val n = noise(0.05, 2.2); filter(n, "bp", 750 + Random.nextDouble() * 250, 1.1)
        addNoise(buf, n, 0.45 * v)
        addOsc(buf, "triangle", 150 + Random.nextDouble() * 40, 0.0, 0.006, 0.24 * v, 0.09)
        return buf
    }

    private fun synthBubble(v: Float): FloatArray {
        val buf = FloatArray(frames(0.11))
        val f0 = 420 + Random.nextDouble() * 180
        addOsc(buf, "sine", f0, f0 * 2.2, 0.008, 0.3 * v, 0.09, fRampDur = 0.05)
        return buf
    }

    private fun synthMush(v: Float): FloatArray {
        val buf = FloatArray(frames(0.1))
        val n = noise(0.05, 1.6); filter(n, "lp", 320 + Random.nextDouble() * 80, 0.707)
        addNoise(buf, n, 0.2 * v)
        addOsc(buf, "sine", 70 + Random.nextDouble() * 20, 0.0, 0.01, 0.12 * v, 0.08)
        return buf
    }

    private fun synthPencil(v: Float): FloatArray {
        val dur = 0.05 + Random.nextDouble() * 0.05
        val buf = FloatArray(frames(dur + 0.01))
        val n = noise(dur, 1.3); filter(n, "bp", 2200 + Random.nextDouble() * 2600, 0.6)
        addNoiseEnv(buf, n, 0.008, 0.16 * v, dur)
        val body = noise(dur, 2.0); filter(body, "lp", 420 + Random.nextDouble() * 120, 0.707)
        addNoise(buf, body, 0.06 * v)
        return buf
    }

    private fun synthCreamy(v: Float): FloatArray {
        val buf = FloatArray(frames(0.12))
        val n = noise(0.06, 1.8); filter(n, "lp", 380 + Random.nextDouble() * 90, 0.707)
        addNoise(buf, n, 0.22 * v)
        addOsc(buf, "sine", 78 + Random.nextDouble() * 22, 0.0, 0.008, 0.34 * v, 0.1)
        return buf
    }

    private fun synthClacky(v: Float): FloatArray {
        val buf = FloatArray(frames(0.05))
        val n = noise(0.022, 5.0); filter(n, "hp", 3600.0, 0.707)
        addNoise(buf, n, 0.6 * v)
        addOsc(buf, "square", 300 + Random.nextDouble() * 60, 0.0, 0.002, 0.16 * v, 0.03)
        return buf
    }

    private fun synthGlass(v: Float): FloatArray {
        val buf = FloatArray(frames(0.16))
        val f = 1500 + Random.nextDouble() * 500
        addOsc(buf, "sine", f, 0.0, 0.002, 0.22 * v, 0.14)
        addOsc(buf, "sine", f * 2.01, 0.0, 0.002, 0.10 * v, 0.1)
        return buf
    }

    private fun synthThud(v: Float): FloatArray {
        val buf = FloatArray(frames(0.13))
        val n = noise(0.07, 1.4); filter(n, "lp", 260 + Random.nextDouble() * 70, 0.707)
        addNoise(buf, n, 0.26 * v)
        addOsc(buf, "sine", 58 + Random.nextDouble() * 16, 0.0, 0.012, 0.36 * v, 0.11)
        return buf
    }

    private fun synthPen(v: Float): FloatArray {
        val dur = 0.03 + Random.nextDouble() * 0.025
        val buf = FloatArray(frames(dur + 0.01))
        val n = noise(dur, 2.2); filter(n, "bp", 1500 + Random.nextDouble() * 1400, 0.8)
        addNoiseEnv(buf, n, 0.004, 0.13 * v, dur)
        addOsc(buf, "triangle", 220 + Random.nextDouble() * 60, 0.0, 0.002, 0.06 * v, 0.03)
        return buf
    }

    private fun synthCarriageReturn(v: Float, withSwipe: Boolean): FloatArray {
        val buf = FloatArray(frames(0.7))
        // bell: two close partials, fast attack, long ring
        addOsc(buf, "sine", 1850.0, 0.0, 0.004, 0.18 * v, 0.6)
        addOsc(buf, "sine", 2640.0, 0.0, 0.004, 0.10 * v, 0.6)
        if (withSwipe) {
            val dur = 0.16
            val n = noise(dur, 1.1)
            filter(n, "bp", 1800.0, 0.9) // mid sweep approximation of the carriage flyback
            val off = (0.04 * sr).toInt()
            for (i in n.indices) {
                val idx = off + i
                if (idx >= buf.size) break
                val t = i.toDouble() / sr
                buf[idx] += (n[i] * env(t, 0.03, 0.12 * v, dur)).toFloat()
            }
        }
        return buf
    }
}
