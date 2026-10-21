package com.nexus.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.OrbitCore
import com.nexus.launcher.core.OrbitRingStyle
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.nexus.launcher.ui.components.AppIcon
import com.nexus.launcher.ui.components.BoundsHolder
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.longPressTrack
import com.nexus.launcher.ui.components.nexusItemGestures
import com.nexus.launcher.ui.components.rememberBounds
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.components.trackBounds
import com.nexus.launcher.ui.fx.entrance
import com.nexus.launcher.ui.drawer.DrawerMotion
import com.nexus.launcher.ui.fx.parallax
import com.nexus.launcher.ui.fx.rememberAmbientClock
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

class OrbitCallbacks(
    val labelOf: (AppInfo) -> String,
    val launch: (AppInfo, Rect) -> Unit,
    val menu: (AppInfo, Rect) -> Unit,
    val coreTap: () -> Unit,
    val coreHold: () -> Unit,
    val radialStart: (Offset) -> Unit,
    val radialMove: (Offset) -> Unit,
    val radialEnd: (Offset) -> Unit,
)

private val RING_NAMES = listOf("PREDICTIVE", "PRIMARY", "ARCHIVE")

/**
 * The ORBIT page: a reactor core with apps on three counter-rotating rings.
 * Drag a ring to spin it (with inertia), tap an icon to launch, tap the core for the shell,
 * hold the core for the glyph pad. Labels are hidden by design — you learn your orbit.
 */
@Composable
fun OrbitPage(
    predicted: List<AppInfo>,
    pool: List<AppInfo>,
    battery: BatteryState,
    badges: Map<String, Int>,
    animate: Boolean,
    cb: OrbitCallbacks,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cfg = nx.config
    val ringCount = cfg.orbitRings.coerceIn(1, 3)
    val caps = listOf(cfg.orbitRing1.coerceIn(2, 12), cfg.orbitRing2.coerceIn(3, 18), cfg.orbitRing3.coerceIn(4, 24)).take(ringCount)
    val rings = remember(predicted, pool, caps) {
        val all = (if (cfg.predictive) predicted + pool else pool).distinctBy { it.key }
        var offset = 0
        caps.map { n -> all.drop(offset).take(n).also { offset += n } }
    }
    val angles = remember { List(3) { i -> Animatable(i * 17f) } }
    val spin = rememberAmbientClock(animate && cfg.orbitSpin && cfg.orbitSpinSpeed > 0f)
    val speed = cfg.orbitSpinSpeed
    fun dir(i: Int) = if (!cfg.orbitAlternate || i % 2 == 0) 1f else -1f
    val bgBounds = rememberBounds()
    val measurer = rememberTextMeasurer()
    val ringLabel = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 7.sp, color = p.accent.copy(alpha = 0.6f), letterSpacing = 3.sp)
    val ringLabels = remember(ringLabel) { RING_NAMES.map { measurer.measure(it, ringLabel) } }

    BoxWithConstraints(modifier.fillMaxSize().trackBounds(bgBounds)) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val center = Offset(wPx / 2f, hPx * 0.47f)
        val unit = minOf(wPx, hPx * 0.95f)
        val radii = when (ringCount) {
            1 -> listOf(unit * 0.32f)
            2 -> listOf(unit * 0.25f, unit * 0.41f)
            else -> listOf(unit * 0.21f, unit * 0.33f, unit * 0.45f)
        }
        val coreR = unit * 0.13f
        val sc = cfg.orbitIconScale.coerceIn(0.6f, 1.5f)
        val iconDp = listOf(cfg.iconSize * 0.82f * sc, cfg.iconSize * 0.74f * sc, cfg.iconSize * 0.64f * sc)
        val radiiNow by rememberUpdatedState(radii)

        fun ringAt(o: Offset): Int {
            val d = hypot(o.x - center.x, o.y - center.y)
            return radiiNow.indexOfFirst { kotlin.math.abs(d - it) < unit * 0.065f }
        }

        // background: rotation drags on rings, long-press anywhere else → radial launcher
        Box(
            Modifier
                .fillMaxSize()
                .longPressTrack(bgBounds, cb.radialStart, cb.radialMove, cb.radialEnd)
                .pointerInput(center, unit, ringCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val ring = ringAt(down.position)
                        if (ring < 0) return@awaitEachGesture
                        val anim = angles[ring]
                        var prev = down.position
                        var lastTime = down.uptimeMillis
                        var velocity = 0f
                        var dragging = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val now = ch.position
                            if (!dragging && hypot(now.x - down.position.x, now.y - down.position.y) > viewConfiguration.touchSlop) {
                                // The rings cover much of the page: a clearly vertical stroke is the
                                // home swipe (drawer / notifications), not a spin. Leave it unconsumed.
                                if (DrawerMotion.isVerticalSwipe(now.x - down.position.x, now.y - down.position.y)) break
                                dragging = true; haptic(Haptic.TICK)
                            }
                            if (dragging) {
                                ch.consume()
                                val a0 = atan2(prev.y - center.y, prev.x - center.x)
                                val a1 = atan2(now.y - center.y, now.x - center.x)
                                var d = Math.toDegrees((a1 - a0).toDouble()).toFloat()
                                if (d > 180f) d -= 360f
                                if (d < -180f) d += 360f
                                val dt = (ch.uptimeMillis - lastTime).coerceAtLeast(1L)
                                velocity = velocity * 0.6f + (d / dt * 1000f) * 0.4f
                                lastTime = ch.uptimeMillis
                                scope.launch { anim.snapTo(anim.value + d) }
                                prev = now
                            }
                            if (ch.positionChange() != Offset.Zero && dragging) ch.consume()
                        }
                        if (dragging) scope.launch { anim.animateDecay(velocity.coerceIn(-1400f, 1400f), exponentialDecay(frictionMultiplier = 1.4f)) }
                    }
                }
        )

        // rings + core (one Canvas, draw-phase only)
        Canvas(Modifier.fillMaxSize().parallax(-0.4f).entrance(0)) {
            val t = spin.value * speed
            radii.forEachIndexed { i, r ->
                val a = angles[i].value + t * (if (i % 2 == 0) 4f else 3f) * dir(i)
                when (cfg.orbitRingStyle) {
                    OrbitRingStyle.TICKS -> {
                        drawCircle(p.accent, r, center, alpha = 0.10f, style = Stroke(unit * 0.11f))
                        drawCircle(p.accent, r, center, alpha = 0.30f, style = Stroke(1f))
                        rotate(a, center) {
                            for (k in 0 until 72) {
                                val ang = k * 5f * PI.toFloat() / 180f
                                val inner = r + unit * 0.05f
                                val outer = inner + if (k % 6 == 0) 7f else 3f
                                drawLine(p.accent, Offset(center.x + cos(ang) * inner, center.y + sin(ang) * inner), Offset(center.x + cos(ang) * outer, center.y + sin(ang) * outer), 1f, alpha = 0.35f)
                            }
                        }
                    }
                    OrbitRingStyle.SOLID -> drawCircle(p.accent, r, center, alpha = 0.16f, style = Stroke(unit * 0.11f))
                    OrbitRingStyle.DASHED -> rotate(a, center) {
                        drawCircle(p.accent, r, center, alpha = 0.5f, style = Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 12f))))
                    }
                    OrbitRingStyle.NEON -> {
                        drawCircle(p.accent, r, center, alpha = 0.08f, style = Stroke(14f))
                        drawCircle(p.accent, r, center, alpha = 0.25f, style = Stroke(5f))
                        drawCircle(if (i % 2 == 0) p.accent else p.accent2, r, center, alpha = 0.9f, style = Stroke(1.5f))
                    }
                    OrbitRingStyle.NONE -> Unit
                }
                if (cfg.orbitLegend && nx.tech && i < ringLabels.size) rotate(a, center) {
                    drawText(ringLabels[i], topLeft = Offset(center.x - ringLabels[i].size.width / 2f, center.y - r - unit * 0.075f))
                }
            }
            when (cfg.orbitCore) {
                OrbitCore.REACTOR -> {
                    for (seg in 0 until 3) {
                        rotate(t * (30f + seg * 25f) * (if (seg % 2 == 0) 1 else -1), center) {
                            val rr = coreR * (0.7f + seg * 0.18f)
                            for (g in 0 until 4) drawArc(if (seg == 1) p.accent2 else p.accent, g * 90f, 55f, false, Offset(center.x - rr, center.y - rr), Size(rr * 2, rr * 2), alpha = 0.7f, style = Stroke(2.5f, cap = StrokeCap.Round))
                        }
                    }
                    drawArc(p.fx, -90f, 360f * battery.level / 100f, false, Offset(center.x - coreR * 1.25f, center.y - coreR * 1.25f), Size(coreR * 2.5f, coreR * 2.5f), style = Stroke(3f, cap = StrokeCap.Round))
                    val pulse = 0.5f + 0.5f * sin(t * 2.2f)
                    drawCircle(Brush.radialGradient(listOf(p.fx.copy(alpha = 0.55f), p.accent.copy(alpha = 0.15f), Color.Transparent), center, coreR * (0.9f + 0.1f * pulse)), coreR * (0.9f + 0.1f * pulse), center)
                }
                OrbitCore.BATTERY -> {
                    drawCircle(p.accent, coreR * 1.1f, center, alpha = 0.15f, style = Stroke(8f))
                    drawArc(p.accent, -90f, 360f * battery.level / 100f, false, Offset(center.x - coreR * 1.1f, center.y - coreR * 1.1f), Size(coreR * 2.2f, coreR * 2.2f), style = Stroke(8f, cap = StrokeCap.Round))
                }
                OrbitCore.CLOCK, OrbitCore.MINIMAL -> {
                    drawCircle(p.panel, coreR, center)
                    drawCircle(p.border, coreR, center, style = Stroke(1.5f))
                }
            }
        }

        // core: configurable tap / hold actions
        val coreDp = with(density) { (coreR * 2f).toDp() }
        Box(
            Modifier
                .offset { IntOffset((center.x - coreR).roundToInt(), (center.y - coreR).roundToInt()) }
                .size(coreDp)
                .entrance(1)
                .nexusItemGestures(key = "core", onTap = { haptic(Haptic.CONFIRM); cb.coreTap() }, onLongPress = { cb.coreHold() }, onLongPressStart = { haptic(Haptic.PRESS) }),
            contentAlignment = Alignment.Center,
        ) {
            val now by rememberNow(false)
            when (cfg.orbitCore) {
                OrbitCore.BATTERY -> Text("${battery.level}%", style = nx.display(18f, p.text, weight = 300))
                OrbitCore.MINIMAL -> Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.accent))
                OrbitCore.CLOCK -> Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = nx.display(20f, p.text, weight = 300))
                OrbitCore.REACTOR -> com.nexus.launcher.ui.fx.DotMatrixText(now.format(DateTimeFormatter.ofPattern("HH:mm")), pitch = (coreDp / 24f), color = p.text, unlitAlpha = 0f)
            }
        }

        // icons on rings — positions are computed in the layout phase only
        rings.forEachIndexed { ri, ring ->
            val sizeDp = iconDp[ri]
            val half = with(density) { (sizeDp / 2f).dp.toPx() }
            val labelPx = if (cfg.orbitLabels) with(density) { 9.dp.toPx() } else 0f
            ring.forEachIndexed { idx, app ->
                key(app.key.id) {
                    val bounds = rememberBounds()
                    val base = idx * 360f / ring.size.coerceAtLeast(1)
                    Box(
                        Modifier
                            .offset {
                                val a = Math.toRadians((base + angles[ri].value + spin.value * speed * (if (ri % 2 == 0) 4f else 3f) * dir(ri)).toDouble())
                                IntOffset(
                                    (center.x + radii[ri] * cos(a).toFloat() - half - (if (cfg.orbitLabels) with(density) { 11.dp.toPx() } else 0f)).roundToInt(),
                                    (center.y + radii[ri] * sin(a).toFloat() - half - labelPx).roundToInt(),
                                )
                            }
                            .entrance(2 + ri * 2)
                            .parallax(0.3f + ri * 0.25f)
                            .trackBounds(bounds)
                            .nexusItemGestures(
                                key = app.key.id,
                                onTap = { cb.launch(app, bounds.rect) },
                                onLongPress = { cb.menu(app, bounds.rect) },
                                onLongPressStart = { haptic(Haptic.PRESS) },
                            )
                    ) {
                        AppIcon(app, cb.labelOf(app), sizeDp, showLabel = cfg.orbitLabels, badge = badges[app.component.packageName] ?: 0)
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun BoundsHolder.center(): Offset = rect.center
