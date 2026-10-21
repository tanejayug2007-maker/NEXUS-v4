package com.nexus.launcher.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.BackgroundStyle
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Seconds since [running] became true, advanced only on frames while running. Reading the
 * returned state inside a draw lambda invalidates draw only — no recomposition per frame —
 * and when [running] goes false the loop ends so nothing renders while idle.
 */
@Composable
fun rememberAmbientClock(running: Boolean, maxFps: Int = 0): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, maxFps) {
        if (!running) return@LaunchedEffect
        // maxFps > 0 publishes at most that many updates per second: slow ambient layers
        // (backgrounds, HUD) don't need 120 Hz, which halves or quarters their GPU work.
        val minStep = if (maxFps > 0) 1_000_000_000L / maxFps else 0L
        var last = withFrameNanos { it }
        var acc = 0L
        while (true) {
            withFrameNanos { now ->
                acc += (now - last).coerceAtMost(100_000_000L)
                last = now
                if (acc >= minStep) {
                    t.floatValue += acc / 1_000_000_000f
                    acc = 0L
                }
            }
        }
    }
    return t
}

@Composable
fun NexusBackground(animate: Boolean, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val spec = nx.spec
    val run = animate && spec.animatedBackground && nx.quality.ambientEnabled
    val clock = rememberAmbientClock(run, maxFps = if (nx.quality.tier >= 2) 60 else 30)

    when (spec.backgroundStyle) {
        BackgroundStyle.NEBULA -> NebulaBackground(clock, LocalTilt.current, modifier)
        BackgroundStyle.HOLO_GRID -> HoloGridBackground(clock, LocalTilt.current, modifier)
        BackgroundStyle.VOID -> Box(modifier.background(p.bg))
        BackgroundStyle.GRADIENT -> Box(
            modifier.background(Brush.linearGradient(listOf(p.bg, androidx.compose.ui.graphics.lerp(p.bg, p.accent, if (p.isLight) 0.10f else 0.18f), androidx.compose.ui.graphics.lerp(p.bg, p.accent2, if (p.isLight) 0.08f else 0.14f))))
        )
        BackgroundStyle.AURORA -> Box(modifier.background(p.bg).drawWithCache {
            val a = if (p.isLight) 0.18f else 0.28f
            onDrawBehind {
                val t = clock.value * 0.05f
                val blobs = listOf(
                    Triple(p.accent, Offset(size.width * (0.3f + 0.15f * sin(t * 1.3f)), size.height * (0.25f + 0.08f * sin(t * 0.9f))), size.width * 0.9f),
                    Triple(p.accent2, Offset(size.width * (0.75f + 0.12f * sin(t * 1.1f + 2f)), size.height * (0.55f + 0.1f * sin(t * 0.7f + 1f))), size.width * 0.8f),
                    Triple(p.fx, Offset(size.width * (0.45f + 0.2f * sin(t * 0.6f + 4f)), size.height * (0.85f + 0.05f * sin(t + 3f))), size.width * 0.7f),
                )
                if (size.minDimension >= 1f) blobs.forEach { (c, o, r) -> drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), o, r), r, o) }
            }
        })
        BackgroundStyle.WALLPAPER -> Box(modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(0.55f), Color.Black.copy(0.15f), Color.Black.copy(0.6f)))))
        BackgroundStyle.DOT_GRID -> Box(modifier.background(p.bg).drawWithCache {
            val step = 22.dp.toPx()
            val r = 1.1.dp.toPx()
            val dots = Path()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    dots.addOval(Rect(Offset(x, y), r)); x += step
                }
                y += step
            }
            val band = 90.dp.toPx()
            onDrawBehind {
                drawPath(dots, p.accent, alpha = 0.10f)
                if (run || clock.value > 0f) {
                    val by = ((clock.value * 0.07f) % 1f) * (size.height + band * 2) - band
                    clipRect(top = by - band, bottom = by + band) {
                        drawPath(dots, p.accent, alpha = 0.38f * (0.5f + spec.glow))
                    }
                }
            }
        })
        BackgroundStyle.CIRCUIT -> Box(modifier.background(p.bg).drawWithCache {
            val traces = generateTraces(size.width, size.height, 18.dp.toPx(), seed = 2050)
            val path = Path().apply {
                traces.forEach { t ->
                    moveTo(t.first().x, t.first().y); t.drop(1).forEach { lineTo(it.x, it.y) }
                }
            }
            val pad = 2.2.dp.toPx()
            val stroke = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            val pulseCount = (traces.size * (0.35f + 0.65f * nx.quality.particles)).toInt().coerceAtLeast(4)
            onDrawBehind {
                drawPath(path, p.accent, alpha = 0.085f, style = stroke)
                traces.forEach { t -> drawCircle(p.accent, pad, t.last(), alpha = 0.18f) }
                if ((run || clock.value > 0f) && traces.isNotEmpty()) {
                    val time = clock.value
                    for (i in 0 until pulseCount) {
                        val tr = traces[i % traces.size]
                        val f = ((time * 0.12f + i * 0.618f) % 1f)
                        val pt = pointAlong(tr, f)
                        drawCircle(p.accent, 6.dp.toPx(), pt, alpha = 0.06f * (0.5f + spec.glow))
                        drawCircle(p.fx, 1.6.dp.toPx(), pt, alpha = 0.75f)
                    }
                }
            }
        })
        BackgroundStyle.PARTICLES -> Box(modifier.background(p.bg).drawWithCache {
            val n = (90 * spec.particleIntensity * (0.3f + 0.7f * nx.quality.particles)).toInt().coerceIn(12, 120)
            val rnd = Random(7)
            val px = FloatArray(n) { rnd.nextFloat() * size.width }
            val py = FloatArray(n) { rnd.nextFloat() * size.height }
            val vx = FloatArray(n) { (rnd.nextFloat() - 0.5f) * 12f }
            val vy = FloatArray(n) { -4f - rnd.nextFloat() * 14f }
            val rad = FloatArray(n) { 0.6f + rnd.nextFloat() * 1.6f }
            val ph = FloatArray(n) { rnd.nextFloat() * 6.28f }
            val glowBrush = Brush.radialGradient(
                listOf(p.accent.copy(alpha = 0.10f * (0.4f + spec.glow)), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.35f), radius = (size.width * 0.9f).coerceAtLeast(1f),
            )
            onDrawBehind {
                drawRect(glowBrush)
                val t = clock.value
                for (i in 0 until n) {
                    val x = ((px[i] + vx[i] * t) % size.width + size.width) % size.width
                    val y = ((py[i] + vy[i] * t) % size.height + size.height) % size.height
                    val a = 0.25f + 0.35f * (0.5f + 0.5f * sin(t * 1.3f + ph[i]))
                    drawCircle(if (i % 5 == 0) p.accent2 else p.accent, rad[i].dp.toPx(), Offset(x, y), alpha = a)
                }
            }
        })
    }
}

/** Procedural circuit traces: orthogonal + 45° runs on a grid, seeded so they never jump. */
internal fun generateTraces(w: Float, h: Float, step: Float, seed: Int): List<List<Offset>> {
    if (w <= 0f || h <= 0f) return emptyList()
    val rnd = Random(seed)
    val count = ((w * h) / (step * step * 26f)).toInt().coerceIn(14, 60)
    val dirs = listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f), Offset(1f, 1f), Offset(-1f, 1f), Offset(1f, -1f), Offset(-1f, -1f))
    return List(count) {
        var pt = Offset((rnd.nextInt((w / step).toInt().coerceAtLeast(1)) * step), (rnd.nextInt((h / step).toInt().coerceAtLeast(1)) * step))
        val pts = mutableListOf(pt)
        var dir = dirs[rnd.nextInt(4)]
        repeat(2 + rnd.nextInt(4)) {
            val len = (2 + rnd.nextInt(6)) * step
            val n = Offset(pt.x + dir.x * len, pt.y + dir.y * len)
            pt = Offset(n.x.coerceIn(0f, w), n.y.coerceIn(0f, h))
            pts += pt
            dir = if (abs(dir.x) + abs(dir.y) > 1.5f) dirs[rnd.nextInt(4)] else dirs[4 + rnd.nextInt(4)].takeIf { rnd.nextBoolean() } ?: dirs[rnd.nextInt(4)]
        }
        pts
    }
}

internal fun pointAlong(pts: List<Offset>, f: Float): Offset {
    if (pts.size < 2) return pts.firstOrNull() ?: Offset.Zero
    var total = 0f
    for (i in 1 until pts.size) total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
    var target = total * f
    for (i in 1 until pts.size) {
        val seg = hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        if (target <= seg && seg > 0f) {
            val k = target / seg
            return Offset(pts[i - 1].x + (pts[i].x - pts[i - 1].x) * k, pts[i - 1].y + (pts[i].y - pts[i - 1].y) * k)
        }
        target -= seg
    }
    return pts.last()
}


/** Tron-style perspective floor racing toward the viewer, with a horizon sun and star field. */
@Composable
fun HoloGridBackground(clock: State<Float>, tilt: State<Offset>, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val stars = remember { Random(31).let { r -> List(90) { Offset(r.nextFloat(), r.nextFloat()) } } }
    Box(modifier.background(p.bg).drawWithCache {
        val stroke = 1.dp.toPx()
        onDrawBehind {
            val t = clock.value
            val tl = tilt.value
            val horizon = size.height * (0.60f + tl.y * 0.02f)
            val vx = size.width / 2f + tl.x * size.width * 0.06f
            // sky
            stars.forEach { s ->
                if (s.y * horizon < horizon - 4f) drawCircle(p.fx, 1.1f, Offset(s.x * size.width, s.y * horizon * 0.95f), alpha = 0.35f)
            }
            // horizon sun with slices
            val sunR = size.width * 0.24f
            drawCircle(Brush.verticalGradient(listOf(p.accent2.copy(alpha = 0.55f), p.accent.copy(alpha = 0.35f)), startY = horizon - sunR, endY = horizon), sunR, Offset(vx, horizon))
            for (k in 0 until 6) {
                val y = horizon - sunR * (0.1f + k * 0.13f)
                drawLine(p.bg, Offset(vx - sunR, y), Offset(vx + sunR, y), strokeWidth = 2f + k * 1.2f)
            }
            drawRect(p.bg, topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(size.width, size.height - horizon))
            drawRect(
                Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.25f), androidx.compose.ui.graphics.Color.Transparent), startY = horizon, endY = horizon + size.height * 0.12f),
                topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.12f),
            )
            // converging verticals
            val lanes = 16
            for (i in -lanes..lanes) {
                val bottomX = vx + i * size.width * 0.16f
                drawLine(p.accent, Offset(vx + i * 6f, horizon), Offset(bottomX, size.height), strokeWidth = stroke, alpha = 0.32f)
            }
            // horizontals moving toward the viewer
            val rows = 18
            val phase = (t * 0.35f) % 1f
            for (k in 0 until rows) {
                val z = (k + 1 - phase) / rows          // 0 (far) .. 1 (near)
                val y = horizon + (size.height - horizon) * z * z
                drawLine(p.accent, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke * (0.6f + z * 1.4f), alpha = 0.10f + 0.4f * z)
            }
        }
    })
}
