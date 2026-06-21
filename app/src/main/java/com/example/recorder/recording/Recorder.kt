package com.example.recorder.recording

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.example.recorder.audio.AudioEngine
import com.example.recorder.recording.gl.GlBitmapEncoder
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Encodes a take to MP4 at exact logical resolution. Video frames are Compose
 * renders (GraphicsLayer.toImageBitmap) pushed through OpenGL ([GlBitmapEncoder])
 * into an AVC encoder input Surface — exact 1080×1920, no MediaProjection. Audio
 * is the exact PCM the [AudioEngine] is playing (tapped via pcmSink) and
 * AAC-encoded. Both tracks are muxed with [MediaMuxer]; the caller publishes the
 * file to the gallery. Requires API 29+.
 */
class Recorder(
    private val width: Int,
    private val height: Int,
    private val outFile: File,
    private val audio: AudioEngine,
) {
    private val sampleRate = 44100

    private lateinit var videoCodec: MediaCodec
    private lateinit var audioCodec: MediaCodec
    private lateinit var muxer: MediaMuxer

    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null
    private lateinit var glEnc: GlBitmapEncoder
    private var videoStartNs = -1L
    private val pendingFrames = AtomicInteger(0)

    private val lock = Object()
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false

    private val stopping = AtomicBoolean(false)
    private val pcmQueue = ArrayBlockingQueue<ShortArray>(64)
    private val eos = ShortArray(0) // poison pill

    private var videoThread: Thread? = null
    private var audioFeedThread: Thread? = null
    private var audioDrainThread: Thread? = null

    fun start() {
        audio.resume() // share t=0 with video; emits silence until the first keystroke

        val w = width and 1.inv()
        val h = height and 1.inv()

        val vFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, min(16_000_000, (w * h * 5.0).toInt()))
            setInteger(MediaFormat.KEY_FRAME_RATE, com.example.recorder.model.AppSettings.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoCodec.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoCodec.createInputSurface()
        videoCodec.start()

        val thread = HandlerThread("gl-encoder").apply { start() }
        glThread = thread
        val handler = Handler(thread.looper)
        glHandler = handler
        val ready = Object()
        var done = false
        handler.post {
            glEnc = GlBitmapEncoder(inputSurface, w, h)
            glEnc.setup()
            synchronized(ready) { done = true; ready.notifyAll() }
        }
        synchronized(ready) { while (!done) ready.wait() }

        val aFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        audioCodec.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioCodec.start()

        muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        audio.pcmSink = { buf, n ->
            if (!stopping.get()) pcmQueue.offer(buf.copyOf(n))
        }

        videoThread = Thread({ drainVideo() }, "rec-video").apply { start() }
        audioFeedThread = Thread({ feedAudio() }, "rec-audio-feed").apply { start() }
        audioDrainThread = Thread({ drainAudio() }, "rec-audio-drain").apply { start() }
    }

    /** Push one Compose-rendered frame. Frames are dropped if the GL thread falls
        behind, so the producer (a capture loop) never blocks or OOMs. */
    fun encodeFrame(img: ImageBitmap) {
        if (stopping.get()) return
        val handler = glHandler ?: return
        if (pendingFrames.get() > 2) return
        pendingFrames.incrementAndGet()
        handler.post {
            try {
                if (stopping.get()) return@post
                // copy to a software ARGB_8888 bitmap GLUtils can upload (the Compose
                // snapshot may be HARDWARE), and that we own + can recycle.
                val src = img.asAndroidBitmap()
                val safe = try { src.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Throwable) { src }
                val now = System.nanoTime()
                if (videoStartNs < 0) videoStartNs = now
                glEnc.drawFrame(safe, now - videoStartNs)
                if (safe !== src) safe.recycle()
            } finally {
                pendingFrames.decrementAndGet()
            }
        }
    }

    fun stop() {
        if (!stopping.compareAndSet(false, true)) return
        audio.pcmSink = null
        pcmQueue.offer(eos)

        val handler = glHandler
        if (handler != null) {
            val q = Object(); var done = false
            handler.post {
                try { videoCodec.signalEndOfInputStream() } catch (_: Throwable) {}
                synchronized(q) { done = true; q.notifyAll() }
            }
            synchronized(q) { val t0 = System.currentTimeMillis(); while (!done && System.currentTimeMillis() - t0 < 2000) q.wait(2000) }
        }

        videoThread?.join(2000)
        audioFeedThread?.join(2000)
        audioDrainThread?.join(2000)

        glHandler?.post { try { glEnc.release() } catch (_: Throwable) {} }
        glThread?.quitSafely()

        try { videoCodec.stop(); videoCodec.release() } catch (_: Throwable) {}
        try { audioCodec.stop(); audioCodec.release() } catch (_: Throwable) {}
        synchronized(lock) {
            if (muxerStarted) { try { muxer.stop() } catch (_: Throwable) {} }
            try { muxer.release() } catch (_: Throwable) {}
        }
    }

    private fun maybeStartMuxer() {
        synchronized(lock) {
            if (!muxerStarted && videoTrack >= 0 && audioTrack >= 0) {
                muxer.start()
                muxerStarted = true
                lock.notifyAll()
            }
        }
    }

    private fun awaitMuxer(): Boolean {
        synchronized(lock) {
            while (!muxerStarted && !stopping.get()) {
                try { lock.wait(50) } catch (_: InterruptedException) {}
            }
            return muxerStarted
        }
    }

    private fun drainVideo() {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = try { videoCodec.dequeueOutputBuffer(info, 10_000) } catch (_: IllegalStateException) { break }
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(lock) { videoTrack = muxer.addTrack(videoCodec.outputFormat) }
                maybeStartMuxer()
            } else if (idx >= 0) {
                val buf = videoCodec.getOutputBuffer(idx)
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                if (info.size > 0 && buf != null && awaitMuxer()) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    synchronized(lock) { muxer.writeSampleData(videoTrack, buf, info) }
                }
                videoCodec.releaseOutputBuffer(idx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }

    private fun feedAudio() {
        var totalSamples = 0L
        try {
            while (true) {
                val block = try { pcmQueue.take() } catch (_: InterruptedException) { break }
                val ptsUs = totalSamples * 1_000_000L / sampleRate
                if (block === eos) {
                    var idx = -1
                    var tries = 0
                    while (idx < 0 && tries < 100) { idx = audioCodec.dequeueInputBuffer(10_000); tries++ }
                    if (idx >= 0) audioCodec.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                val inIdx = audioCodec.dequeueInputBuffer(10_000)
                if (inIdx < 0) continue
                val inBuf: ByteBuffer = audioCodec.getInputBuffer(inIdx) ?: continue
                inBuf.clear()
                for (s in block) { inBuf.put((s.toInt() and 0xFF).toByte()); inBuf.put(((s.toInt() shr 8) and 0xFF).toByte()) }
                audioCodec.queueInputBuffer(inIdx, 0, block.size * 2, ptsUs, 0)
                totalSamples += block.size
            }
        } catch (_: IllegalStateException) {
            // codec stopped/released underneath us
        }
    }

    private fun drainAudio() {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = try { audioCodec.dequeueOutputBuffer(info, 10_000) } catch (_: IllegalStateException) { break }
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(lock) { audioTrack = muxer.addTrack(audioCodec.outputFormat) }
                maybeStartMuxer()
            } else if (idx >= 0) {
                val buf = audioCodec.getOutputBuffer(idx)
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                if (info.size > 0 && buf != null && awaitMuxer()) {
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    synchronized(lock) { muxer.writeSampleData(audioTrack, buf, info) }
                }
                audioCodec.releaseOutputBuffer(idx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }
}
