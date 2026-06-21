package com.example.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.recorder.sims.SimLogical
import kotlin.math.min

/**
 * Renders [content] at a fixed [logical] size and scales it to fit, so a sim
 * looks identical at any viewport size. Port of src/shell/Stage.tsx — 1 logical
 * px maps to 1 dp here.
 */
@Composable
fun Stage(logical: SimLogical, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(maxWidth.value / logical.w, maxHeight.value / logical.h)
        Box(
            Modifier
                .size(logical.w.dp, logical.h.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            content()
        }
    }
}
