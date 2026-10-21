package com.nexus.launcher.ui.widgets

import com.nexus.launcher.ui.components.produceWhileVisible
import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.ClockStyle
import com.nexus.launcher.core.NetType
import com.nexus.launcher.core.NexusWidgetKind
import com.nexus.launcher.core.WeatherState
import com.nexus.launcher.nexus
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.fx.DotMatrixText
import com.nexus.launcher.ui.fx.NexusClock
import com.nexus.launcher.ui.fx.rememberAmbientClock
import com.nexus.launcher.ui.home.SignalBars
import com.nexus.launcher.ui.home.signalLevel
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

/** Names for the style chips in the widget picker / Studio. */
fun nexusStyleNames(kind: NexusWidgetKind): List<String> = when (kind) {
    NexusWidgetKind.CLOCK -> ClockStyle.entries.map { it.name.replace('_', ' ') }
    NexusWidgetKind.BATTERY -> listOf("RING", "SEGMENTS", "CELL", "MATRIX", "GAUGE", "DATA")
    NexusWidgetKind.WEATHER -> listOf("BIG TEMP", "COMPACT", "HORIZON", "MINIMAL", "DATA GRID", "THERMO")
    NexusWidgetKind.MEDIA -> listOf("BAR", "ART", "WAVE", "COMPACT", "ORBIT", "MINIMAL")
    NexusWidgetKind.SYSTEM -> listOf("DUAL RING", "BARS", "MATRIX", "HEX", "TERMINAL", "PULSE")
    NexusWidgetKind.SIGNALS -> listOf("COUNT", "APPS", "MATRIX", "RADAR", "LIST", "MINIMAL")
    NexusWidgetKind.SEARCH -> listOf("PILL", "BAR", "TERMINAL", "LINE")
    NexusWidgetKind.TOGGLES -> listOf("CIRCLES", "TILES", "PILLS", "TEXT")
    NexusWidgetKind.WORLD_CLOCK -> listOf("COLUMNS", "LIST", "FEATURED", "MINIMAL")
    NexusWidgetKind.NOTE -> listOf("STICKY", "PLAIN", "TERMINAL", "SERIF")
    NexusWidgetKind.CALENDAR -> listOf("AGENDA", "TIMELINE", "NEXT UP", "MINIMAL")
    NexusWidgetKind.PHOTO -> listOf("ROUNDED", "CIRCLE", "HEXAGON", "POLAROID")
    NexusWidgetKind.FAVORITES -> listOf("ICONS", "LABELLED", "HEX", "PILL")
    NexusWidgetKind.DATE -> listOf("BIG DAY", "STACKED", "LINE", "PAGE")
}

private data class SysStats(val memPct: Int, val storagePct: Int, val memUsedGb: Float, val memTotalGb: Float)

@Composable
private fun rememberSysStats(): SysStats {
    val ctx = LocalContext.current
    val s by produceWhileVisible(SysStats(0, 0, 0f, 0f)) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        while (true) {
            am.getMemoryInfo(mi)
            val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
            val stPct = st?.let { (100 - it.availableBytes * 100 / it.totalBytes.coerceAtLeast(1)).toInt() } ?: 0
            value = SysStats(
                (100 - mi.availMem * 100 / mi.totalMem.coerceAtLeast(1)).toInt(), stPct,
                (mi.totalMem - mi.availMem) / 1_073_741_824f, mi.totalMem / 1_073_741_824f,
            )
            delay(10_000)
        }
    }
    return s
}

/** A built-in NEXUS widget. [style] indexes [nexusStyleNames]; [frame] null = the global default. */
@Composable
fun NexusModule(
    kind: NexusWidgetKind,
    style: Int,
    modifier: Modifier = Modifier,
    frame: com.nexus.launcher.core.WidgetFrame? = null,
    data: String = "",
) {
    val app = LocalContext.current.nexus
    val battery by app.system.battery.collectAsState()
    WidgetFrameBox(frame ?: com.nexus.launcher.ui.theme.LocalNexus.current.config.widgetFrame, modifier.fillMaxSize()) {
        when (kind) {
            NexusWidgetKind.CLOCK -> NexusClock(ClockStyle.entries[style.coerceIn(0, ClockStyle.entries.size - 1)], Modifier.fillMaxSize(), showDate = true)
            NexusWidgetKind.BATTERY -> BatteryModule(battery, style)
            NexusWidgetKind.WEATHER -> {
                val w by app.weather.state.collectAsState()
                WeatherModule(w, style)
            }
            NexusWidgetKind.MEDIA -> MediaModule(style)
            NexusWidgetKind.SYSTEM -> SystemModule(style)
            NexusWidgetKind.SIGNALS -> SignalsModule(style)
            NexusWidgetKind.SEARCH -> SearchModule(style)
            NexusWidgetKind.TOGGLES -> TogglesModule(style)
            NexusWidgetKind.WORLD_CLOCK -> WorldClockModule(style, data)
            NexusWidgetKind.NOTE -> NoteModule(style, data)
            NexusWidgetKind.CALENDAR -> CalendarModule(style)
            NexusWidgetKind.PHOTO -> PhotoModule(style, data)
            NexusWidgetKind.FAVORITES -> FavoritesModule(style)
            NexusWidgetKind.DATE -> DateModule(style)
        }
    }
}

@Composable
private fun BoxScope.BatteryModule(b: BatteryState, style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val lvl = b.level / 100f
    val col = if (b.level <= 15 && !b.charging) p.accent2 else p.accent
    when (style) {
        0 -> { // ring
            Canvas(Modifier.fillMaxSize().align(Alignment.Center)) {
                val r = size.minDimension / 2 - 8f
                val tl = Offset(center.x - r, center.y - r)
                drawCircle(col, r, alpha = 0.15f, style = Stroke(10f))
                drawArc(col, -90f, 360f * lvl, false, tl, Size(r * 2, r * 2), style = Stroke(10f, cap = StrokeCap.Round))
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${b.level}%", style = nx.display(24f, p.text, weight = 300))
                Text(if (b.charging) "CHARGING" else "POWER", style = nx.label(8f, p.textDim))
            }
        }
        1 -> Column(Modifier.align(Alignment.Center)) { // segments
            Text("POWER", style = nx.label(8.5f, p.textDim))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until 10) Box(Modifier.width(9.dp).height(34.dp).background(if (i < (b.level + 9) / 10) col else col.copy(alpha = 0.12f)))
            }
            Spacer(Modifier.height(6.dp))
            Text("${b.level}%", style = nx.display(20f, p.text, weight = 300))
        }
        2 -> Canvas(Modifier.fillMaxSize().padding(10.dp)) { // cell
            val w = size.width * 0.5f; val h = size.height * 0.9f
            val l = center.x - w / 2; val t = center.y - h / 2 + 4f
            drawRoundRect(col, Offset(l, t), Size(w, h), androidx.compose.ui.geometry.CornerRadius(10f), style = Stroke(3f))
            drawRect(col, Offset(center.x - w * 0.18f, t - 8f), Size(w * 0.36f, 6f))
            val fh = (h - 10f) * lvl
            drawRoundRect(Brush.verticalGradient(listOf(p.fx, col.copy(alpha = 0.4f))), Offset(l + 5f, t + h - 5f - fh), Size(w - 10f, fh), androidx.compose.ui.geometry.CornerRadius(6f))
        }
        3 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { // matrix
            DotMatrixText("${b.level}", pitch = 6.dp, color = col)
            Spacer(Modifier.height(6.dp))
            Text("% POWER", style = nx.label(8f, p.textDim))
        }
        4 -> { // gauge
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2 - 6f
                val tl = Offset(center.x - r, center.y - r + size.height * 0.1f)
                drawArc(col, 180f, 180f, false, tl, Size(r * 2, r * 2), alpha = 0.15f, style = Stroke(12f))
                drawArc(col, 180f, 180f * lvl, false, tl, Size(r * 2, r * 2), style = Stroke(12f, cap = StrokeCap.Round))
            }
            Text("${b.level}", style = nx.display(26f, p.text, weight = 300), modifier = Modifier.align(Alignment.BottomCenter))
        }
        else -> Column(Modifier.align(Alignment.CenterStart)) { // data
            Text("PWR  ${b.level}%", style = nx.label(10f, p.text))
            Text("TMP  ${"%.1f".format(b.temperatureC)}C", style = nx.label(9f, p.textDim))
            Text("VLT  ${"%.2f".format(b.voltageMv / 1000f)}V", style = nx.label(9f, p.textDim))
            Text("SRC  ${b.source.ifEmpty { "CELL" }}", style = nx.label(9f, col))
        }
    }
}

@Composable
private fun BoxScope.WeatherModule(w: WeatherState?, style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val c = nx.config.weatherCelsius
    fun f(v: Float) = if (v.isNaN()) "--" else if (c) "${v.roundToInt()}°" else "${(v * 9 / 5 + 32).roundToInt()}°"
    if (w == null) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ATMOSPHERE", style = nx.label(9f, p.textDim))
            Text(if (nx.config.weatherEnabled) "SCANNING…" else "ENABLE IN STUDIO", style = nx.label(8.5f, p.accent))
        }
        return
    }
    when (style) {
        0 -> Column(Modifier.align(Alignment.CenterStart)) {
            Text(f(w.tempC), style = nx.display(40f, p.text, weight = 200)); Text(w.condition, style = nx.label(9f, p.accent))
        }
        1 -> Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(f(w.tempC), style = nx.display(24f, p.text, weight = 300)); Spacer(Modifier.width(8.dp))
            Column { Text(w.condition, style = nx.label(8.5f, p.accent)); Text("${f(w.highC)} / ${f(w.lowC)}", style = nx.label(8.5f, p.textDim)) }
        }
        2 -> {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * 0.62f
                drawLine(p.accent, Offset(0f, y), Offset(size.width, y), 1.5f, alpha = 0.6f)
                drawArc(if (w.isDay) p.accent2 else p.fx, 180f, 180f, true, Offset(size.width / 2 - 26f, y - 26f), Size(52f, 52f), alpha = 0.8f)
            }
            Text(f(w.tempC), style = nx.display(22f, p.text, weight = 300), modifier = Modifier.align(Alignment.BottomCenter))
        }
        3 -> Text(f(w.tempC), style = nx.display(46f, p.text, weight = 100), modifier = Modifier.align(Alignment.Center))
        4 -> Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("TMP ${f(w.tempC)}", style = nx.label(10f, p.text)); Text("HUM ${w.humidity}%", style = nx.label(9f, p.textDim))
            Text("WND ${w.windKmh.roundToInt()}KM/H", style = nx.label(9f, p.textDim)); Text(w.condition, style = nx.label(9f, p.accent))
        }
        else -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(18.dp).fillMaxHeight()) {
                val k = ((w.tempC + 10f) / 55f).coerceIn(0f, 1f)
                drawRoundRect(p.accent, Offset(size.width / 2 - 4f, 0f), Size(8f, size.height), androidx.compose.ui.geometry.CornerRadius(4f), alpha = 0.15f)
                drawRoundRect(Brush.verticalGradient(listOf(p.accent2, p.accent)), Offset(size.width / 2 - 4f, size.height * (1 - k)), Size(8f, size.height * k), androidx.compose.ui.geometry.CornerRadius(4f))
            }
            Spacer(Modifier.width(10.dp))
            Column { Text(f(w.tempC), style = nx.display(28f, p.text, weight = 300)); Text(w.condition, style = nx.label(8.5f, p.accent)) }
        }
    }
}

@Composable
private fun BoxScope.MediaModule(style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val m by NotificationHub.media.collectAsState()
    val media = m
    if (media == null) {
        Text("NO ACTIVE PLAYBACK", style = nx.label(9f, p.textDim), modifier = Modifier.align(Alignment.Center))
        return
    }
    val art = remember(media.art) { media.art?.asImageBitmap() }
    val clock = rememberAmbientClock(media.playing && (style == 2 || style == 4))
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if ((style == 1 || style == 0) && art != null) {
            Image(art, null, Modifier.fillMaxHeight().aspectRatio(1f).clip(NxShapes.chip), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
        }
        if (style == 4) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                drawCircle(p.accent, size.minDimension / 2 - 4f, alpha = 0.3f, style = Stroke(2f))
                val a = clock.value * 2f
                drawCircle(p.accent2, 5f, Offset(center.x + kotlin.math.cos(a) * (size.minDimension / 2 - 4f), center.y + sin(a) * (size.minDimension / 2 - 4f)))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            if (style != 5) Text(if (media.playing) "PLAYBACK · LIVE" else "PAUSED", style = nx.label(8f, p.accent))
            Text(media.title, style = nx.body(if (style == 5) 15f else 13f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (style != 3) Text(media.artist, style = nx.body(11f, color = p.textDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (style == 2) {
            Canvas(Modifier.width(60.dp).fillMaxHeight()) {
                for (i in 0 until 10) {
                    val h = (0.3f + 0.7f * (0.5f + 0.5f * sin(clock.value * 5f + i))) * size.height * 0.4f
                    drawLine(p.accent, Offset(i * 6f + 3f, center.y - h), Offset(i * 6f + 3f, center.y + h), 3f, cap = StrokeCap.Round)
                }
            }
        }
        Text(
            if (media.playing) "❚❚" else "▶",
            style = nx.label(14f, p.accent),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun BoxScope.SystemModule(style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val s = rememberSysStats()
    when (style) {
        0 -> Canvas(Modifier.fillMaxSize()) {
            val r1 = size.minDimension / 2 - 6f; val r2 = r1 - 14f
            drawCircle(p.accent, r1, alpha = 0.15f, style = Stroke(8f))
            drawArc(p.accent, -90f, 3.6f * s.memPct, false, Offset(center.x - r1, center.y - r1), Size(r1 * 2, r1 * 2), style = Stroke(8f, cap = StrokeCap.Round))
            drawCircle(p.accent2, r2, alpha = 0.15f, style = Stroke(8f))
            drawArc(p.accent2, -90f, 3.6f * s.storagePct, false, Offset(center.x - r2, center.y - r2), Size(r2 * 2, r2 * 2), style = Stroke(8f, cap = StrokeCap.Round))
        }
        1, 4 -> Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("MEM" to s.memPct, "STO" to s.storagePct).forEach { (l, v) ->
                if (style == 4) Text("$l [" + "#".repeat(v / 10) + ".".repeat(10 - v / 10) + "] $v%", style = nx.label(9f, p.text))
                else {
                    Text("$l $v%", style = nx.label(8.5f, p.textDim))
                    Box(Modifier.fillMaxWidth().height(6.dp).background(p.accent.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth(v / 100f).height(6.dp).background(if (l == "MEM") p.accent else p.accent2))
                    }
                }
            }
        }
        2 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            DotMatrixText("${s.memPct}", pitch = 5.dp, color = p.accent); Text("% MEMORY", style = nx.label(8f, p.textDim))
        }
        3 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1f".format(s.memUsedGb), style = nx.display(26f, p.text, weight = 300))
            Text("/ %.0f GB RAM".format(s.memTotalGb), style = nx.label(8.5f, p.accent))
        }
        else -> {
            val clock = rememberAmbientClock(true)
            Canvas(Modifier.fillMaxSize()) {
                val n = 30
                for (i in 0 until n) {
                    val v = s.memPct / 100f * (0.7f + 0.3f * sin(clock.value * 2f + i * 0.6f))
                    drawLine(p.accent, Offset(i * size.width / n, size.height), Offset(i * size.width / n, size.height * (1 - v)), size.width / n * 0.6f, alpha = 0.7f)
                }
            }
            Text("MEM ${s.memPct}%", style = nx.label(9f, p.text), modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

@Composable
private fun BoxScope.SignalsModule(style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val items by NotificationHub.items.collectAsState()
    val connected by NotificationHub.connected.collectAsState()
    val net by LocalContext.current.nexus.system.net.collectAsState()
    val list = items.filter { !it.ongoing }
    if (!connected) { Text("GRANT NOTIFICATION ACCESS", style = nx.label(8.5f, p.textDim), modifier = Modifier.align(Alignment.Center)); return }
    when (style) {
        0 -> Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.Bottom) {
            Text("${list.size}", style = nx.display(28f, p.text, weight = 300)); Spacer(Modifier.width(6.dp)); Text("SIGNALS", style = nx.label(8.5f, p.textDim))
        }
        1 -> Text(list.map { it.appLabel }.distinct().take(4).joinToString(" · ").uppercase().ifEmpty { "ALL CLEAR" }, style = nx.label(9f, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart))
        2 -> DotMatrixText("${list.size}", pitch = 4.dp, color = p.accent, modifier = Modifier.align(Alignment.Center))
        3 -> Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            SignalBars(signalLevel(net), net.type != NetType.NONE, Modifier.size(22.dp, 16.dp)); Spacer(Modifier.width(10.dp))
            Text("${net.type} · ${list.size}", style = nx.label(9f, p.text))
        }
        4 -> Text(list.firstOrNull()?.let { "${it.appLabel.uppercase()}: ${it.title}" } ?: "ALL CLEAR", style = nx.body(12f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart))
        else -> Text(if (list.isEmpty()) "◇ CLEAR" else "◆ ${list.size}", style = nx.label(12f, if (list.isEmpty()) p.textDim else p.accent2), modifier = Modifier.align(Alignment.Center))
    }
}
