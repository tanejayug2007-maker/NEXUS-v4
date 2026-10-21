package com.nexus.launcher.ui.home

import com.nexus.launcher.ui.components.produceWhileVisible
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.DisplayFont
import com.nexus.launcher.core.NetState
import com.nexus.launcher.core.NetType
import com.nexus.launcher.core.WeatherState
import com.nexus.launcher.nexus
import com.nexus.launcher.notifications.MediaState
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.longPressTrack
import com.nexus.launcher.ui.components.rememberBounds
import com.nexus.launcher.ui.components.trackBounds
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.fx.DotMatrixText
import com.nexus.launcher.ui.fx.dotPitchFor
import com.nexus.launcher.ui.fx.entrance
import com.nexus.launcher.ui.fx.rememberAmbientClock
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Ticks once per minute (or second), aligned to the wall clock. */
@Composable
fun rememberNow(seconds: Boolean): State<LocalDateTime> = produceWhileVisible(LocalDateTime.now(), seconds) {
    while (true) {
        value = LocalDateTime.now()
        val step = if (seconds) 1000L else 60_000L
        val ms = System.currentTimeMillis()
        delay(step - ms % step + 15)
    }
}

class CoreCallbacks(
    val openSearch: () -> Unit,
    val openNotifications: () -> Unit,
    val openControl: () -> Unit,
    val enableWeather: () -> Unit,
    val radialStart: (Offset) -> Unit,
    val radialMove: (Offset) -> Unit,
    val radialEnd: (Offset) -> Unit,
    val openMediaApp: (String) -> Unit,
)

/** The "CORE" page: a living system dashboard instead of an icon grid. */
@Composable
fun CorePage(battery: BatteryState, net: NetState, cb: CoreCallbacks, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val weather by ctx.nexus.weather.state.collectAsStateWithLifecycle()
    val media by NotificationHub.media.collectAsStateWithLifecycle()
    val notifs by NotificationHub.items.collectAsStateWithLifecycle()
    val listener by NotificationHub.connected.collectAsStateWithLifecycle()
    val haptic = rememberHaptics()

    Box(modifier.fillMaxSize()) {
        val bg = rememberBounds()
        Column(
            Modifier
                .fillMaxSize()
                .trackBounds(bg)
                .longPressTrack(
                    bg,
                    onStart = { o -> haptic(Haptic.PRESS); cb.radialStart(o) },
                    onMove = { o -> cb.radialMove(o) },
                    onEnd = { o -> cb.radialEnd(o) },
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            if (nx.config.clockStyle == com.nexus.launcher.core.ClockStyle.DOT_MATRIX) HeroClock(Modifier.entrance(0))
            else com.nexus.launcher.ui.fx.NexusClock(nx.config.clockStyle, Modifier.fillMaxWidth().entrance(0), seconds = nx.config.clockSeconds)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth().entrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BatteryModule(battery, Modifier.weight(1f).clickable { cb.openControl() })
                WeatherModule(weather, nx.config.weatherEnabled, Modifier.weight(1f).clickable { cb.enableWeather() })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().entrance(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NetworkModule(net, Modifier.weight(1f).clickable { cb.openControl() })
                SignalsModule(
                    count = notifs.count { !it.ongoing }, connected = listener,
                    modifier = Modifier.weight(1f).clickable { cb.openNotifications() },
                )
            }
            media?.let { m ->
                Spacer(Modifier.height(10.dp))
                MediaModule(m, Modifier.entrance(3).clickable { cb.openMediaApp(m.pkg) })
            }
            Spacer(Modifier.height(14.dp))
            SearchCapsule(Modifier.entrance(4).clickable { haptic(Haptic.TICK); cb.openSearch() })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun HeroClock(modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val now by rememberNow(nx.config.clockSeconds)
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "hh:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEE · dd MMM", Locale.getDefault())).uppercase()

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = nx.label(12f, p.textDim))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(5.dp).background(p.accent2))
            Spacer(Modifier.width(10.dp))
            Text("NEXUS // CORE", style = nx.label(10f, p.textFaint))
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (nx.spec.displayFont == DisplayFont.MATRIX) {
                val pitch = dotPitchFor(time, maxWidth, 13.dp)
                DotMatrixText(time, pitch = pitch, color = p.text)
            } else {
                Text(time, style = nx.display(88f, p.text, weight = nx.spec.fontWeight.coerceAtMost(300)))
            }
        }
        if (nx.config.clockSeconds) {
            Spacer(Modifier.height(8.dp))
            val sec = now.second
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val n = 60
                val gap = size.width / n
                for (i in 0 until n) {
                    drawLine(
                        if (i <= sec) p.accent else p.accent.copy(alpha = 0.12f),
                        Offset(i * gap + gap / 2, 0f), Offset(i * gap + gap / 2, size.height),
                        strokeWidth = if (i % 5 == 0) 2.5f else 1.2f,
                    )
                }
            }
        }
        if (!is24) Text(if (now.hour < 12) "AM" else "PM", style = nx.label(10f, p.textFaint))
    }
}

@Composable
private fun ModuleTitle(text: String) {
    val nx = LocalNexus.current
    Text(text, style = nx.label(9.5f, nx.palette.textFaint))
}

@Composable
fun BatteryModule(b: BatteryState, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val clock = rememberAmbientClock(b.charging && nx.quality.ambientEnabled)
    GlassPanel(modifier.height(118.dp)) {
        Column {
            ModuleTitle(if (b.charging) "POWER · CHARGING" else "POWER")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(60.dp)) {
                    val stroke = 5.dp.toPx()
                    val r = size.minDimension / 2 - stroke
                    val tl = Offset(center.x - r, center.y - r)
                    val sz = Size(r * 2, r * 2)
                    drawArc(p.accent, 135f, 270f, false, tl, sz, alpha = 0.14f, style = Stroke(stroke, cap = StrokeCap.Round))
                    val color = if (b.level <= 15 && !b.charging) p.accent2 else p.accent
                    drawArc(color, 135f, 270f * b.level / 100f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (b.charging) {
                        val t = clock.value
                        val a = 135f + ((t * 120f) % 270f)
                        drawArc(p.fx, a, 18f, false, tl, sz, alpha = 0.9f, style = Stroke(stroke * 0.6f, cap = StrokeCap.Round))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${b.level}%", style = nx.display(24f, p.text, weight = 300))
                    Text(
                        when { b.full -> "FULL"; b.charging -> b.source.ifEmpty { "PLUGGED" }; else -> "%.0f°C".format(b.temperatureC) },
                        style = nx.label(9f, p.textDim),
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherModule(w: WeatherState?, enabled: Boolean, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val celsius = nx.config.weatherCelsius
    fun fmt(c: Float) = if (c.isNaN()) "--" else if (celsius) "${c.roundToInt()}°" else "${(c * 9 / 5 + 32).roundToInt()}°"
    GlassPanel(modifier.height(118.dp)) {
        Column {
            ModuleTitle("ATMOSPHERE")
            Spacer(Modifier.height(8.dp))
            when {
                !enabled -> {
                    Text("Tap to enable", style = nx.body(13f, color = p.textDim))
                    Text("OPEN-METEO · NO ACCOUNT", style = nx.label(8.5f, p.textFaint))
                }
                w == null -> Text("SCANNING…", style = nx.label(11f, p.textDim))
                else -> {
                    Text(fmt(w.tempC), style = nx.display(30f, p.text, weight = 300))
                    Text(w.condition, style = nx.label(9f, p.accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("H ${fmt(w.highC)}  L ${fmt(w.lowC)}  ${w.humidity}%", style = nx.label(8.5f, p.textFaint))
                }
            }
        }
    }
}

@Composable
fun NetworkModule(n: NetState, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    GlassPanel(modifier.height(84.dp)) {
        Column {
            ModuleTitle("UPLINK")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBars(level = signalLevel(n), active = n.type != NetType.NONE, modifier = Modifier.size(22.dp, 16.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        when (n.type) {
                            NetType.WIFI -> "WI-FI"; NetType.CELLULAR -> "CELLULAR"; NetType.ETHERNET -> "ETHERNET"
                            NetType.VPN -> "VPN"; NetType.OTHER -> "LINKED"; NetType.NONE -> "OFFLINE"
                        },
                        style = nx.label(11f, if (n.type == NetType.NONE) p.accent2 else p.text),
                    )
                    Text(if (n.validated) "VERIFIED" else if (n.type == NetType.NONE) "NO SIGNAL" else "LIMITED", style = nx.label(8.5f, p.textFaint))
                }
            }
        }
    }
}

fun signalLevel(n: NetState): Int {
    val s = n.signal ?: return if (n.type == NetType.NONE) 0 else 3
    return if (n.type == NetType.WIFI) when {
        s >= -55 -> 4; s >= -66 -> 3; s >= -77 -> 2; s >= -88 -> 1; else -> 0
    } else 3
}

@Composable
fun SignalBars(level: Int, active: Boolean, modifier: Modifier = Modifier) {
    val p = LocalNexus.current.palette
    Canvas(modifier) {
        val bars = 4
        val w = size.width / (bars * 2 - 1)
        for (i in 0 until bars) {
            val h = size.height * (i + 1) / bars
            drawRect(
                if (active && i < level) p.accent else p.accent.copy(alpha = 0.18f),
                Offset(i * w * 2, size.height - h), Size(w, h),
            )
        }
    }
}

@Composable
fun SignalsModule(count: Int, connected: Boolean, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    GlassPanel(modifier.height(84.dp)) {
        Column {
            ModuleTitle("SIGNALS")
            Spacer(Modifier.height(6.dp))
            if (connected) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$count", style = nx.display(24f, if (count > 0) p.text else p.textDim, weight = 300))
                    Spacer(Modifier.width(6.dp))
                    Text(if (count == 1) "ALERT" else "ALERTS", style = nx.label(9f, p.textDim), modifier = Modifier.padding(bottom = 4.dp))
                }
            } else {
                Text("Grant access", style = nx.body(12.5f, color = p.textDim))
            }
        }
    }
}

@Composable
fun MediaModule(m: MediaState, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    GlassPanel(modifier.fillMaxWidth(), highlight = m.playing) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = remember(m.art) { m.art?.asImageBitmap() }
            Box(Modifier.size(52.dp).clip(NxShapes.chip).background(p.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (m.playing) "PLAYBACK · LIVE" else "PLAYBACK · PAUSED", style = nx.label(8.5f, p.accent))
                Text(m.title, style = nx.body(14f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.artist, style = nx.body(12f, color = p.textDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { haptic(Haptic.TICK); NotificationHub.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = p.text) }
            IconButton(onClick = { haptic(Haptic.TICK); if (m.playing) NotificationHub.pause() else NotificationHub.play() }) {
                Icon(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (m.playing) "Pause" else "Play", tint = p.accent)
            }
            IconButton(onClick = { haptic(Haptic.TICK); NotificationHub.next() }) { Icon(Icons.Rounded.SkipNext, "Next", tint = p.text) }
        }
    }
}

@Composable
fun SearchCapsule(modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(NxShapes.capsule)
            .background(Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.10f), p.panel)))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(nx.caps(if (nx.tech) "Search NEXUS" else "Search"), style = nx.label(11f, p.textDim))
    }
}
