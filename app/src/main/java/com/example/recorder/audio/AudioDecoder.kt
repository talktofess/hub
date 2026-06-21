package com.example.recorder.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

/**
 * Decodes an audio file (content URI) to mono 16-bit PCM at 44.1 kHz, so it can
 * be mixed into [AudioEngine] as a looping background bed. Decoding is capped at
 * ~90 s (longer files are truncated, then looped) to bound memory.
 */
object AudioDecoder {

    private const val TARGET_RATE = 44100
    private const val MAX_SECONDS = 90

    fun decodeToMono44100(ctx: Context, uri: Uri): ShortArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(ctx, uri, null)
        } catch (_: Throwable) {
            return null
        }
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) { track = i; break }
        }
        if (track < 0) { extractor.release(); return null }

        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: run { extractor.release(); return null }
        val srcRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else TARGET_RATE
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        val maxMonoSamples = srcRate.toLong() * MAX_SECONDS

        val codec = try {
            MediaCodec.createDecoderByType(mime).also { it.configure(format, null, null, 0); it.start() }
        } catch (_: Throwable) {
            extractor.release(); return null
        }

        val mono = ByteArrayOutputStream()
        var monoCount = 0L
        val info = MediaCodec.BufferInfo()
        var inEos = false
        var outEos = false
        var guard = 0
        try {
            while (!outEos && guard++ < 500_000 && monoCount < maxMonoSamples) {
                if (!inEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        val size = if (buf != null) extractor.readSampleData(buf, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frame = ShortArray(sb.remaining())
                        sb.get(frame)
                        var i = 0
                        while (i + channels <= frame.size) {
                            var sum = 0
                            for (c in 0 until channels) sum += frame[i + c]
                            val m = (sum / channels)
                            mono.write(m and 0xFF)
                            mono.write((m shr 8) and 0xFF)
                            monoCount++
                            i += channels
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true
                }
            }
        } catch (_: Throwable) {
            // partial decode is fine
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            try { codec.release() } catch (_: Throwable) {}
            extractor.release()
        }

        val bytes = mono.toByteArray()
        val src = ShortArray(bytes.size / 2)
        var j = 0
        for (k in src.indices) { src[k] = ((bytes[j].toInt() and 0xFF) or (bytes[j + 1].toInt() shl 8)).toShort(); j += 2 }
        if (src.isEmpty()) return null
        return if (srcRate == TARGET_RATE) src else resample(src, srcRate, TARGET_RATE)
    }

    private fun resample(src: ShortArray, from: Int, to: Int): ShortArray {
        val outLen = (src.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val res = ShortArray(outLen)
        for (i in 0 until outLen) {
            val pos = i.toDouble() * from / to
            val i0 = pos.toInt()
            val frac = pos - i0
            val a = if (i0 < src.size) src[i0].toInt() else 0
            val b = if (i0 + 1 < src.size) src[i0 + 1].toInt() else a
            res[i] = (a + (b - a) * frac).toInt().toShort()
        }
        return res
    }
}
