package com.nexus.launcher.ui.fx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.ChargingAnim
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Charging experience shown inside the launcher when power is connected while NEXUS is visible
 * (or within the configured window after unlocking). It does not and cannot cover the secure
 * lock screen. Auto-dismisses; tap to dismiss early. All frame work stops when it closes.
 */
@Composable
fun ChargingOverlay(
    visible: Boolean,
    battery: BatteryState,
    style: ChargingAnim,
    intensity: Float,
    durationSec: Int,
    onDismiss: () -> Unit,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible, durationSec) {
        if (visible) { delay(durationSec.coerceIn(2, 30) * 1000L); dismiss() }
    }
    AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
      com.nexus.launcher.ui.theme.SurfaceAccent(LocalNexus.current.config.chargeAccent) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { dismiss() },
            contentAlignment = Alignment.Center,
        ) {
            ChargingVisual(battery, style, intensity, Modifier.size(300.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val nx = LocalNexus.current
                DotMatrixText("${battery.level}%", pitch = 7.dp, color = nx.palette.text, unlitAlpha = 0f)
                Spacer(Modifier.height(10.dp))
                val status = when {
                    battery.full || battery.level >= 100 -> "FULLY CHARGED"
                    battery.source.isNotEmpty() -> "CHARGING · ${battery.source}"
                    else -> "CHARGING"
                }
                Text(status, style = nx.label(11f, nx.palette.accent))
                if (battery.temperatureC > 0f) {
                    Spacer(Modifier.height(4.dp))
                    Text("CORE ${"%.1f".format(battery.temperatureC)}°C", style = nx.label(9f, nx.palette.textFaint))
                }
            }
        }
      }
    }
}

@Composable
fun ChargingVisual(battery: BatteryState, style: ChargingAnim, intensity: Float, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val clock = rememberAmbientClock(true)
    val accent = nx.palette.accent
    val accent2 = nx.palette.accent2
    val fx = nx.palette.fx
    val heavy = nx.quality.heavyFx
    val k = intensity.coerceIn(0.1f, 1f)
    val level = battery.level / 100f
    val seeds = remember { Random(11).let { r -> List(64) { r.nextFloat() } } }

    Canvas(modifier) {
        val t = clock.value * (0.6f + k)
        val c = center
        val r = min(size.width, size.height) / 2f * 0.82f
        when (style) {
            ChargingAnim.ENERGY_REACTOR -> {
                drawCircle(Brush.radialGradient(listOf(fx.copy(alpha = 0.35f * k), Color.Transparent), c, r * 0.9f), r * 0.9f, c)
                for (ring in 0 until 4) {
                    val rr = r * (0.55f + ring * 0.15f)
                    val dir = if (ring % 2 == 0) 1f else -1f
                    rotate(t * 40f * dir * (1 + ring * 0.3f), c) {
                        for (seg in 0 until 6) {
                            drawArc(
                                if (ring == 1) accent2 else accent, seg * 60f, 34f, false,
                                topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                                alpha = 0.35f + 0.5f * k * (1f - ring * 0.18f), style = Stroke(width = 4f - ring * 0.6f, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                drawArc(accent, -90f, 360f * level, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(width = 6f, cap = StrokeCap.Round))
                val pulse = 0.5f + 0.5f * sin(t * 3f)
                drawCircle(fx, r * (0.16f + 0.03f * pulse), c, alpha = 0.25f + 0.25f * pulse)
            }
            ChargingAnim.QUANTUM_RING -> {
                drawCircle(accent, r, c, alpha = 0.12f, style = Stroke(width = 10f))
                drawArc(
                    Brush.sweepGradient(listOf(accent2, accent, fx, accent2), c), -90f, 360f * level, false,
                    Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(width = 10f, cap = StrokeCap.Round),
                )
                val n = if (heavy) (18 * (0.4f + k)).toInt() else 6
                for (i in 0 until n) {
                    val a = t * (0.8f + seeds[i] * 0.8f) + seeds[i + 20] * 2f * PI.toFloat()
                    val rr = r * (1.08f + 0.12f * sin(t * 2f + i))
                    drawCircle(if (i % 3 == 0) accent2 else fx, 2.5f + seeds[i] * 2f, Offset(c.x + cos(a) * rr, c.y + sin(a) * rr), alpha = 0.8f)
                }
            }
            ChargingAnim.ELECTRIC_PULSE -> {
                for (i in 0 until 4) {
                    val lt = ((t * 0.5f + i * 0.25f) % 1f)
                    drawCircle(accent, r * 0.25f + r * 0.9f * lt, c, alpha = (1f - lt) * 0.7f * k, style = Stroke(width = 2.5f))
                }
                val bolts = if (heavy) 5 else 2
                val frame = (t * 6f).toInt()
                for (b in 0 until bolts) bolt(c, r, frame * 31 + b * 97, accent2, fx)
                drawCircle(fx, r * 0.18f, c, alpha = 0.3f)
            }
            ChargingAnim.BATTERY_CORE -> {
                val w = r * 0.9f
                val h = r * 1.7f
                val left = c.x - w / 2
                val top = c.y - h / 2
                drawRoundRect(accent, Offset(left, top), Size(w, h), androidx.compose.ui.geometry.CornerRadius(w * 0.18f), style = Stroke(width = 3f))
                drawRoundRect(accent, Offset(c.x - w * 0.18f, top - 14f), Size(w * 0.36f, 10f), androidx.compose.ui.geometry.CornerRadius(4f))
                val fh = (h - 16f) * level
                drawRoundRect(
                    Brush.verticalGradient(listOf(fx.copy(alpha = 0.8f), accent.copy(alpha = 0.35f)), startY = top + h - fh, endY = top + h),
                    Offset(left + 8f, top + h - 8f - fh), Size(w - 16f, fh), androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                )
                val bubbles = if (heavy) 16 else 6
                for (i in 0 until bubbles) {
                    val by = top + h - 8f - ((t * 40f * (0.5f + seeds[i]) + seeds[i + 16] * fh) % fh.coerceAtLeast(1f))
                    drawCircle(Color.White, 2f + seeds[i] * 3f, Offset(left + 16f + seeds[i + 32] * (w - 32f), by), alpha = 0.35f)
                }
                var y = top
                while (y < top + h) { drawLine(accent, Offset(left, y), Offset(left + w, y), 1f, alpha = 0.06f); y += 6f }
            }
            ChargingAnim.PLASMA_CIRCUIT -> {
                val traces = 10
                for (i in 0 until traces) {
                    val a = i / traces.toFloat() * 2f * PI.toFloat()
                    val p = Path().apply {
                        val start = Offset(c.x + cos(a) * r * 1.2f, c.y + sin(a) * r * 1.2f)
                        val mid = Offset(c.x + cos(a + 0.3f) * r * 0.7f, c.y + sin(a + 0.3f) * r * 0.7f)
                        val end = Offset(c.x + cos(a) * r * 0.3f, c.y + sin(a) * r * 0.3f)
                        moveTo(start.x, start.y); lineTo(mid.x, mid.y); lineTo(end.x, end.y)
                    }
                    drawPath(p, accent, alpha = 0.18f, style = Stroke(width = 2f))
                    drawPath(
                        p, if (i % 2 == 0) fx else accent2,
                        style = Stroke(width = 3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 34f), -t * 90f)),
                    )
                }
                drawCircle(Brush.radialGradient(listOf(fx.copy(alpha = 0.7f), accent.copy(alpha = 0.15f), Color.Transparent), c, r * 0.4f), r * 0.4f, c)
                drawArc(accent, -90f, 360f * level, false, Offset(c.x - r * 0.42f, c.y - r * 0.42f), Size(r * 0.84f, r * 0.84f), style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
            ChargingAnim.LIQUID_FILL -> {
                // a sphere filling with glowing liquid; the surface is a travelling double sine wave
                val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
                drawCircle(accent, r, c, alpha = 0.10f)
                drawCircle(accent, r, c, alpha = 0.6f, style = Stroke(2f))
                val surface = c.y + r - 2 * r * level
                clipPath(clip) {
                    for (layer in 0 until 2) {
                        val wave = Path()
                        val amp = r * 0.05f * (1f + layer * 0.6f) * (0.5f + k)
                        wave.moveTo(c.x - r, c.y + r)
                        var x = c.x - r
                        while (x <= c.x + r) {
                            val y = surface + sin((x / r) * 5f + t * (2.2f + layer)) * amp + sin((x / r) * 9f - t * 1.3f) * amp * 0.4f
                            wave.lineTo(x, y)
                            x += 6f
                        }
                        wave.lineTo(c.x + r, c.y + r); wave.close()
                        drawPath(
                            wave,
                            Brush.verticalGradient(listOf((if (layer == 0) accent2 else fx).copy(alpha = 0.55f), accent.copy(alpha = 0.35f)), startY = surface, endY = c.y + r),
                        )
                    }
                    val bubbles = if (heavy) 18 else 6
                    for (i in 0 until bubbles) {
                        val span = (c.y + r - surface).coerceAtLeast(1f)
                        val by = c.y + r - ((t * 30f * (0.5f + seeds[i]) + seeds[i + 18] * span) % span)
                        drawCircle(Color.White, 1.5f + seeds[i] * 3f, Offset(c.x - r * 0.7f + seeds[i + 36] * r * 1.4f, by), alpha = 0.35f)
                    }
                }
                drawArc(Color.White, 200f, 50f, false, Offset(c.x - r * 0.86f, c.y - r * 0.86f), Size(r * 1.72f, r * 1.72f), alpha = 0.35f, style = Stroke(3f, cap = StrokeCap.Round))
            }
            ChargingAnim.PARTICLE_VORTEX -> {
                // particles spiral into the core; the inflow rate follows intensity
                val n = if (heavy) (60 * (0.4f + k)).toInt() else 18
                for (i in 0 until n) {
                    val life = ((t * 0.35f * (0.6f + seeds[i % 64] * 0.8f)) + seeds[(i * 7) % 64]) % 1f
                    val ang = seeds[(i * 3) % 64] * 6.283f + life * 7f
                    val d = r * 1.15f * (1f - life)
                    val col = if (i % 5 == 0) accent2 else if (i % 2 == 0) fx else accent
                    drawCircle(col, 1.2f + 2.2f * (1f - life), Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), alpha = life.coerceIn(0.1f, 1f))
                }
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), accent.copy(alpha = 0.3f), Color.Transparent), c, r * 0.32f), r * 0.32f, c)
                drawArc(accent, -90f, 360f * level, false, Offset(c.x - r * 0.5f, c.y - r * 0.5f), Size(r, r), style = Stroke(4f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.bolt(c: Offset, r: Float, seed: Int, color: Color, core: Color) {
    val rnd = Random(seed)
    val a = rnd.nextFloat() * 2f * PI.toFloat()
    var p = Offset(c.x + cos(a) * r * 0.2f, c.y + sin(a) * r * 0.2f)
    val path = Path().apply { moveTo(p.x, p.y) }
    val steps = 6
    for (i in 1..steps) {
        val d = r * (0.2f + 0.8f * i / steps)
        val jitter = (rnd.nextFloat() - 0.5f) * 0.5f
        p = Offset(c.x + cos(a + jitter) * d, c.y + sin(a + jitter) * d)
        path.lineTo(p.x, p.y)
    }
    drawPath(path, color, alpha = 0.35f, style = Stroke(width = 6f, cap = StrokeCap.Round))
    drawPath(path, core, alpha = 0.9f, style = Stroke(width = 1.8f, cap = StrokeCap.Round))
}
