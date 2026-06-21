package com.example.recorder

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.recorder.audio.AudioBus
import com.example.recorder.audio.AudioDecoder
import com.example.recorder.engine.SimRuntime
import com.example.recorder.model.BgKind
import com.example.recorder.recording.Gallery
import com.example.recorder.recording.RecordController
import com.example.recorder.recording.Recorder
import com.example.recorder.sims.SimDef
import com.example.recorder.sims.defaultSim
import com.example.recorder.sims.getSim
import com.example.recorder.sims.notes.NotesStore
import com.example.recorder.store.Media
import com.example.recorder.store.Projects
import com.example.recorder.ui.ConfigScreen
import com.example.recorder.ui.PresentScreen
import com.example.recorder.ui.ProjectsScreen
import com.example.recorder.ui.SimHubTheme
import com.example.recorder.ui.TypeSheetScreen
import java.io.File

private enum class SimScreen { CONFIG, PRESENT, TYPESHEET, PROJECTS }

/** Hosts ONE sim — its config (builder + tabs), stage, recording. Launched per
 *  sim by [MainActivity]; the sim id comes in via the [SIM_ID] intent extra. */
class SimActivity : ComponentActivity() {

    companion object {
        const val SIM_ID = "sim_id"
        const val START_PRESENT = "present"
    }

    private val runtime = SimRuntime()
    private lateinit var sim: SimDef
    private var recordTemp: File? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Thread {
            val path = Media.copyIntoApp(this, uri.toString(), "bg") ?: return@Thread
            runtime.settings = runtime.settings.copy(bg = runtime.settings.bg.copy(uri = path, kind = BgKind.IMAGE))
        }.start()
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Thread {
            val path = Media.copyIntoApp(this, uri.toString(), "audio") ?: uri.toString()
            runtime.settings = runtime.settings.copy(bgAudioUri = path)
            decodeBed(Uri.parse(path))
        }.start()
    }

    private val pickSimImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Thread {
            val path = Media.copyIntoApp(this, uri.toString(), "img") ?: return@Thread
            sim.onPickedImage(path)
        }.start()
    }

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Thread {
            val path = Media.copyIntoApp(this, uri.toString(), "avatar") ?: return@Thread
            sim.onPickedAvatar(path)
        }.start()
    }

    private fun decodeBed(uri: Uri) {
        Thread { AudioBus.engine.setBed(AudioDecoder.decodeToMono44100(this, uri)) }.start()
    }

    private fun applyProject(name: String) {
        val loaded = Projects.load(this, name) ?: return
        NotesStore.setAll(loaded.first)
        runtime.settings = loaded.second
        runtime.applyAudioSettings()
        AudioBus.engine.setBed(null)
        loaded.second.bgAudioUri?.let { decodeBed(Uri.parse(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sim = getSim(intent.getStringExtra(SIM_ID)) ?: defaultSim()
        runtime.script = sim.defaultScript
        runtime.applyAudioSettings()
        applyProject(Projects.AUTOSAVE) // shared session state

        val startPresent = intent.getBooleanExtra(START_PRESENT, false)

        setContent {
            SimHubTheme {
                val rt = remember { runtime }
                var screen by remember { mutableStateOf(if (startPresent) SimScreen.PRESENT else SimScreen.CONFIG) }

                LaunchedEffect(screen) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (screen == SimScreen.PRESENT) controller.hide(WindowInsetsCompat.Type.systemBars())
                    else controller.show(WindowInsetsCompat.Type.systemBars())
                }

                Surface(Modifier.fillMaxSize()) {
                    when (screen) {
                        SimScreen.CONFIG -> ConfigScreen(
                            rt = rt,
                            selected = sim,
                            onBack = { finish() },
                            onPickBackground = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onPickImage = { pickSimImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onPickAudio = { pickAudio.launch("audio/*") },
                            onPickAvatar = { pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onOpenTypeSheet = { screen = SimScreen.TYPESHEET },
                            onOpenProjects = { screen = SimScreen.PROJECTS },
                            onPlay = { rt.stop(); rt.script = sim.defaultScript; screen = SimScreen.PRESENT },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                        )
                        SimScreen.PROJECTS -> ProjectsScreen(
                            rt = rt,
                            onLoad = { name -> applyProject(name) },
                            onBack = { screen = SimScreen.CONFIG },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                        )
                        SimScreen.TYPESHEET -> TypeSheetScreen(
                            rt = rt,
                            onBack = { screen = SimScreen.CONFIG },
                            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                        )
                        SimScreen.PRESENT -> PresentScreen(
                            rt = rt,
                            sim = sim,
                            onExit = {
                                if (RecordController.isRecording) stopRecording()
                                rt.stop()
                                screen = SimScreen.CONFIG
                            },
                            onStartRecord = ::startRecording,
                            onStopRecord = ::stopRecording,
                        )
                    }
                }
            }
        }
    }

    private fun startRecording() {
        if (RecordController.isRecording) return
        try {
            val temp = File(cacheDir, "take_${System.currentTimeMillis()}.mp4")
            recordTemp = temp
            val s = com.example.recorder.model.AppSettings.recordScale
            val w = (sim.logical.w * s).toInt()
            val h = (sim.logical.h * s).toInt()
            val rec = Recorder(w, h, temp, AudioBus.engine).also { it.start() }
            RecordController.recorder = rec
            RecordController.lastError = null
            RecordController.isRecording = true
        } catch (t: Throwable) {
            RecordController.lastError = t.message ?: "could not start recording"
            RecordController.isRecording = false
        }
    }

    private fun stopRecording() {
        val rec = RecordController.recorder ?: return
        RecordController.recorder = null
        RecordController.isRecording = false
        runtime.stop()
        val temp = recordTemp
        recordTemp = null
        Thread {
            try { rec.stop() } catch (_: Throwable) {}
            if (temp != null && temp.exists() && temp.length() > 0) {
                try {
                    val name = Gallery.save(this, temp)
                    RecordController.lastSavedName = name
                    RecordController.savedCount++
                } catch (t: Throwable) {
                    RecordController.lastError = t.message ?: "could not save to gallery"
                }
            }
            temp?.delete()
        }.start()
    }

    override fun onStop() {
        super.onStop()
        if (RecordController.isRecording) stopRecording()
        runtime.stop()
        try { Projects.save(this, Projects.AUTOSAVE, NotesStore.notes.toList(), runtime.settings) } catch (_: Throwable) {}
    }
    // NB: runtime.audio is the shared AudioBus.engine singleton — don't release it
    // per-activity; it lives for the process and is reclaimed on exit.
}
