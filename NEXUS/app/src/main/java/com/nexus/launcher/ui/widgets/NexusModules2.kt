package com.nexus.launcher.ui.widgets

import com.nexus.launcher.ui.components.produceWhileVisible
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.launcher.LocalLauncherActions
import com.nexus.launcher.core.IconShape
import com.nexus.launcher.core.Predictor
import com.nexus.launcher.core.WidgetFrame
import com.nexus.launcher.nexus
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.ui.components.AppIconImage
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.theme.IconShapeOutline
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The six widget frames. All are cheap: no blur, at most a few stroked outlines. */
@Composable
fun WidgetFrameBox(frame: WidgetFrame, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    when (frame) {
        WidgetFrame.GLASS -> GlassPanel(modifier, padding = 10.dp, brackets = true, content = content)
        WidgetFrame.SOLID -> Box(modifier.clip(NxShapes.panel).background(p.panelTop.copy(alpha = 0.95f)).padding(12.dp), content = content)
        WidgetFrame.OUTLINE -> Box(modifier.clip(NxShapes.panel).border(1.5.dp, p.accent.copy(alpha = 0.7f), NxShapes.panel).padding(12.dp), content = content)
        WidgetFrame.NEON -> Box(
            modifier
                .drawBehind {
                    val o = NxShapes.panel.createOutline(size, layoutDirection, this)
                    for (i in 1..3) drawOutline(o, p.accent, alpha = 0.12f / i, style = Stroke((i * 6).dp.toPx()))
                    drawOutline(o, p.accent, style = Stroke(1.5.dp.toPx()))
                }
                .padding(12.dp),
            content = content,
        )
        WidgetFrame.PILL -> Box(modifier.clip(NxShapes.capsule).background(p.panelTop.copy(alpha = 0.9f)).padding(horizontal = 18.dp, vertical = 10.dp), content = content)
        WidgetFrame.NONE -> Box(modifier.padding(6.dp), content = content)
    }
}

@Composable
internal fun BoxScope.SearchModule(style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    when (style) {
        0, 1 -> Row(Modifier.align(Alignment.Center).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = p.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(nx.caps("Search"), style = nx.body(16f, color = p.textDim), modifier = Modifier.weight(1f))
            if (style == 1) Icon(Icons.Rounded.Mic, null, tint = p.textDim, modifier = Modifier.size(22.dp))
        }
        2 -> Text("> search_", style = nx.label(13f, p.accent).copy(fontFamily = FontFamily.Monospace), modifier = Modifier.align(Alignment.CenterStart))
        else -> Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
            Text(nx.caps("Search"), style = nx.body(15f, color = p.textDim))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(p.accent))
        }
    }
}

@Composable
internal fun BoxScope.TogglesModule(style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val actions = LocalLauncherActions.current
    val torch = actions?.torch
    val fallback = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val torchOn by (torch?.on ?: fallback).collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val dnd = remember(tick) { runCatching { SystemControls.dndOn(ctx) }.getOrDefault(false) }
    val rot = remember(tick) { SystemControls.autoRotate(ctx) }
    data class T(val icon: ImageVector, val label: String, val on: Boolean, val act: () -> Unit)
    val items = listOf(
        T(Icons.Rounded.FlashlightOn, "Torch", torchOn) { torch?.toggle() },
        T(Icons.Rounded.DoNotDisturbOn, "DND", dnd) { SystemControls.toggleDnd(ctx); tick++ },
        T(Icons.Rounded.ScreenRotation, "Rotate", rot) {
            if (SystemControls.canWriteSettings(ctx)) { SystemControls.setAutoRotate(ctx, !rot); tick++ } else SystemControls.start(ctx, SystemControls.writeSettingsIntent(ctx))
        },
        T(Icons.Rounded.Wifi, "Wi-Fi", SystemControls.wifiEnabled(ctx)) { SystemControls.openWifi(ctx) },
        T(Icons.Rounded.Bluetooth, "BT", SystemControls.bluetoothEnabled(ctx)) { SystemControls.openBluetooth(ctx) },
    )
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        items.forEach { t ->
            val shape = when (style) { 0 -> CircleShape; 1 -> NxShapes.chip; 2 -> NxShapes.capsule; else -> NxShapes.chip }
            Box(
                Modifier
                    .then(if (style == 2) Modifier.height(36.dp).width(52.dp) else Modifier.size(44.dp))
                    .clip(shape)
                    .background(if (style == 3) Color.Transparent else if (t.on) p.accent else p.accent.copy(alpha = 0.12f))
                    .clickable(role = Role.Switch, onClickLabel = t.label) { t.act() },
                contentAlignment = Alignment.Center,
            ) {
                if (style == 3) Text(t.label.uppercase(), style = nx.label(10f, if (t.on) p.accent else p.textDim))
                else Icon(t.icon, t.label, tint = if (t.on) p.onAccent else p.text, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun BoxScope.WorldClockModule(style: Int, data: String) {
    val nx = LocalNexus.current
    val p = nx.palette
    val zones = remember(data) {
        data.ifBlank { "Europe/London, America/New_York, Asia/Tokyo" }.split(',')
            .mapNotNull { runCatching { ZoneId.of(it.trim()) }.getOrNull() }.take(4)
    }
    val now by produceWhileVisible(Instant.now()) { while (true) { value = Instant.now(); delay(30_000) } }
    fun city(z: ZoneId) = z.id.substringAfterLast('/').replace('_', ' ')
    fun time(z: ZoneId) = now.atZone(z).format(DateTimeFormatter.ofPattern("HH:mm"))
    if (zones.isEmpty()) { Text("Long-press › Edit cities", style = nx.body(12f, color = p.textDim), modifier = Modifier.align(Alignment.Center)); return }
    when (style) {
        0 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            zones.forEach { z -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(time(z), style = nx.display(20f, p.text, weight = 300)); Text(nx.caps(city(z)), style = nx.label(8.5f, p.accent)) } }
        }
        1 -> Column(Modifier.align(Alignment.CenterStart)) {
            zones.forEach { z -> Row { Text(nx.caps(city(z)), style = nx.label(10f, p.textDim), modifier = Modifier.width(110.dp)); Text(time(z), style = nx.label(10f, p.text)) } }
        }
        2 -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(time(zones[0]), style = nx.display(30f, p.text, weight = 200)); Text(nx.caps(city(zones[0])), style = nx.label(9f, p.accent)) }
            Column(horizontalAlignment = Alignment.End) { zones.drop(1).forEach { z -> Text("${city(z)} ${time(z)}", style = nx.body(11f, color = p.textDim)) } }
        }
        else -> Text(zones.joinToString("   ") { "${city(it).take(3).uppercase()} ${time(it)}" }, style = nx.label(10.5f, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart))
    }
}

@Composable
internal fun BoxScope.NoteModule(style: Int, data: String) {
    val nx = LocalNexus.current
    val p = nx.palette
    val text = data.ifBlank { "Long-press › Edit note" }
    when (style) {
        0 -> Box(Modifier.fillMaxSize().clip(NxShapes.tile).background(p.accent2.copy(alpha = 0.22f)).padding(10.dp)) {
            Text(text, style = nx.body(14f), overflow = TextOverflow.Ellipsis)
        }
        1 -> Text(text, style = nx.body(14f), overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.TopStart))
        2 -> Text("$ cat note.txt\n$text", style = nx.label(11f, p.accent).copy(fontFamily = FontFamily.Monospace), overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.TopStart))
        else -> Text(text, style = nx.body(16f).copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic), overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.TopStart))
    }
}

private data class Event(val title: String, val begin: Long, val allDay: Boolean)

@Composable
internal fun BoxScope.CalendarModule(style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
        Text(nx.caps("Tap to show your calendar"), style = nx.body(13f, color = p.textDim), modifier = Modifier.align(Alignment.Center))
        return
    }
    val events by produceWhileVisible(emptyList<Event>()) {
        while (true) {
            value = withContext(Dispatchers.IO) { queryEvents(ctx) }
            delay(5 * 60_000L)
        }
    }
    fun t(e: Event) = if (e.allDay) "All day" else Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    if (events.isEmpty()) { Text(nx.caps("Nothing scheduled today"), style = nx.body(13f, color = p.textDim), modifier = Modifier.align(Alignment.Center)); return }
    when (style) {
        0 -> Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(nx.caps("Today"), style = nx.label(9f, p.accent))
            events.take(3).forEach { e -> Row { Text(t(e), style = nx.label(10f, p.textDim), modifier = Modifier.width(64.dp)); Text(e.title, style = nx.body(13f), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
        1 -> Column(Modifier.align(Alignment.TopStart)) {
            events.take(3).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(p.accent)); Spacer(Modifier.width(10.dp))
                    Column { Text(e.title, style = nx.body(13f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(t(e), style = nx.label(9f, p.textDim)) }
                }
            }
        }
        2 -> Column(Modifier.align(Alignment.CenterStart)) {
            Text(nx.caps("Next up · ${t(events[0])}"), style = nx.label(9.5f, p.accent))
            Text(events[0].title, style = nx.display(22f, p.text, weight = 300), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        else -> Text("${t(events[0])}  ${events[0].title}", style = nx.body(13f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart))
    }
}

private fun queryEvents(ctx: android.content.Context): List<Event> = runCatching {
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(now.toString()).appendPath((now + 24 * 3600_000L).toString()).build()
    ctx.contentResolver.query(
        uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
        null, null, "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { c ->
        val out = ArrayList<Event>()
        while (c.moveToNext() && out.size < 5) out += Event(c.getString(0).orEmpty().ifBlank { "(no title)" }, c.getLong(1), c.getInt(2) == 1)
        out
    } ?: emptyList()
}.getOrDefault(emptyList())

@Composable
internal fun BoxScope.PhotoModule(style: Int, data: String) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, data) {
        value = if (data.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val u = Uri.parse(data)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 720) s *= 2
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val b = img
    if (b == null) { Text(nx.caps("Tap to choose a photo"), style = nx.body(13f, color = p.textDim), modifier = Modifier.align(Alignment.Center)); return }
    val shape = when (style) { 1 -> CircleShape; 2 -> IconShapeOutline(IconShape.HEXAGON); else -> NxShapes.tile }
    if (style == 3) {
        Column(Modifier.align(Alignment.Center).background(Color.White).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 22.dp)) {
            Image(b, null, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Crop)
        }
    } else {
        Image(b, null, Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun BoxScope.FavoritesModule(style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val app = LocalContext.current.nexus
    val actions = LocalLauncherActions.current
    val apps by app.apps.apps.collectAsState()
    val usage by app.store.usage.collectAsState(emptyMap())
    val favs = remember(apps, usage) {
        val byId = apps.associateBy { it.key.id }
        val ranked = Predictor.rank(usage, java.time.LocalTime.now().hour, 6).mapNotNull { byId[it] }
        (ranked + apps).distinctBy { it.key }.take(5)
    }
    Row(
        Modifier.fillMaxSize().then(if (style == 3) Modifier.clip(NxShapes.capsule).background(p.accent.copy(alpha = 0.08f)) else Modifier),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        favs.forEach { a ->
            Column(
                Modifier.clickable(role = Role.Button, onClickLabel = a.label) { actions?.launchApp(a, Rect.Zero) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIconImage(a, if (style == 2) 40f else 38f)
                if (style == 1) Text(a.label, style = nx.body(9.5f, color = p.textDim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(52.dp))
            }
        }
    }
}

@Composable
internal fun BoxScope.DateModule(style: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val d by produceWhileVisible(LocalDate.now()) { while (true) { value = LocalDate.now(); delay(60_000) } }
    val loc = Locale.getDefault()
    when (style) {
        0 -> Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Text("${d.dayOfMonth}", style = nx.display(34f, p.accent, weight = 300)); Spacer(Modifier.width(10.dp))
            Column { Text(d.format(DateTimeFormatter.ofPattern("EEEE", loc)), style = nx.body(13f)); Text(d.format(DateTimeFormatter.ofPattern("MMMM yyyy", loc)), style = nx.body(11f, color = p.textDim)) }
        }
        1 -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(nx.caps(d.format(DateTimeFormatter.ofPattern("EEE", loc))), style = nx.label(10f, p.accent2)); Text("${d.dayOfMonth}", style = nx.display(26f, p.text, weight = 300))
        }
        2 -> Text(d.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", loc)), style = nx.body(14f), modifier = Modifier.align(Alignment.CenterStart))
        else -> Column(Modifier.align(Alignment.Center).clip(NxShapes.chip).background(p.panelTop), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().background(p.accent2).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                Text(d.format(DateTimeFormatter.ofPattern("MMM", loc)).uppercase(), style = nx.label(9f, Color.White, weight = 700))
            }
            Text("${d.dayOfMonth}", style = nx.display(24f, p.text, weight = 400), modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        }
    }
}

