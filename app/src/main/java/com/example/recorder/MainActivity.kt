package com.example.recorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.recorder.audio.AudioBus
import com.example.recorder.model.AppSettings
import com.example.recorder.sims.SIMS
import com.example.recorder.sims.SimDef
import com.example.recorder.ui.HubScreen
import com.example.recorder.ui.SettingsScreen
import com.example.recorder.ui.SimHubTheme

/** The hub / launcher. Each sim opens in its own [SimActivity]. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        enableEdgeToEdge()
        setContent {
            SimHubTheme {
                Surface(Modifier.fillMaxSize()) {
                    var settings by remember { mutableStateOf(false) }
                    val pad = Modifier.windowInsetsPadding(WindowInsets.systemBars)
                    if (settings) {
                        SettingsScreen(onBack = { settings = false }, modifier = pad)
                    } else {
                        HubScreen(
                            sims = SIMS,
                            onOpenSim = { openSim(it, present = false) },
                            onPlaySim = { openSim(it, present = true) },
                            onOpenSettings = { settings = true },
                            modifier = pad,
                        )
                    }
                }
            }
        }
    }

    private fun openSim(sim: SimDef, present: Boolean) {
        startActivity(
            Intent(this, SimActivity::class.java).apply {
                putExtra(SimActivity.SIM_ID, sim.id)
                putExtra(SimActivity.START_PRESENT, present)
            },
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // app is exiting — release the shared audio engine.
        if (isFinishing) runCatching { AudioBus.engine.release() }
    }
}
