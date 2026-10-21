package com.nexus.launcher.ui.fx

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.NetState
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Heads-up display drawn over the home screen: corner reticles, edge rulers with a moving
 * scan marker, a compass arc driven by the real heading, a sweeping scanline and a live
 * telemetry ticker. Draw-only (no pointer input), so every touch passes straight through.
 */
@Composable
fun HudChrome(
    battery: BatteryState,
    net: NetState,
    clock: androidx.compose.runtime.State<Float>,
    topInsetPx: Float,
    bottomInsetPx: Float,
    ticker: Boolean,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val heading = LocalHeading.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val small = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = p.accent.copy(alpha = 0.75f), letterSpacing = 1.5.sp)

    var mem by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        while (true) {
            am.getMemoryInfo(info)
            mem = (100 - info.availMem * 100 / info.totalMem.coerceAtLeast(1)).toInt()
            delay(5000)
        }
    }
    val upMin = (SystemClock.elapsedRealtime() / 60_000).toInt()
    val tickerText = remember(battery.level, battery.charging, net.type, mem, upMin) {
        listOf(
            "SYS.NOMINAL", "CORE ${battery.level}%${if (battery.charging) "+" else ""}",
            "THERM ${"%.1f".format(battery.temperatureC)}C", "UPLINK ${net.type}", "MEM ${mem}%",
            "UPTIME ${upMin / 60}H${"%02d".format(upMin % 60)}M", "${Build.MANUFACTURER.uppercase()} ${Build.MODEL.uppercase()}",
            "API ${Build.VERSION.SDK_INT}", "NEXUS v2 HELIX", "LINK SECURE",
        ).joinToString("   ◆   ", postfix = "   ◆   ")
    }
    val tickerLayout = remember(tickerText, small) { measurer.measure(tickerText, small) }

    Canvas(modifier.fillMaxSize()) {
        val t = clock.value
        val w = size.width
        val h = size.height
        val m = 10.dp.toPx()
        val top = topInsetPx + m
        val bottom = h - bottomInsetPx - m
        val len = 22.dp.toPx()
        val sw = 1.4.dp.toPx()
        val c = p.accent

        // corner reticles
        corners(m, top, w - m, bottom, len, c, 0.55f, sw)
        drawCircle(p.accent2, 2.dp.toPx(), Offset(m + 6.dp.toPx(), top + 6.dp.toPx()), alpha = 0.9f)

        // edge rulers
        val tick = 12.dp.toPx()
        var y = top + len + 8.dp.toPx()
        var i = 0
        while (y < bottom - len - 8.dp.toPx()) {
            val l = if (i % 5 == 0) 7.dp.toPx() else 3.dp.toPx()
            drawLine(c, Offset(m, y), Offset(m + l, y), 1f, alpha = 0.35f)
            drawLine(c, Offset(w - m, y), Offset(w - m - l, y), 1f, alpha = 0.35f)
            y += tick; i++
        }
        val span = (bottom - top - 2 * len)
        val markY = top + len + ((t * 0.08f) % 1f) * span
        drawLine(p.accent2, Offset(m, markY), Offset(m + 10.dp.toPx(), markY), 2f, alpha = 0.9f)
        drawLine(p.accent2, Offset(w - m, h - markY), Offset(w - m - 10.dp.toPx(), h - markY), 2f, alpha = 0.9f)

        // compass arc (real heading from the rotation sensor)
        val hd = heading.value
        val arcW = w * 0.5f
        val arcTop = top + 2.dp.toPx()
        drawArc(c, 200f, 140f, false, Offset(w / 2 - arcW / 2, arcTop), Size(arcW, arcW * 0.5f), alpha = 0.22f, style = Stroke(1f))
        for (deg in -60..60 step 10) {
            val x = w / 2 + deg / 60f * arcW * 0.42f
            val real = ((hd + deg) % 360 + 360) % 360
            val major = (real.roundToInt() / 10) % 9 == 0
            drawLine(c, Offset(x, arcTop + 4.dp.toPx()), Offset(x, arcTop + (if (major) 11 else 7).dp.toPx()), 1f, alpha = if (deg == 0) 1f else 0.4f)
        }
        val hdText = "HDG %03d°".format(hd.roundToInt() % 360)
        val hdLayout = measurer.measure(hdText, small)
        drawText(hdLayout, topLeft = Offset(w / 2 - hdLayout.size.width / 2f, arcTop + 13.dp.toPx()))

        // scanline
        val sy = ((t * 0.05f) % 1f) * h
        drawLine(c, Offset(0f, sy), Offset(w, sy), 1f, alpha = 0.06f)

        // telemetry ticker
        if (ticker) {
            val tw = tickerLayout.size.width.toFloat()
            val x0 = -((t * 40f) % tw)
            val ty = bottom - tickerLayout.size.height - 2.dp.toPx()
            var x = x0
            while (x < w) {
                drawText(tickerLayout, topLeft = Offset(x + m * 2, ty))
                x += tw
            }
            drawLine(c, Offset(m * 2, ty - 3.dp.toPx()), Offset(w - m * 2, ty - 3.dp.toPx()), 1f, alpha = 0.18f)
        }
        // centre crosshair hint
        drawCircle(c, 3.dp.toPx(), Offset(w / 2, h / 2), alpha = 0.08f, style = Stroke(1f, cap = StrokeCap.Round))
    }
}
