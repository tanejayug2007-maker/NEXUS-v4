package com.nexus.launcher.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.nexus.launcher.core.LaunchAnim
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

data class LaunchFxEvent(
    val id: Long,
    val center: Offset,
    val iconSize: Float,
    val preset: LaunchAnim,
)

/**
 * Launcher-side flourish drawn around the tapped icon. It runs in parallel with the real
 * window transition (ActivityOptions clip-reveal / scale-up / custom anim) — it never delays
 * the app beyond the user-configurable "lead" (0–150 ms, 0 in Battery Saver).
 */
@Composable
fun LaunchFxOverlay(event: LaunchFxEvent?, onDone: () -> Unit) {
    if (event == null || event.preset == LaunchAnim.SYSTEM_DEFAULT) return
    val nx = LocalNexus.current
    val progress = remember(event.id) { Animatable(0f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(nx.dur(if (nx.quality.heavyFx) 520 else 280), easing = LinearEasing))
        done()
    }
    val accent = nx.palette.accent
    val fx = nx.palette.fx
    val accent2 = nx.palette.accent2
    val particles = if (nx.quality.heavyFx) (24 * (0.4f + nx.quality.particles)).toInt() else 0
    val seeds = remember(event.id) { Random(event.id).let { r -> List(64) { r.nextFloat() } } }
    val measurer = rememberTextMeasurer()
    val glyphs = remember(accent) {
        val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = accent)
        "01アイウエオカキクケコ<>/\\#$%ΣΔΩ".map { measurer.measure(it.toString(), style) }
    }
    if (event.preset == LaunchAnim.HYPERSPACE_JUMP && nx.quality.heavyFx) {
        WarpOverlay(event.center, progress.asState())
    }

    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        val e = FastOutSlowInEasing.transform(t)
        val c = event.center
        val maxR = maxDistance(c, size)
        val fade = (1f - t).coerceIn(0f, 1f)
        when (event.preset) {
            LaunchAnim.QUANTUM_PORTAL -> {
                val r = event.iconSize * 0.6f + (maxR - event.iconSize * 0.6f) * e
                drawCircle(Brush.radialGradient(listOf(fx.copy(alpha = 0.55f * fade), Color.Transparent), c, r * 0.5f + 1f), r * 0.5f + 1f, c)
                drawCircle(accent, r, c, alpha = fade, style = Stroke(width = 3f + 10f * fade))
                for (k in 0 until 6) {
                    rotate(degrees = k * 60f + 220f * e, pivot = c) {
                        drawArc(accent2, 0f, 32f, false, topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.62f), size = Size(r * 1.24f, r * 1.24f), alpha = fade * 0.8f, style = Stroke(width = 3f, cap = StrokeCap.Round))
                    }
                }
            }
            LaunchAnim.HOLOGRAPHIC_EXPANSION -> {
                val w = event.iconSize + (size.width - event.iconSize) * e
                val h = event.iconSize + (size.height - event.iconSize) * e
                val left = c.x + (0f - c.x) * e - w / 2f * (1f - e)
                val top = c.y + (0f - c.y) * e - h / 2f * (1f - e)
                drawRoundRect(accent, Offset(left, top), Size(w, h), CornerRadius(28f * (1f - e)), alpha = fade, style = Stroke(width = 2.5f))
                var y = top
                val gap = 9f
                while (y < top + h) {
                    drawLine(fx, Offset(left, y), Offset(left + w, y), strokeWidth = 1f, alpha = 0.18f * fade)
                    y += gap
                }
            }
            LaunchAnim.NEURAL_PULSE -> {
                for (k in 0 until 3) {
                    val lt = ((t - k * 0.15f) / 0.7f).coerceIn(0f, 1f)
                    if (lt <= 0f) continue
                    drawCircle(accent, event.iconSize * 0.5f + maxR * 0.6f * lt, c, alpha = (1f - lt) * 0.9f, style = Stroke(width = 2.5f))
                }
                val rays = 12
                for (i in 0 until rays) {
                    val a = (i / rays.toFloat()) * 2f * PI.toFloat() + seeds[i] * 0.4f
                    val len = event.iconSize * 0.6f + maxR * 0.45f * e * (0.6f + seeds[i + 12] * 0.4f)
                    val start = Offset(c.x + cos(a) * event.iconSize * 0.55f, c.y + sin(a) * event.iconSize * 0.55f)
                    val end = Offset(c.x + cos(a) * len, c.y + sin(a) * len)
                    drawLine(accent2, start, end, strokeWidth = 2f, alpha = fade * 0.8f, cap = StrokeCap.Round)
                    drawCircle(fx, 4f, end, alpha = fade)
                }
            }
            LaunchAnim.DIGITAL_MATERIALIZATION -> {
                val cell = event.iconSize / 4f
                val spread = 1f + 6f * e
                for (gx in -4 until 4) for (gy in -4 until 4) {
                    val s = seeds[((gx + 4) * 8 + gy + 4) % seeds.size]
                    if (s > 0.25f + 0.75f * (1f - fade)) continue
                    val ox = c.x + (gx + 0.5f) * cell * spread
                    val oy = c.y + (gy + 0.5f) * cell * spread
                    drawRect(if (s > 0.6f) accent else fx, Offset(ox - cell * 0.4f, oy - cell * 0.4f), Size(cell * 0.8f, cell * 0.8f), alpha = fade * (0.4f + s * 0.6f))
                }
            }
            LaunchAnim.CYBERNETIC_ZOOM -> {
                val l = c.x - event.iconSize / 2f + (0f - (c.x - event.iconSize / 2f)) * e
                val tp = c.y - event.iconSize / 2f + (0f - (c.y - event.iconSize / 2f)) * e
                val r = c.x + event.iconSize / 2f + (size.width - (c.x + event.iconSize / 2f)) * e
                val b = c.y + event.iconSize / 2f + (size.height - (c.y + event.iconSize / 2f)) * e
                corners(l, tp, r, b, 26f + 40f * e, accent, fade)
                drawLine(accent2, Offset(0f, c.y), Offset(size.width, c.y), strokeWidth = 1f, alpha = 0.5f * fade)
                drawLine(accent2, Offset(c.x, 0f), Offset(c.x, size.height), strokeWidth = 1f, alpha = 0.5f * fade)
            }
            LaunchAnim.ENERGY_WAVE -> {
                val r = maxR * e
                drawCircle(Brush.radialGradient(listOf(Color.Transparent, accent.copy(alpha = 0.35f * fade), Color.Transparent), c, r + 1f), r + 1f, c)
                for (k in 1..3) drawCircle(fx, r * (1f - k * 0.12f).coerceAtLeast(0f), c, alpha = fade * 0.4f / k, style = Stroke(width = 1.5f))
            }
            LaunchAnim.HYPERSPACE_JUMP -> {
                // star streaks accelerating away from the icon
                for (i in 0 until 56) {
                    val a = seeds[i % seeds.size] * 2f * PI.toFloat() + i * 0.11f
                    val speed = 0.4f + seeds[(i * 3) % seeds.size] * 0.8f
                    val far = event.iconSize * 0.4f + maxR * e * e * speed
                    val near = (far - maxR * 0.22f * e * speed).coerceAtLeast(event.iconSize * 0.35f)
                    drawLine(
                        if (i % 4 == 0) accent2 else fx,
                        Offset(c.x + cos(a) * near, c.y + sin(a) * near), Offset(c.x + cos(a) * far, c.y + sin(a) * far),
                        strokeWidth = 1.5f + speed * 2f, alpha = fade, cap = StrokeCap.Round,
                    )
                }
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * fade), accent.copy(alpha = 0.3f * fade), Color.Transparent), c, event.iconSize * (0.8f + 2f * e)), event.iconSize * (0.8f + 2f * e), c)
            }
            LaunchAnim.SINGULARITY -> {
                if (t < 0.45f) {
                    val k = t / 0.45f
                    for (i in 0 until 40) {
                        val a = seeds[i % seeds.size] * 6.283f + k * 5f
                        val d = event.iconSize * 2.6f * (1f - k) * (0.6f + seeds[(i + 9) % seeds.size] * 0.6f)
                        drawCircle(if (i % 3 == 0) accent2 else fx, 2.4f, Offset(c.x + cos(a) * d, c.y + sin(a) * d), alpha = 0.4f + 0.6f * k)
                    }
                    drawCircle(Color.Black, event.iconSize * 0.45f * k, c)
                    drawCircle(accent, event.iconSize * 0.45f * k, c, alpha = 0.9f, style = Stroke(3f))
                } else {
                    val k = (t - 0.45f) / 0.55f
                    val r = event.iconSize * 0.45f + maxR * FastOutSlowInEasing.transform(k)
                    drawCircle(accent, r, c, alpha = 1f - k, style = Stroke(width = 4f + 16f * (1f - k)))
                    drawCircle(accent2, r * 0.8f, c, alpha = (1f - k) * 0.5f, style = Stroke(2f))
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 1f - k), Color.Transparent), c, event.iconSize * 1.2f), event.iconSize * 1.2f, c)
                }
            }
            LaunchAnim.HEX_DISSOLVE -> {
                val hr = 22f
                val wave = maxR * e
                val cols = (size.width / (hr * 1.5f)).toInt() + 2
                val rows = (size.height / (hr * 1.732f)).toInt() + 2
                for (cx in 0 until cols) for (cy in 0 until rows) {
                    val x = cx * hr * 1.5f
                    val y = cy * hr * 1.732f + (if (cx % 2 == 1) hr * 0.866f else 0f)
                    val d = hypot(x - c.x, y - c.y)
                    val front = wave - d
                    if (front < 0f || front > maxR * 0.35f) continue
                    val lk = (front / (maxR * 0.35f)).coerceIn(0f, 1f)
                    hexagon(Offset(x, y), hr * (0.2f + 0.75f * (1f - lk)), if ((cx + cy) % 5 == 0) accent2 else accent, (1f - lk) * 0.85f)
                }
            }
            LaunchAnim.DATA_STREAM -> {
                val colsN = 18
                val spread = size.width * 0.5f * (0.4f + e)
                for (col in 0 until colsN) {
                    val x = c.x - spread + col * (spread * 2f / colsN)
                    val speed = 0.6f + seeds[col] * 0.9f
                    val head = c.y - size.height * 0.4f + size.height * 1.1f * e * speed
                    for (k in 0 until 9) {
                        val y = head - k * 18f
                        val g = glyphs[((col * 7 + k * 3 + (t * 30).toInt()) % glyphs.size)]
                        drawText(g, topLeft = Offset(x, y), alpha = fade * (1f - k / 9f) * (if (k == 0) 1f else 0.7f))
                    }
                }
            }
            LaunchAnim.SYSTEM_DEFAULT -> Unit
        }
        // shared spark particles
        for (i in 0 until particles) {
            val a = seeds[i % seeds.size] * 2f * PI.toFloat() + i
            val d = event.iconSize * 0.5f + max(size.width, size.height) * 0.35f * e * (0.3f + seeds[(i * 7) % seeds.size])
            drawCircle(if (i % 3 == 0) accent2 else fx, 2.2f, Offset(c.x + cos(a) * d, c.y + sin(a) * d), alpha = fade)
        }
    }
}

private fun DrawScope.hexagon(center: Offset, r: Float, color: Color, alpha: Float) {
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0 until 6) {
        val a = (PI / 3 * i).toFloat()
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, alpha = alpha * 0.25f)
    drawPath(path, color, alpha = alpha, style = Stroke(1.5f))
}

private fun maxDistance(c: Offset, s: Size): Float = listOf(
    hypot(c.x, c.y), hypot(s.width - c.x, c.y), hypot(c.x, s.height - c.y), hypot(s.width - c.x, s.height - c.y)
).max()

internal fun DrawScope.corners(l: Float, t: Float, r: Float, b: Float, len: Float, color: Color, alpha: Float, width: Float = 3f) {
    drawLine(color, Offset(l, t), Offset(l + len, t), width, alpha = alpha)
    drawLine(color, Offset(l, t), Offset(l, t + len), width, alpha = alpha)
    drawLine(color, Offset(r, t), Offset(r - len, t), width, alpha = alpha)
    drawLine(color, Offset(r, t), Offset(r, t + len), width, alpha = alpha)
    drawLine(color, Offset(l, b), Offset(l + len, b), width, alpha = alpha)
    drawLine(color, Offset(l, b), Offset(l, b - len), width, alpha = alpha)
    drawLine(color, Offset(r, b), Offset(r - len, b), width, alpha = alpha)
    drawLine(color, Offset(r, b), Offset(r, b - len), width, alpha = alpha)
}
