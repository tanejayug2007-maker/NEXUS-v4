package com.nexus.launcher.ui.fx

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.UnlockAnim
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** 0 → 1 progress of the post-unlock reveal. 1 means "fully shown" (the resting state). */
val LocalEntrance = compositionLocalOf<State<Float>> { mutableFloatStateOf(1f) }

private fun localProgress(p: Float, order: Int): Float {
    val start = (order * 0.07f).coerceAtMost(0.45f)
    return FastOutSlowInEasing.transform(((p - start) / 0.55f).coerceIn(0f, 1f))
}

/**
 * Staggered reveal for one home-screen element. Reads progress inside graphicsLayer so the
 * animation only touches the layer, never recomposition.
 */
@Composable
fun Modifier.entrance(order: Int): Modifier {
    val progress = LocalEntrance.current
    val style = LocalNexus.current.config.unlockAnim
    val shift = with(LocalDensity.current) { 36.dp.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        if (p >= 1f) return@graphicsLayer
        val l = localProgress(p, order)
        when (style) {
            UnlockAnim.IRIS_SCAN -> {
                alpha = l
                val sc = 1.18f - 0.18f * l
                scaleX = sc; scaleY = sc
            }
            UnlockAnim.BOOT_SEQUENCE -> {
                alpha = if (l > 0.35f) 1f else 0f
                translationX = if (l < 0.6f) ((order * 37) % 11 - 5) * 1.5f else 0f
            }
            UnlockAnim.HOLOGRAM_PROJECTION -> {
                val flick = if (l < 0.7f && sin(l * 60f + order * 2f) < -0.5f) 0.4f else 1f
                alpha = l * flick
                translationY = (1f - l) * shift * 3f
                scaleY = 0.15f + 0.85f * l
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            UnlockAnim.HOLOGRAPHIC_AWAKENING -> {
                val flicker = if (l < 0.65f && sin(l * 48f + order) < -0.35f) 0.35f else 1f
                alpha = l * flicker
                scaleY = 0.72f + 0.28f * l
                scaleX = 0.96f + 0.04f * l
            }
            UnlockAnim.NEURAL_INITIALIZATION -> {
                alpha = l
                translationY = (1f - l) * shift
            }
            UnlockAnim.QUANTUM_BOOT -> {
                alpha = l
                scaleX = 0.82f + 0.18f * l
                scaleY = 0.82f + 0.18f * l
            }
            UnlockAnim.DIGITAL_RECONSTRUCTION -> {
                alpha = (l * 6f).toInt() / 6f
                translationX = (1f - l) * (if (order % 2 == 0) 1f else -1f) * shift * 0.4f
            }
        }
    }
}

/** Full-screen accent effect that plays over the reveal. Renders nothing at rest. */
@Composable
fun EntranceOverlay(progress: State<Float>) {
    val nx = LocalNexus.current
    val style = nx.config.unlockAnim
    val accent = nx.palette.accent
    val fx = nx.palette.fx
    val nodes = remember { Random(42).let { r -> List(18) { Offset(r.nextFloat(), r.nextFloat()) } } }
    val measurer = rememberTextMeasurer()
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = accent)
    val bootLines = remember(mono) {
        listOf(
            "[ 0.000] NEXUS kernel handshake", "[ 0.012] mounting /holo/surface", "[ 0.031] calibrating optic array .... OK",
            "[ 0.044] neural mesh 4096 nodes ... OK", "[ 0.058] loading glyph lexicon", "[ 0.071] uplink negotiation ...... OK",
            "[ 0.090] power cell telemetry ..... OK", "[ 0.102] spawning orbit rings", "[ 0.118] shader cache warm",
            "[ 0.131] identity: VERIFIED BY ANDROID", "[ 0.140] HUD online", "[ 0.152] >>> WELCOME, OPERATOR",
        ).map { measurer.measure(it, mono) }
    }
    val verified = remember(accent) { measurer.measure("IDENTITY VERIFIED", mono.copy(fontSize = 12.sp, letterSpacing = 4.sp)) }
    if (progress.value >= 1f) return

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            val fade = (1f - p).coerceIn(0f, 1f)
            when (style) {
                UnlockAnim.IRIS_SCAN -> {
                    val c = center
                    val r = size.minDimension * 0.34f
                    val open = FastOutSlowInEasing.transform((p / 0.7f).coerceIn(0f, 1f))
                    drawRect(Color.Black, alpha = (1f - open) * 0.85f)
                    for (b in 0 until 16) {
                        val start = b * 22.5f + open * 40f
                        drawArc(
                            accent, start, 22.5f * (1f - open) + 2f, true,
                            Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2),
                            alpha = 0.18f * fade,
                        )
                    }
                    drawCircle(accent, r * (0.2f + 0.8f * open), c, alpha = fade, style = Stroke(2f))
                    drawCircle(accent, r * 1.08f, c, alpha = 0.5f * fade, style = Stroke(1f))
                    val scanY = c.y - r + (2 * r) * ((p * 2.2f) % 1f)
                    drawLine(fx, Offset(c.x - r, scanY), Offset(c.x + r, scanY), strokeWidth = 2f, alpha = fade)
                    if (p > 0.45f) drawText(verified, topLeft = Offset(c.x - verified.size.width / 2f, c.y + r + 24f), alpha = fade)
                }
                UnlockAnim.BOOT_SEQUENCE -> {
                    drawRect(Color.Black, alpha = (1f - p * 1.3f).coerceIn(0f, 0.9f))
                    val shown = (bootLines.size * (p * 1.8f)).toInt().coerceAtMost(bootLines.size)
                    var y = size.height * 0.18f
                    for (i in 0 until shown) {
                        drawText(bootLines[i], topLeft = Offset(48f, y), alpha = fade)
                        y += bootLines[i].size.height + 6f
                    }
                    if (shown < bootLines.size) drawRect(fx, Offset(48f, y + 2f), androidx.compose.ui.geometry.Size(14f, 22f), alpha = fade)
                }
                UnlockAnim.HOLOGRAM_PROJECTION -> {
                    val base = size.height - 40f
                    val beamTop = size.height * (1f - p)
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.18f * fade), accent.copy(alpha = 0.35f * fade)), startY = beamTop, endY = base),
                        topLeft = Offset(0f, beamTop), size = androidx.compose.ui.geometry.Size(size.width, base - beamTop),
                    )
                    drawLine(fx, Offset(size.width * 0.15f, base), Offset(size.width * 0.85f, base), strokeWidth = 4f, alpha = fade)
                    var y = beamTop
                    while (y < base) { drawLine(accent, Offset(0f, y), Offset(size.width, y), 1f, alpha = 0.07f * fade); y += 5f }
                }
                UnlockAnim.HOLOGRAPHIC_AWAKENING -> {
                    val y = size.height * (p * 1.2f - 0.1f)
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.22f * fade), Color.Transparent), startY = y - 140f, endY = y + 140f),
                        topLeft = Offset(0f, y - 140f), size = androidx.compose.ui.geometry.Size(size.width, 280f),
                    )
                    drawLine(fx, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f, alpha = 0.8f * fade)
                }
                UnlockAnim.NEURAL_INITIALIZATION -> {
                    val pts = nodes.map { Offset(it.x * size.width, it.y * size.height) }
                    val visible = (pts.size * (p * 1.6f)).toInt().coerceAtMost(pts.size)
                    for (i in 1 until visible) {
                        drawLine(accent, pts[i - 1], pts[i], strokeWidth = 1.2f, alpha = 0.45f * fade)
                    }
                    for (i in 0 until visible) drawCircle(fx, 3.5f, pts[i], alpha = 0.9f * fade)
                }
                UnlockAnim.QUANTUM_BOOT -> {
                    val c = center
                    val r = size.maxDimension * p
                    drawCircle(accent, r, c, alpha = 0.7f * fade, style = Stroke(width = 2f + 6f * fade))
                    drawCircle(accent, r * 0.66f, c, alpha = 0.35f * fade, style = Stroke(width = 1.5f))
                    drawCircle(Brush.radialGradient(listOf(fx.copy(alpha = 0.5f * fade), Color.Transparent), c, 140f), 140f, c)
                }
                UnlockAnim.DIGITAL_RECONSTRUCTION -> {
                    val y = size.height * p
                    val rows = 26
                    val rh = size.height / rows
                    for (row in 0 until rows) {
                        val ry = row * rh
                        if (ry < y - rh * 3 || ry > y) continue
                        val seg = (sin(row * 12.9898f + p * 30f) * 43758.547f) % 1f
                        drawRect(accent, Offset(size.width * (0.5f + seg * 0.5f).coerceIn(0f, 0.9f) * 0.5f, ry), androidx.compose.ui.geometry.Size(size.width * 0.4f, rh * 0.5f), alpha = 0.18f * fade)
                    }
                    drawLine(fx, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, alpha = fade)
                }
            }
        }
        if (style == UnlockAnim.QUANTUM_BOOT) {
            DotMatrixText(
                text = "ONLINE",
                pitch = 5.dp,
                color = accent,
                modifier = Modifier.graphicsLayer { alpha = (1f - progress.value * 1.4f).coerceIn(0f, 1f) },
                unlitAlpha = 0f,
            )
        }
    }
}
