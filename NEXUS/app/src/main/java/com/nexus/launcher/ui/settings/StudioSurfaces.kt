package com.nexus.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexus.launcher.call.CallPhase
import com.nexus.launcher.call.CallUi
import com.nexus.launcher.core.AnswerGesture
import com.nexus.launcher.core.AodStyle
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.core.CallStyle
import com.nexus.launcher.core.ClockStyle
import com.nexus.launcher.core.DrawerStyle
import com.nexus.launcher.core.FingerprintStyle
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.NexusWidgetKind
import com.nexus.launcher.core.PageTransition
import com.nexus.launcher.nexus
import com.nexus.launcher.ui.ambient.AmbientFace
import com.nexus.launcher.ui.call.CallActions
import com.nexus.launcher.ui.call.CallScreen
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.HoloChip
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.SectionHeader
import com.nexus.launcher.ui.components.SettingRow
import com.nexus.launcher.ui.components.SliderRow
import com.nexus.launcher.ui.components.StatusDot
import com.nexus.launcher.ui.components.SwitchRow
import com.nexus.launcher.ui.fx.ChargingVisual
import com.nexus.launcher.ui.fx.FingerprintFx
import com.nexus.launcher.ui.fx.NexusClock
import com.nexus.launcher.ui.theme.LocalNexus
import com.nexus.launcher.ui.theme.SurfaceAccent
import com.nexus.launcher.ui.widgets.NexusModule
import com.nexus.launcher.ui.widgets.nexusStyleNames
import kotlin.math.roundToInt

private typealias Update = ((NexusConfig) -> NexusConfig) -> Unit

/** A black, clipped frame that shows a live render of the current choice. */
@Composable
private fun PreviewFrame(height: Int, content: @Composable () -> Unit) {
    val p = LocalNexus.current.palette
    Box(
        Modifier.fillMaxWidth().height(height.dp).padding(vertical = 6.dp).clip(NxShapes.panel).background(Color.Black)
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
        Text("LIVE", style = LocalNexus.current.label(8f, p.accent), modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

@Composable
fun ClockSection(cfg: NexusConfig, update: Update) {
    Column {
        SectionHeader("Clock style")
        PreviewFrame(230) { NexusClock(cfg.clockStyle, Modifier.fillMaxWidth(0.8f), seconds = true) }
        EnumChips(ClockStyle.entries, cfg.clockStyle) { v -> update { it.copy(clockStyle = v) } }
        SwitchRow("Seconds", cfg.clockSeconds, { v -> update { it.copy(clockSeconds = v) } })
    }
}

@Composable
fun DrawerSection(cfg: NexusConfig, update: Update) {
    val nx = LocalNexus.current
    Column {
        SectionHeader("App drawer style")
        EnumChips(DrawerStyle.entries, cfg.drawerStyle) { v -> update { it.copy(drawerStyle = v) } }
        Text(
            when (cfg.drawerStyle) {
                DrawerStyle.GRID -> "Holo icon grid with A–Z rail."
                DrawerStyle.LIST -> "Large rows with package IDs — best for many apps."
                DrawerStyle.HONEYCOMB -> "Hex-packed icons, no labels."
                DrawerStyle.CAROUSEL -> "3D cover-flow: swipe sideways, tap the centred app."
                DrawerStyle.CATEGORIES -> "Auto-grouped by the category each app declares (games, audio, social…)."
                DrawerStyle.TERMINAL -> "Monospace command-line list."
            },
            style = nx.body(12f, color = nx.palette.textDim),
        )
        SliderRow("Grid columns", cfg.drawerCols.toFloat(), 3f..6f, { v -> update { it.copy(drawerCols = v.roundToInt()) } }, { "${it.roundToInt()}" }, steps = 2)
        SectionHeader("Page transition")
        EnumChips(PageTransition.entries, cfg.pageTransition) { v -> update { it.copy(pageTransition = v) } }
    }
}

@Composable
fun CallSection(cfg: NexusConfig, update: Update, host: StudioHost) {
    val nx = LocalNexus.current
    val ctx = LocalContext.current
    val isDialer = remember(host.refreshTick) { com.nexus.launcher.call.DialerActivity.isDefaultDialer(ctx) }
    var phase by remember { mutableStateOf(CallPhase.RINGING) }
    Column {
        SectionHeader("Call screen")
        GlassPanel(Modifier.fillMaxWidth(), highlight = !isDialer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isDialer) "NEXUS is your phone app" else "Calls use your current phone app", style = nx.body(14f))
                    Text("Android only allows custom call screens from the default phone app.", style = nx.body(11.5f, color = nx.palette.textDim))
                }
                if (isDialer) StatusDot(true) else NxButton("Enable", { host.requestDialerRole() }, primary = true)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            HoloChip("INCOMING", phase == CallPhase.RINGING, { phase = CallPhase.RINGING })
            HoloChip("IN CALL", phase == CallPhase.ACTIVE, { phase = CallPhase.ACTIVE })
        }
        PreviewFrame(470) {
            SurfaceAccent(cfg.callAccent) {
                CallScreen(
                    CallUi(0, "Sample Contact", "+00 00000 00000", phase, System.currentTimeMillis() - 83_000),
                    cfg.callStyle, cfg.answerGesture, muted = false, speaker = phase == CallPhase.ACTIVE,
                    CallActions({}, {}, {}, {}, {}, {}, {}),
                    Modifier.fillMaxSize(),
                )
            }
        }
        Label("Style")
        EnumChips(CallStyle.entries, cfg.callStyle) { v -> update { it.copy(callStyle = v) } }
        Label("Answer gesture")
        EnumChips(AnswerGesture.entries, cfg.answerGesture) { v -> update { it.copy(answerGesture = v) } }
        AccentRow("Call colour", cfg.callAccent) { v -> update { it.copy(callAccent = v) } }
    }
}

@Composable
fun LockSection(cfg: NexusConfig, update: Update, host: StudioHost) {
    val nx = LocalNexus.current
    val ctx = LocalContext.current
    val overlay = remember(host.refreshTick) { android.provider.Settings.canDrawOverlays(ctx) }
    Column {
        SectionHeader("Lock layer & fingerprint")
        Text(
            "A NEXUS skin shown in front of Android's secure lock screen when the screen turns on. Unlocking is still done by Android (fingerprint, PIN, pattern). Your phone's own lock screen and sensor icon can't be replaced by any app.",
            style = nx.body(12f, color = nx.palette.textDim),
        )
        SwitchRow("Enable lock layer", cfg.lockLayer, { v -> update { it.copy(lockLayer = v) }; if (v && !overlay) host.openOverlayPermission() })
        if (cfg.lockLayer && !overlay) SettingRow("Display over other apps", "Needed to appear when the screen turns on") { NxButton("Grant", { host.openOverlayPermission() }) }
        PreviewFrame(380) {
            SurfaceAccent(cfg.lockAccent) {
                Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NexusClock(cfg.lockClockStyle, Modifier.fillMaxWidth(0.7f), seconds = true)
                    Spacer(Modifier.weight(1f))
                    FingerprintFx(cfg.fingerprintStyle, true, Modifier.size((cfg.fpSize * 1.6f).dp))
                }
            }
        }
        Label("Lock clock")
        EnumChips(ClockStyle.entries, cfg.lockClockStyle) { v -> update { it.copy(lockClockStyle = v) } }
        Label("Fingerprint animation")
        EnumChips(FingerprintStyle.entries, cfg.fingerprintStyle) { v -> update { it.copy(fingerprintStyle = v) } }
        NxButton("Calibrate sensor position", { host.calibrateFingerprint() }, Modifier.padding(top = 6.dp))
        AccentRow("Lock colour", cfg.lockAccent) { v -> update { it.copy(lockAccent = v) } }
    }
}

@Composable
fun AodSection(cfg: NexusConfig, update: Update, host: StudioHost) {
    val nx = LocalNexus.current
    val battery by LocalContext.current.nexus.system.battery.collectAsState()
    Column {
        SectionHeader("Always-on")
        Text(
            "Two ways to run NEXUS always-on faces: as Android's screen saver while charging (Settings › Display › Screen saver › NEXUS Ambient), or by letting the lock layer dim into ambient mode for a set time. The system AOD itself can't be replaced.",
            style = nx.body(12f, color = nx.palette.textDim),
        )
        PreviewFrame(360) {
            SurfaceAccent(cfg.aodAccent) {
                AmbientFace(cfg.aodStyle, cfg.aodClockStyle, battery, emptyList(), false, cfg.aodDim, Modifier.fillMaxSize())
            }
        }
        Label("Face")
        EnumChips(AodStyle.entries, cfg.aodStyle) { v -> update { it.copy(aodStyle = v) } }
        Label("Clock (Edge Pulse & Constellation)")
        EnumChips(ClockStyle.entries, cfg.aodClockStyle) { v -> update { it.copy(aodClockStyle = v) } }
        SliderRow("Brightness", cfg.aodDim, 0.15f..1f, { v -> update { it.copy(aodDim = v) } }, { "${(it * 100).roundToInt()}%" })
        SwitchRow("Show notification apps", cfg.aodNotifications, { v -> update { it.copy(aodNotifications = v) } })
        SwitchRow("Ambient on lock layer", cfg.aodOnLockLayer, { v -> update { it.copy(aodOnLockLayer = v) } }, "Keeps the screen on, dimmed — uses noticeably more battery")
        SliderRow("Stay on for", cfg.aodMinutes.toFloat(), 1f..30f, { v -> update { it.copy(aodMinutes = v.roundToInt()) } }, { "${it.roundToInt()} min" })
        NxButton("Open screen-saver settings", { host.openScreensaverSettings() }, Modifier.padding(top = 6.dp))
        AccentRow("Always-on colour", cfg.aodAccent) { v -> update { it.copy(aodAccent = v) } }
    }
}

@Composable
fun ChargingSection(cfg: NexusConfig, update: Update) {
    val battery by LocalContext.current.nexus.system.battery.collectAsState()
    Column {
        SectionHeader("Charging style")
        PreviewFrame(300) {
            SurfaceAccent(cfg.chargeAccent) {
                ChargingVisual(battery.copy(charging = true), cfg.chargingAnim, cfg.chargingIntensity, Modifier.size(260.dp))
            }
        }
        EnumChips(com.nexus.launcher.core.ChargingAnim.entries, cfg.chargingAnim) { v -> update { it.copy(chargingAnim = v) } }
        AccentRow("Charging colour", cfg.chargeAccent) { v -> update { it.copy(chargeAccent = v) } }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WidgetsSection() {
    val nx = LocalNexus.current
    var kind by remember { mutableStateOf(NexusWidgetKind.CLOCK) }
    var style by remember { mutableStateOf(0) }
    var frame by remember { mutableStateOf(nx.config.widgetFrame) }
    Column {
        SectionHeader("Widgets")
        Text(
            "${NexusWidgetKind.entries.size} built-in widgets, up to 8 styles each, 6 frames. Add from home: long-press empty space › Add widget. Long-press a widget to change its style, frame or content.",
            style = nx.body(12f, color = nx.palette.textDim),
        )
        Label("Widget")
        EnumChips(NexusWidgetKind.entries, kind) { kind = it; style = 0 }
        Label("Style")
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            nexusStyleNames(kind).take(kind.styles).forEachIndexed { i, n -> HoloChip(n, style == i, { style = i }) }
        }
        Label("Frame")
        EnumChips(com.nexus.launcher.core.WidgetFrame.entries, frame) { frame = it }
        Box(Modifier.fillMaxWidth().height(if (kind.h >= 2) 180.dp else 96.dp).padding(vertical = 8.dp)) {
            NexusModule(kind, style, frame = frame)
        }
    }
}

/** "Use theme colour" or a custom accent for one surface. */
@Composable
private fun AccentRow(title: String, value: Long?, onChange: (Long?) -> Unit) {
    Column {
        SwitchRow("$title: custom", value != null, { on -> onChange(if (on) (value ?: 0xFFFF2E88L) else null) }, if (value == null) "Using the theme accent" else null)
        if (value != null) ColorEditor(title, value) { onChange(it) }
    }
}

@Suppress("unused")
private fun previewBattery() = BatteryState(level = 72, charging = true)
