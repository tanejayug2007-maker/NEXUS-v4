package com.nexus.launcher.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.nexus.launcher.core.FingerprintStyle
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * NEXUS's own animation drawn around the fingerprint position on the lock layer.
 * Android does not expose the sensor's location or its icon, so the user calibrates the
 * position in Studio; the real sensor and authentication remain Android's.
 * [scanning] = finger down / unlock requested (animation intensifies).
 */
@Composable
fun FingerprintFx(style: FingerprintStyle, scanning: Boolean, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val a = nx.palette.accent
    val b = nx.palette.accent2
    val fx = nx.palette.fx
    val clock = rememberAmbientClock(true)
    val seeds = remember { Random(23).let { r -> List(48) { r.nextFloat() } } }
    Canvas(modifier) {
        val t = clock.value * (if (scanning) 2.4f else 1f)
        val c = center
        val r = size.minDimension / 2f
        val k = if (scanning) 1f else 0.55f
        when (style) {
            FingerprintStyle.SCAN_RING -> {
                drawCircle(a, r * 0.62f, c, alpha = 0.35f * k, style = Stroke(2f))
                rotate(t * 90f, c) {
                    drawArc(a, 0f, 80f, false, Offset(c.x - r * 0.82f, c.y - r * 0.82f), Size(r * 1.64f, r * 1.64f), style = Stroke(3f, cap = StrokeCap.Round))
                    drawArc(b, 180f, 50f, false, Offset(c.x - r * 0.82f, c.y - r * 0.82f), Size(r * 1.64f, r * 1.64f), style = Stroke(3f, cap = StrokeCap.Round))
                }
                val y = c.y - r * 0.5f + (r * 1.0f) * ((t * 0.6f) % 1f)
                drawLine(fx, Offset(c.x - r * 0.45f, y), Offset(c.x + r * 0.45f, y), 2f, alpha = 0.8f * k)
            }
            FingerprintStyle.RIPPLE -> {
                for (i in 0 until 4) {
                    val p = ((t * 0.45f) + i * 0.25f) % 1f
                    drawCircle(a, r * (0.45f + 0.55f * p), c, alpha = (1f - p) * 0.8f * k, style = Stroke(2f))
                }
            }
            FingerprintStyle.HEX_LOCK -> {
                for (ring in 0 until 3) {
                    val rr = r * (0.55f + ring * 0.16f)
                    rotate((if (ring % 2 == 0) 1 else -1) * t * (20f + ring * 15f), c) { hex(c, rr, if (ring == 1) b else a, (0.8f - ring * 0.2f) * k) }
                }
            }
            FingerprintStyle.RADAR_SWEEP -> {
                drawCircle(a, r * 0.9f, c, alpha = 0.3f * k, style = Stroke(1.5f))
                drawCircle(a, r * 0.6f, c, alpha = 0.2f * k, style = Stroke(1f))
                rotate(t * 120f, c) {
                    drawArc(
                        Brush.sweepGradient(listOf(Color.Transparent, a.copy(alpha = 0.55f * k)), c), -60f, 60f, true,
                        Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f),
                    )
                }
                for (i in 0 until 5) {
                    val ang = seeds[i] * 2f * PI.toFloat()
                    val d = r * (0.4f + seeds[i + 5] * 0.45f)
                    drawCircle(b, 2.5f, Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), alpha = (0.5f + 0.5f * sin(t * 3f + i)) * k)
                }
            }
            FingerprintStyle.PARTICLE_GATHER -> {
                for (i in 0 until 36) {
                    val ang = seeds[i] * 2f * PI.toFloat() + t * 0.4f
                    val p = ((t * 0.35f) + seeds[(i + 7) % 48]) % 1f
                    val d = r * (1.0f - 0.65f * p)
                    drawCircle(if (i % 4 == 0) b else fx, 1.4f + seeds[i] * 1.6f, Offset(c.x + cos(ang) * d, c.y + sin(ang) * d), alpha = p * k)
                }
            }
            FingerprintStyle.GLITCH_IRIS -> {
                val open = 0.5f + 0.5f * sin(t * 1.4f)
                for (bl in 0 until 10) {
                    drawArc(a, bl * 36f + t * 40f, 36f * (1f - open * 0.7f), true, Offset(c.x - r * 0.85f, c.y - r * 0.85f), Size(r * 1.7f, r * 1.7f), alpha = 0.15f * k)
                }
                val jitter = if ((t * 10f).toInt() % 7 == 0) 4f else 0f
                drawCircle(b, r * 0.85f, Offset(c.x + jitter, c.y), alpha = 0.6f * k, style = Stroke(1.5f))
                drawCircle(a, r * 0.85f, Offset(c.x - jitter, c.y), alpha = 0.6f * k, style = Stroke(1.5f))
            }
        }
        fingerprintGlyph(c, r * 0.42f, if (scanning) fx else a.copy(alpha = 0.85f))
    }
}

private fun DrawScope.hex(c: Offset, r: Float, col: Color, alpha: Float) {
    val p = Path()
    for (i in 0 until 6) {
        val ang = (PI / 3 * i - PI / 2).toFloat()
        val x = c.x + r * cos(ang); val y = c.y + r * sin(ang)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, col, alpha = alpha, style = Stroke(2f))
}

/** Stylised ridge pattern (concentric arcs). */
private fun DrawScope.fingerprintGlyph(c: Offset, r: Float, col: Color) {
    for (i in 0 until 5) {
        val rr = r * (0.25f + i * 0.19f)
        val gap = 30f + i * 8f
        drawArc(col, 200f + gap / 2, 320f - gap, false, Offset(c.x - rr * 0.8f, c.y - rr), Size(rr * 1.6f, rr * 2f), style = Stroke(1.8f, cap = StrokeCap.Round))
    }
}
