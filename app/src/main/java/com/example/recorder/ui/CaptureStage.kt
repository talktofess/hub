package com.example.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.recorder.sims.SimLogical
import kotlin.math.min

/**
 * Renders [content] at EXACT logical pixels and records each frame into
 * [captureLayer]. The recorder rasterizes the layer with toImageBitmap() and
 * uploads it to the encoder — a Bitmap replays correctly anywhere (a layer/picture
 * display list draws BLACK in the encoder's separate render context).
 *
 * Exact-pixel trick: under Density(1f), `1.dp == 1px`, so a Box sized
 * logical.w×logical.h dp lays out at exactly logical.w×logical.h px. We record
 * that unscaled draw into the layer, then a graphicsLayer scales the whole thing
 * down to fit the screen for the live preview.
 */
@Composable
fun CaptureStage(
    logical: SimLogical,
    captureLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
    recordScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color.Black).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        // Under Density(recordScale), logical.w.dp == logical.w*recordScale px, so the
        // captured bitmap is supersampled (crisper) while still displaying the same size.
        val pxW = logical.w * recordScale
        val pxH = logical.h * recordScale
        val availW = constraints.maxWidth.toFloat()
        val availH = constraints.maxHeight.toFloat()
        val scale = min(availW / pxW, availH / pxH)

        CompositionLocalProvider(LocalDensity provides Density(recordScale, 1f)) {
            Box(
                Modifier
                    .size(logical.w.dp, logical.h.dp) // == logical.w*recordScale × logical.h*recordScale px
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .drawWithContent {
                        captureLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(captureLayer)
                    },
            ) {
                content()
            }
        }
    }
}
