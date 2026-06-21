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
import androidx.compose.ui.Modifier
import com.example.recorder.audio.AudioBus
import com.example.recorder.sims.SIMS
import com.example.recorder.sims.SimDef
import com.example.recorder.ui.HubScreen
import com.example.recorder.ui.SimHubTheme

/** The hub / launcher. Each sim opens in its own [SimActivity]. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimHubTheme {
                Surface(Modifier.fillMaxSize()) {
                    HubScreen(
                        sims = SIMS,
                        onOpenSim = { openSim(it, present = false) },
                        onPlaySim = { openSim(it, present = true) },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                    )
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
