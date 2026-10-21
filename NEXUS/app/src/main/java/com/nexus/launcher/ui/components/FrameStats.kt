package com.nexus.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.nexus.launcher.ui.theme.LocalNexus

/**
 * Developer overlay (Studio › Performance › Frame stats). Measures real frame intervals
 * while visible — so you can verify smoothness instead of trusting claims. It keeps the
 * frame clock running, so leave it off for normal use.
 */
@Composable
fun FrameStatsOverlay(modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val view = LocalView.current
    var fps by remember { mutableStateOf(0) }
    var jank by remember { mutableStateOf(0) }
    var worst by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val refresh = view.display?.refreshRate ?: 60f
        val budgetNs = 1_000_000_000f / refresh
        var last = withFrameNanos { it }
        var frames = 0
        var janky = 0
        var worstMs = 0f
        var windowStart = last
        while (true) {
            withFrameNanos { now ->
                val dt = now - last
                last = now
                frames++
                if (dt > budgetNs * 1.5f) janky++
                worstMs = maxOf(worstMs, dt / 1_000_000f)
                if (now - windowStart >= 1_000_000_000L) {
                    fps = frames; jank = janky; worst = worstMs
                    frames = 0; janky = 0; worstMs = 0f; windowStart = now
                }
            }
        }
    }
    Column(modifier.background(Color.Black.copy(alpha = 0.7f)).padding(8.dp)) {
        Text("FPS $fps", style = nx.label(10f, nx.palette.accent))
        Text("JANK $jank/s", style = nx.label(9f, if (jank > 2) nx.palette.accent2 else nx.palette.textDim))
        Text("WORST ${"%.1f".format(worst)}ms", style = nx.label(9f, nx.palette.textDim))
        Text(nx.quality.reason.uppercase(), style = nx.label(8f, nx.palette.textFaint))
    }
}
