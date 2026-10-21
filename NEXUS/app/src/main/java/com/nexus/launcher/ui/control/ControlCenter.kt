package com.nexus.launcher.ui.control

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.ControlModules
import com.nexus.launcher.core.NetState
import com.nexus.launcher.core.NetType
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.system.TorchController
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.fx.DotMatrixText
import com.nexus.launcher.ui.home.BatteryGlyph
import com.nexus.launcher.ui.home.rememberNow
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Spaceship-console control center. Real toggles where Android allows (flashlight, DND,
 * brightness, auto-rotate, media volume); official system panels where it does not
 * (Wi-Fi, Bluetooth, mobile data, NFC, location). The system Quick Settings stay available.
 */
@Composable
fun ControlCenter(
    visible: Boolean,
    battery: BatteryState,
    net: NetState,
    torch: TorchController,
    onClose: () -> Unit,
    onSystemQs: () -> Unit,
) {
    val nx = LocalNexus.current
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(nx.dur(200))) + scaleIn(tween(nx.dur(260)), initialScale = 0.94f),
        exit = fadeOut(tween(nx.dur(160))) + scaleOut(tween(nx.dur(160)), targetScale = 0.96f),
    ) {
        ControlContent(battery, net, torch, onClose, onSystemQs)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlContent(
    battery: BatteryState,
    net: NetState,
    torch: TorchController,
    onClose: () -> Unit,
    onSystemQs: () -> Unit,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val haptic = rememberHaptics()
    val torchOn by torch.on.collectAsStateWithLifecycle()

    // Poll cheap system state only while visible.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    val wifi = remember(tick) { SystemControls.wifiEnabled(ctx) }
    val bt = remember(tick) { SystemControls.bluetoothEnabled(ctx) }
    val dnd = remember(tick) { SystemControls.dndOn(ctx) }
    val rotate = remember(tick) { SystemControls.autoRotate(ctx) }
    val nfc = remember(tick) { SystemControls.nfcState(ctx) }
    val loc = remember(tick) { SystemControls.locationEnabled(ctx) }
    val canWrite = remember(tick) { SystemControls.canWriteSettings(ctx) }
    var brightness by remember { mutableFloatStateOf(SystemControls.brightness(ctx)) }
    var autoBright by remember { mutableStateOf(SystemControls.autoBrightness(ctx)) }
    var volume by remember { mutableFloatStateOf(SystemControls.mediaVolume(ctx)) }
    LaunchedEffect(tick) { volume = SystemControls.mediaVolume(ctx) }

    val modules = nx.config.controlModules.filter { it in ControlModules.all }
    val now by rememberNow(false)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.bg.copy(alpha = 0.96f), p.bg.copy(alpha = 0.88f))))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClose() }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onVerticalDrag = { c, d -> total += d; c.consume() }, onDragEnd = { if (total < -80f) onClose() })
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { },
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nx.tag(if (nx.tech) "Control deck" else "Quick controls"), style = nx.label(10f, p.accent))
                        Spacer(Modifier.height(6.dp))
                        DotMatrixText(now.format(DateTimeFormatter.ofPattern("HH:mm")), pitch = 5.dp, color = p.text)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        BatteryGlyph(battery, Modifier.size(34.dp, 14.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("${battery.level}%${if (battery.charging) " · ${battery.source.ifEmpty { "CHG" }}" else ""}", style = nx.label(10f, p.text))
                    }
                }
            }
            items(modules, key = { it }, span = { m -> if (m in ControlModules.fullWidth) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { m ->
                when (m) {
                    ControlModules.DIALS -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassPanel(Modifier.weight(1f), padding = 10.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                RadialDial(
                                    value = brightness, enabled = canWrite && !autoBright, label = "LUMEN",
                                    onChange = { v -> brightness = v; SystemControls.setBrightness(ctx, v) },
                                    onDisabledTap = {
                                        if (!canWrite) SystemControls.start(ctx, SystemControls.writeSettingsIntent(ctx))
                                        else { autoBright = false; SystemControls.setAutoBrightness(ctx, false) }
                                    },
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                    if (canWrite) { autoBright = !autoBright; SystemControls.setAutoBrightness(ctx, autoBright); haptic(Haptic.TICK) }
                                    else SystemControls.start(ctx, SystemControls.writeSettingsIntent(ctx))
                                }) {
                                    Icon(if (autoBright) Icons.Rounded.BrightnessAuto else Icons.Rounded.BrightnessMedium, null, tint = if (autoBright) p.accent else p.textDim, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (!canWrite) "GRANT ACCESS" else if (autoBright) "ADAPTIVE" else "MANUAL", style = nx.label(9f, p.textDim))
                                }
                            }
                        }
                        GlassPanel(Modifier.weight(1f), padding = 10.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                RadialDial(
                                    value = volume, enabled = true, label = "AUDIO",
                                    onChange = { v -> volume = v; SystemControls.setMediaVolume(ctx, v) },
                                    onDisabledTap = {},
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { SystemControls.openVolumePanel(ctx) }) {
                                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = p.textDim, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("MEDIA · ALL VOLUMES", style = nx.label(9f, p.textDim))
                                }
                            }
                        }
                    }
                    ControlModules.BATTERY -> GlassPanel(Modifier.fillMaxWidth().clickable { SystemControls.openBattery(ctx) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("POWER CELL", style = nx.label(9.5f, p.textFaint))
                                Text("${battery.level}%", style = nx.display(28f, p.text, weight = 300))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("TEMP ${"%.1f".format(battery.temperatureC)}°C", style = nx.label(9.5f, p.textDim))
                                Text("VOLT ${"%.2f".format(battery.voltageMv / 1000f)} V", style = nx.label(9.5f, p.textDim))
                                Text("HEALTH ${battery.health}", style = nx.label(9.5f, p.textDim))
                                Text(if (battery.charging) "SOURCE ${battery.source.ifEmpty { "—" }}" else "DISCHARGING", style = nx.label(9.5f, p.accent))
                            }
                        }
                    }
                    ControlModules.WIFI -> Tile(Icons.Rounded.Wifi, "Wi-Fi", if (net.type == NetType.WIFI) "CONNECTED" else if (wifi) "ON" else "OFF", wifi, panel = true,
                        onClick = { SystemControls.openWifi(ctx) }, onLong = { SystemControls.start(ctx, android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) })
                    ControlModules.BLUETOOTH -> Tile(Icons.Rounded.Bluetooth, "Bluetooth", if (bt) "ON" else "OFF", bt, panel = true,
                        onClick = { SystemControls.openBluetooth(ctx) }, onLong = { SystemControls.openBluetooth(ctx) })
                    ControlModules.MOBILE -> Tile(Icons.Rounded.SignalCellularAlt, "Mobile data", if (net.type == NetType.CELLULAR) "ACTIVE" else "STANDBY", net.type == NetType.CELLULAR, panel = true,
                        onClick = { SystemControls.openInternet(ctx) }, onLong = { SystemControls.openInternet(ctx) })
                    ControlModules.FLASHLIGHT -> Tile(Icons.Rounded.FlashlightOn, "Flashlight", if (!torch.available) "N/A" else if (torchOn) "ON" else "OFF", torchOn,
                        onClick = { haptic(Haptic.TICK); torch.toggle() }, onLong = {})
                    ControlModules.DND -> Tile(Icons.Rounded.DoNotDisturbOn, "Do not disturb", if (!SystemControls.dndAccessGranted(ctx)) "GRANT" else if (dnd) "ON" else "OFF", dnd,
                        onClick = { haptic(Haptic.TICK); SystemControls.toggleDnd(ctx); tick++ },
                        onLong = { SystemControls.start(ctx, android.content.Intent(android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)) })
                    ControlModules.ROTATION -> Tile(Icons.Rounded.ScreenRotation, "Auto-rotate", if (!canWrite) "GRANT" else if (rotate) "ON" else "OFF", rotate,
                        onClick = {
                            if (canWrite) { haptic(Haptic.TICK); SystemControls.setAutoRotate(ctx, !rotate); tick++ }
                            else SystemControls.start(ctx, SystemControls.writeSettingsIntent(ctx))
                        }, onLong = { SystemControls.openDisplay(ctx) })
                    ControlModules.LOCATION -> Tile(Icons.Rounded.LocationOn, "Location", if (loc) "ON" else "OFF", loc, panel = true,
                        onClick = { SystemControls.openLocation(ctx) }, onLong = { SystemControls.openLocation(ctx) })
                    ControlModules.NFC -> Tile(Icons.Rounded.Nfc, "NFC", when (nfc) { null -> "N/A"; true -> "ON"; false -> "OFF" }, nfc == true, panel = true,
                        onClick = { SystemControls.openNfc(ctx) }, onLong = { SystemControls.openNfc(ctx) })
                    ControlModules.SHADE -> Tile(Icons.Rounded.Notifications, "System QS", "OPEN", false, panel = true,
                        onClick = { onSystemQs() }, onLong = { onSystemQs() })
                    ControlModules.SETTINGS -> Tile(Icons.Rounded.Settings, "Settings", "DEVICE", false, panel = true,
                        onClick = { SystemControls.openSettings(ctx) }, onLong = { SystemControls.openSettings(ctx) })
                    else -> Unit
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "foot") {
                Text(
                    "◇ = opens the official Android panel · swipe up or tap outside to close",
                    style = nx.label(8.5f, p.textFaint), modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    icon: ImageVector,
    title: String,
    state: String,
    active: Boolean,
    panel: Boolean = false,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    Column(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(NxShapes.tile)
            .background(if (active) Brush.linearGradient(listOf(p.accent.copy(alpha = 0.30f), p.accent.copy(alpha = 0.10f))) else Brush.linearGradient(listOf(p.panelTop, p.panel)))
            .border(1.dp, if (active) p.accent.copy(alpha = 0.8f) else p.border, NxShapes.tile)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .semantics { contentDescription = title; stateDescription = state }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (active) p.accent else p.textDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.weight(1f))
            if (panel) Text("◇", style = nx.label(10f, p.textFaint))
        }
        Column {
            Text(title, style = nx.body(13.5f), maxLines = 1)
            Text(state, style = nx.label(9f, if (active) p.accent else p.textFaint))
        }
    }
}

/** 270° radial control. Drag around the ring or tap a point on it. */
@Composable
fun RadialDial(
    value: Float,
    enabled: Boolean,
    label: String,
    onChange: (Float) -> Unit,
    onDisabledTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val change by rememberUpdatedState(onChange)
    val disabledTap by rememberUpdatedState(onDisabledTap)
    val isEnabled by rememberUpdatedState(enabled)
    var lastStep by remember { mutableIntStateOf((value * 20).toInt()) }

    fun valueAt(o: Offset, w: Float, h: Float): Float {
        val deg = Math.toDegrees(atan2((o.y - h / 2).toDouble(), (o.x - w / 2).toDouble())).toFloat()
        val rel = ((deg - 135f) % 360f + 360f) % 360f
        return if (rel > 270f) (if (rel > 315f) 0f else 1f) else rel / 270f
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "$label ${(value * 100).toInt()} percent" }
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        if (!isEnabled) disabledTap() else { change(valueAt(o, size.width.toFloat(), size.height.toFloat())); haptic(Haptic.TICK) }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { c, _ ->
                        if (!isEnabled) return@detectDragGestures
                        c.consume()
                        val v = valueAt(c.position, size.width.toFloat(), size.height.toFloat())
                        val step = (v * 20).toInt()
                        if (step != lastStep) { lastStep = step; haptic(Haptic.TICK) }
                        change(v)
                    }
                }
        ) {
            val stroke = 9.dp.toPx()
            val r = min(size.width, size.height) / 2 - stroke * 1.6f
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2, r * 2)
            drawArc(p.accent, 135f, 270f, false, tl, sz, alpha = 0.12f, style = Stroke(stroke, cap = StrokeCap.Round))
            for (i in 0..27) {
                val a = Math.toRadians((135.0 + i * 10.0)).toFloat()
                val inner = r + stroke * 0.9f
                val outer = r + stroke * (if (i % 3 == 0) 1.5f else 1.2f)
                drawLine(p.accent, Offset(center.x + cos(a) * inner, center.y + sin(a) * inner), Offset(center.x + cos(a) * outer, center.y + sin(a) * outer), 1.2f, alpha = 0.35f)
            }
            if (enabled) {
                drawArc(Brush.sweepGradient(listOf(p.accent2, p.accent, p.accent), center), 135f, 270f * value, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                val a = Math.toRadians(135.0 + 270.0 * value).toFloat()
                val k = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                drawCircle(p.accent, stroke * 1.3f, k, alpha = 0.2f * (0.5f + nx.glow))
                drawCircle(Color.White, stroke * 0.55f, k)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (enabled) "${(value * 100).toInt()}" else "—", style = nx.display(26f, p.text, weight = 300))
            Text(label, style = nx.label(8.5f, p.textDim))
        }
    }
}

@Suppress("unused")
private val TAU = (2 * PI).toFloat()
