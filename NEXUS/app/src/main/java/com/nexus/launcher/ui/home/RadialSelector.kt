package com.nexus.launcher.ui.home

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.ui.components.AppIconImage
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Live state of the marking menu: where it opened and where the finger is now. */
data class RadialState(val center: Offset, val pointer: Offset)

const val RADIAL_SLOTS = 8

/** Slot under the finger: 0..n-1, or -1 when inside the dead zone (release there = cancel). */
fun radialSlot(s: RadialState, deadZonePx: Float, n: Int = RADIAL_SLOTS): Int {
    val dx = s.pointer.x - s.center.x
    val dy = s.pointer.y - s.center.y
    if (hypot(dx, dy) < deadZonePx) return -1
    val deg = (Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0 + 360.0 + 360.0 / n / 2) % 360.0
    return (deg / (360.0 / n)).toInt().coerceIn(0, n - 1)
}

/**
 * Marking menu: long-press empty space, keep holding, flick toward a sector, release.
 * Slot 0 (top) is EDIT; the other seven are your predicted / docked apps.
 */
@Composable
fun RadialSelector(
    state: RadialState,
    apps: List<AppInfo?>,
    labelOf: (AppInfo) -> String,
    slots: Int = RADIAL_SLOTS,
    radiusDp: Float = 104f,
    showLabels: Boolean = true,
    editSlot: Boolean = true,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val density = LocalDensity.current
    val radius = with(density) { radiusDp.dp.toPx() }
    val dead = with(density) { 34.dp.toPx() }
    val appear = remember(state.center) { Animatable(0f) }
    LaunchedEffect(state.center) { appear.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 700f)) }
    val n = slots.coerceIn(3, 10)
    val sel = radialSlot(state, dead, n)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = constraints.maxHeight.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val k = appear.value
            drawRect(Color.Black, alpha = 0.45f * k)
            val c = state.center
            val r = radius * k
            // First frame: appear == 0 → radius 0, and a 0-radius gradient throws. Nothing to draw yet.
            if (r < 1f) return@Canvas
            drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.18f), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
            drawCircle(p.accent, r, c, alpha = 0.35f, style = Stroke(r * 0.55f))
            drawCircle(p.accent, r * 1.28f, c, alpha = 0.6f, style = Stroke(1.5f))
            drawCircle(p.accent, dead, c, alpha = 0.5f, style = Stroke(1.5f))
            val sweep = 360f / n
            for (i in 0 until n) {
                val start = -90f - sweep / 2 + i * sweep
                if (i == sel) {
                    drawArc(p.accent2, start + 2f, sweep - 4f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), alpha = 0.9f, style = Stroke(r * 0.55f))
                }
                val a = Math.toRadians(start.toDouble()).toFloat()
                drawLine(p.bg, Offset(c.x + cos(a) * r * 0.72f, c.y + sin(a) * r * 0.72f), Offset(c.x + cos(a) * r * 1.28f, c.y + sin(a) * r * 1.28f), 3f)
            }
            drawLine(p.fx, c, state.pointer, strokeWidth = 1.5f, alpha = 0.6f)
            drawCircle(p.fx, 5f, state.pointer)
        }
        for (i in 0 until n) {
            val a = (-90.0 + i * 360.0 / n) * PI / 180.0
            val pos = Offset(state.center.x + (radius * cos(a)).toFloat(), state.center.y + (radius * sin(a)).toFloat())
            val app = apps.getOrNull(i)
            Box(
                Modifier
                    .layout { m, cons ->
                        val pl = m.measure(cons.copy(minWidth = 0, minHeight = 0))
                        layout(pl.width, pl.height) { pl.place(0, 0) }
                    }
                    .offset { IntOffset((pos.x - 18.dp.toPx()).roundToInt(), (pos.y - 18.dp.toPx()).roundToInt()) }
                    .graphicsLayer {
                        val s = appear.value * if (i == sel) 1.25f else 1f
                        scaleX = s; scaleY = s; alpha = appear.value
                    }
            ) {
                when {
                    i == 0 && editSlot -> Text(nx.caps("Edit"), style = nx.label(9f, if (sel == 0) p.text else p.accent), modifier = Modifier.offset(y = 10.dp))
                    app != null -> AppIconImage(app, 36f)
                    else -> Text("—", style = nx.label(10f, p.textFaint), modifier = Modifier.offset(x = 14.dp, y = 8.dp))
                }
            }
        }
        val label = when {
            sel == 0 && editSlot -> "Edit home"
            sel >= 0 -> apps.getOrNull(sel)?.let(labelOf) ?: "Empty slot"
            else -> "Release in centre to cancel"
        }
        if (showLabels || sel < 0) {
            // Centred under the ring; above it when the ring sits low (the dock lives down there).
            val below = state.center.y + radius * 1.5f
            val y = if (below > screenH - with(density) { 170.dp.toPx() }) state.center.y - radius * 1.5f - with(density) { 20.dp.toPx() } else below
            Text(
                nx.caps(label),
                style = nx.label(10f, p.text),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().offset { IntOffset(0, y.roundToInt()) },
            )
        }
    }
}
