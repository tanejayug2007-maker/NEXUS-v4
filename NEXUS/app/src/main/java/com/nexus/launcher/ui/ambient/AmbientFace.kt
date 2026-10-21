package com.nexus.launcher.ui.ambient

import com.nexus.launcher.ui.components.produceWhileVisible
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.AodStyle
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.ClockStyle
import com.nexus.launcher.notifications.NexusNotification
import com.nexus.launcher.ui.fx.DotMatrixText
import com.nexus.launcher.ui.fx.NexusClock
import com.nexus.launcher.ui.fx.rememberAmbientClock
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Moves content a few dp every minute so OLED pixels are never lit in one place for long. */
@Composable
fun rememberBurnInShift(): IntOffset {
    val shift by produceWhileVisible(IntOffset.Zero) {
        val r = Random(System.currentTimeMillis())
        while (true) {
            delay(60_000)
            value = IntOffset(r.nextInt(-14, 15), r.nextInt(-20, 21))
        }
    }
    return shift
}

/**
 * The six always-on faces. Mostly-black frames with thin lines keep OLED power low.
 * Animation is limited to a slow pulse (a few frames per second is enough here).
 */
@Composable
fun AmbientFace(
    style: AodStyle,
    clockStyle: ClockStyle,
    battery: BatteryState,
    notifications: List<NexusNotification>,
    showNotifications: Boolean,
    dim: Float,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val shift = rememberBurnInShift()
    val clock = rememberAmbientClock(style == AodStyle.EDGE_PULSE || style == AodStyle.CONSTELLATION)
    val apps = remember(notifications) { notifications.filter { !it.ongoing }.map { it.appLabel }.distinct().take(5) }
    val k = dim.coerceIn(0.1f, 1f)

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (style) {
            AodStyle.EDGE_PULSE -> Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                val perim = 2 * (size.width + size.height)
                val pos = (t * 260f) % perim
                fun pt(d: Float): Offset {
                    var x = d
                    if (x < size.width) return Offset(x, 2f); x -= size.width
                    if (x < size.height) return Offset(size.width - 2f, x); x -= size.height
                    if (x < size.width) return Offset(size.width - x, size.height - 2f); x -= size.width
                    return Offset(2f, size.height - x)
                }
                for (i in 0 until 40) drawCircle(if (i < 6) p.fx else p.accent, 3f - i * 0.06f, pt((pos - i * 9f + perim) % perim), alpha = (1f - i / 40f) * k)
                drawRoundRect(p.accent, Offset(1f, 1f), Size(size.width - 2f, size.height - 2f), androidx.compose.ui.geometry.CornerRadius(36f), alpha = 0.10f * k, style = Stroke(2f))
            }
            AodStyle.CONSTELLATION -> {
                val stars = remember { Random(77).let { r -> List(26) { Offset(r.nextFloat(), r.nextFloat()) } } }
                Canvas(Modifier.fillMaxSize()) {
                    val t = clock.value
                    val pts = stars.map { Offset(it.x * size.width, it.y * size.height) }
                    for (i in 1 until pts.size) if (i % 3 != 0) drawLine(p.accent, pts[i - 1], pts[i], 0.8f, alpha = 0.14f * k)
                    pts.forEachIndexed { i, s -> drawCircle(p.fx, 1.6f, s, alpha = (0.3f + 0.4f * sin(t * 0.8f + i)) * k) }
                }
            }
            else -> Unit
        }
        Column(
            Modifier.fillMaxSize().offset { shift }.padding(horizontal = 28.dp, vertical = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (style) {
                AodStyle.DOT_CLOCK -> NexusClock(ClockStyle.DOT_MATRIX, Modifier.fillMaxWidth(), dim = k)
                AodStyle.HOLO_RING -> NexusClock(ClockStyle.HOLO_RING, Modifier.fillMaxWidth(0.82f), dim = k)
                AodStyle.EDGE_PULSE, AodStyle.CONSTELLATION -> NexusClock(clockStyle, Modifier.fillMaxWidth(0.9f), dim = k)
                AodStyle.GLYPH_MATRIX -> GlyphMatrix(battery, k)
                AodStyle.DATA_MINIMAL -> DataMinimal(battery, k)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(28.dp)) {
                    val r = size.minDimension / 2 - 2f
                    drawCircle(p.accent, r, alpha = 0.15f * k, style = Stroke(3f))
                    drawArc(p.accent, -90f, 360f * battery.level / 100f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), alpha = k, style = Stroke(3f, cap = StrokeCap.Round))
                }
                Spacer(Modifier.width(10.dp))
                Text("${battery.level}%${if (battery.charging) " · CHARGING" else ""}", style = nx.label(10f, p.text.copy(alpha = 0.7f * k)))
            }
            if (showNotifications && apps.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { a ->
                        Box(
                            Modifier.size(26.dp).background(p.accent.copy(alpha = 0.10f * k)),
                            contentAlignment = Alignment.Center,
                        ) { Text(a.take(1).uppercase(), style = nx.label(10f, p.accent.copy(alpha = k))) }
                    }
                }
            }
        }
    }
}

/** Nothing-style glyph matrix: a 25×25 dot grid showing the battery as a filling ring + minute dot. */
@Composable
private fun GlyphMatrix(battery: BatteryState, k: Float) {
    val nx = LocalNexus.current
    val p = nx.palette
    val now by produceWhileVisible(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(30_000) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(230.dp)) {
            val n = 25
            val step = size.width / n
            val c = Offset(size.width / 2, size.height / 2)
            val ringR = size.width * 0.40f
            val levelAng = battery.level / 100f * 2 * PI
            val minAng = now.minute / 60.0 * 2 * PI - PI / 2
            val mp = Offset(c.x + cos(minAng).toFloat() * ringR * 0.62f, c.y + sin(minAng).toFloat() * ringR * 0.62f)
            for (y in 0 until n) for (x in 0 until n) {
                val pt = Offset((x + 0.5f) * step, (y + 0.5f) * step)
                val dx = pt.x - c.x; val dy = pt.y - c.y
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d > size.width / 2) continue
                var ang = kotlin.math.atan2(dy, dx) + PI / 2
                if (ang < 0) ang += 2 * PI
                val onRing = kotlin.math.abs(d - ringR) < step * 0.6f
                val lit = (onRing && ang <= levelAng) || (pt - mp).getDistance() < step * 1.1f
                drawCircle(if (lit) p.text else p.text.copy(alpha = 0.06f), step * 0.32f, pt, alpha = if (lit) k else 1f)
            }
        }
        Spacer(Modifier.height(12.dp))
        DotMatrixText("%02d:%02d".format(now.hour, now.minute), pitch = 4.dp, color = p.text.copy(alpha = k), unlitAlpha = 0f)
    }
}

@Composable
private fun DataMinimal(battery: BatteryState, k: Float) {
    val nx = LocalNexus.current
    val p = nx.palette
    val now by produceWhileVisible(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(30_000) } }
    Column(Modifier.fillMaxWidth()) {
        Text("%02d".format(now.hour), style = nx.display(64f, p.text.copy(alpha = k), weight = 100))
        Text("%02d".format(now.minute), style = nx.display(64f, p.accent.copy(alpha = k), weight = 100))
        Spacer(Modifier.height(10.dp))
        Text("PWR ${battery.level}  ·  TMP ${"%.0f".format(battery.temperatureC)}C", style = nx.label(9f, p.text.copy(alpha = 0.55f * k)))
    }
}
