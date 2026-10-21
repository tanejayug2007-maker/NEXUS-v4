package com.nexus.launcher.ui.fx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.HapticSignature
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * "Neural link" ritual shown after Android's real unlock (Operator/Architect). Hold the core
 * for [holdMs] to sync. It is a launcher interaction, not security — Back or Home skips it.
 */
@Composable
fun LinkSyncOverlay(visible: Boolean, holdMs: Int, onLinked: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(260))) {
        LinkSyncContent(holdMs, onLinked)
    }
}

@Composable
private fun LinkSyncContent(holdMs: Int, onLinked: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val level = nx.config.haptics
    val linked by rememberUpdatedState(onLinked)
    val charge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("PLACE FINGER ON CORE") }
    var holding by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val clock = rememberAmbientClock(true)
    val sparks = remember { Random(5).let { r -> List(40) { r.nextFloat() } } }

    LaunchedEffect(done) {
        if (done) {
            status = "LINK ESTABLISHED"
            HapticSignature.play(ctx, HapticSignature.Kind.LINK_DONE, level)
            delay(420)
            linked()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .pointerInput(holdMs) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (done) return@awaitEachGesture
                    holding = true
                    status = "SYNCHRONISING…"
                    val tickJob = scope.launch {
                        while (true) { HapticSignature.play(ctx, HapticSignature.Kind.LINK_TICK, level); delay(140) }
                    }
                    val anim = scope.launch {
                        charge.animateTo(1f, tween(((1f - charge.value) * holdMs).toInt().coerceAtLeast(1), easing = LinearEasing))
                        done = true
                    }
                    do {
                        val ev = awaitPointerEvent()
                    } while (ev.changes.any { it.pressed })
                    holding = false
                    tickJob.cancel()
                    if (!done) {
                        anim.cancel()
                        status = "LINK UNSTABLE — HOLD LONGER"
                        scope.launch { charge.animateTo(0f, tween(450)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(300.dp)) {
            val t = clock.value
            val c = center
            val r = (size.minDimension / 2f * 0.8f).coerceAtLeast(1f)
            val k = charge.value
            // concentric "neural" whorls
            for (ring in 0 until 7) {
                val rr = r * (0.25f + ring * 0.11f)
                val gaps = 3 + ring
                rotate((if (ring % 2 == 0) 1 else -1) * t * (12f + ring * 7f) * (1f + k * 4f), c) {
                    for (g in 0 until gaps) {
                        val sweep = 360f / gaps * 0.68f
                        drawArc(
                            if (ring == 3) p.accent2 else p.accent, g * 360f / gaps, sweep, false,
                            Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2),
                            alpha = 0.18f + 0.6f * k, style = Stroke(2.2f, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            drawArc(p.fx, -90f, 360f * k, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(5f, cap = StrokeCap.Round))
            drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.5f * (0.3f + k)), Color.Transparent), c, r * 0.4f), r * 0.4f, c)
            // converging sparks while holding
            if (holding || k > 0f) {
                for (i in 0 until 20) {
                    val a = sparks[i] * 2f * PI.toFloat() + t * 0.6f
                    val d = r * (1.4f - ((t * 0.9f + sparks[i + 20]) % 1f) * 1.2f)
                    drawCircle(p.fx, 2f, Offset(c.x + cos(a) * d, c.y + sin(a) * d), alpha = 0.7f * k)
                }
            }
            if (done) drawCircle(Color.White, r * 1.4f * k, c, alpha = 0.18f)
        }
        Column(
            Modifier.fillMaxSize().padding(bottom = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(status, style = nx.label(11f, if (done) p.accent else p.text))
            Spacer(Modifier.height(6.dp))
            Text("NEURAL LINK · ${(charge.value * 100).toInt()}%", style = nx.label(9f, p.textFaint))
            Spacer(Modifier.height(4.dp))
            Text("BACK TO SKIP", style = nx.label(8f, p.textFaint))
        }
        Column(Modifier.fillMaxSize().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DotMatrixText("LINK", pitch = 6.dp, color = p.accent, unlitAlpha = 0.05f)
        }
    }
}
