package com.nexus.launcher.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.core.BackgroundStyle
import com.nexus.launcher.core.BodyFont
import com.nexus.launcher.core.ChargingAnim
import com.nexus.launcher.core.ControlModules
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.FlashlightOn
import com.nexus.launcher.core.DeckStyle
import com.nexus.launcher.core.DisplayFont
import com.nexus.launcher.core.GestureAction
import com.nexus.launcher.core.HapticLevel
import com.nexus.launcher.core.IconPack
import com.nexus.launcher.core.IconShape
import com.nexus.launcher.core.IconStyle
import com.nexus.launcher.core.LaunchAnim
import com.nexus.launcher.core.NamedTheme
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.PerfMode
import com.nexus.launcher.core.StatusPosition
import com.nexus.launcher.core.ThemePresets
import com.nexus.launcher.core.ThemeSpec
import com.nexus.launcher.core.UnlockAnim
import com.nexus.launcher.core.pretty
import com.nexus.launcher.nexus
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.ui.components.AppIcon
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.HoloChip
import com.nexus.launcher.ui.components.HoloSwitch
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.SectionHeader
import com.nexus.launcher.ui.components.SettingRow
import com.nexus.launcher.ui.components.SliderRow
import com.nexus.launcher.ui.components.StatusDot
import com.nexus.launcher.ui.components.SwitchRow
import com.nexus.launcher.ui.fx.ChargingOverlay
import com.nexus.launcher.ui.fx.DotMatrixText
import com.nexus.launcher.ui.fx.EntranceOverlay
import com.nexus.launcher.ui.fx.LaunchFxEvent
import com.nexus.launcher.ui.fx.LaunchFxOverlay
import com.nexus.launcher.ui.fx.LocalEntrance
import com.nexus.launcher.ui.fx.NexusBackground
import com.nexus.launcher.ui.fx.entrance
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SECTIONS = listOf(
    "Experience", "Look", "System", "Interface", "Orbit", "Radial", "Operator", "Glyphs", "Clock", "Drawer", "Calls", "Lock", "Always-on", "Charging", "Widgets", "Themes", "Colors", "Effects", "Type", "Icons", "Layout", "Motion",
    "Signals", "Control", "Gestures", "Performance", "Weather", "Backup", "About",
)

@Composable
fun StudioScreen(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit, host: StudioHost) {
    val nx = LocalNexus.current
    val p = nx.palette
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var chargePreview by remember { mutableStateOf(false) }
    val battery by LocalContext.current.nexus.system.battery.collectAsStateWithLifecycle()

    fun theme(t: (ThemeSpec) -> ThemeSpec) = update { it.copy(theme = t(it.theme), presetName = "Custom") }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nx.tag(if (nx.tech) "NEXUS Studio" else "Customize"), style = nx.label(10f, p.accent))
                    Text(cfg.presetName, style = nx.display(22f, p.text, weight = 300))
                }
                NxButton("Home", { host.goHome() })
            }
            LivePreview(onCharge = { chargePreview = true })
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SECTIONS.size) { i ->
                    HoloChip(SECTIONS[i], false, { scope.launch { list.animateScrollToItem(i) } })
                }
            }
            LazyColumn(state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp)) {
                item("experience") { ExperienceSection(cfg, update) }
                item("look") { LookSection(cfg, update) }
                item("system") { SystemSection(host) }
                item("interface") { InterfaceSection(cfg, update) }
                item("orbit") { OrbitSection(cfg, update) }
                item("radial") { RadialSection(cfg, update) }
                item("operator") { OperatorSection(cfg, update, host) }
                item("glyphs") { GlyphSection(cfg, update, host) }
                item("clock") { ClockSection(cfg, update) }
                item("drawer") { DrawerSection(cfg, update) }
                item("calls") { CallSection(cfg, update, host) }
                item("lock") { LockSection(cfg, update, host) }
                item("aod") { AodSection(cfg, update, host) }
                item("charging") { ChargingSection(cfg, update) }
                item("widgets") { WidgetsSection() }
                item("themes") { ThemesSection(cfg, update) }
                item("colors") {
                    Column {
                        SectionHeader("Color editor")
                        ColorEditor("Primary accent", cfg.theme.accent) { c -> theme { it.copy(accent = c) } }
                        ColorEditor("Secondary accent", cfg.theme.accent2) { c -> theme { it.copy(accent2 = c) } }
                        ColorEditor("Animation color", cfg.theme.fxColor) { c -> theme { it.copy(fxColor = c) } }
                        ColorEditor("Background", cfg.theme.background) { c -> theme { it.copy(background = c) } }
                        SwitchRow("Gradients", cfg.theme.gradient, { v -> theme { it.copy(gradient = v) } })
                    }
                }
                item("effects") {
                    Column {
                        SectionHeader("Effects")
                        SliderRow("Glow intensity", cfg.theme.glow, 0f..1f, { v -> theme { it.copy(glow = v) } })
                        SliderRow("Panel opacity", cfg.theme.panelOpacity, 0.1f..1f, { v -> theme { it.copy(panelOpacity = v) } })
                        SliderRow("Background blur (Android 12+)", cfg.theme.blur, 0f..1f, { v -> theme { it.copy(blur = v) } })
                        SliderRow("Outline weight", cfg.theme.outline, 0.3f..2.5f, { v -> theme { it.copy(outline = v) } })
                        SwitchRow("Glow halos", cfg.theme.shadows, { v -> theme { it.copy(shadows = v) } })
                        Label("Background")
                        EnumChips(BackgroundStyle.entries, cfg.theme.backgroundStyle) { v -> theme { it.copy(backgroundStyle = v) } }
                        SwitchRow("Animated background", cfg.theme.animatedBackground, { v -> theme { it.copy(animatedBackground = v) } }, "Pauses automatically when idle in Balanced")
                        SliderRow("Particle intensity", cfg.theme.particleIntensity, 0f..1f, { v -> theme { it.copy(particleIntensity = v) } })
                    }
                }
                item("type") {
                    Column {
                        SectionHeader("Typography")
                        Label("Display & numerals")
                        EnumChips(DisplayFont.entries, cfg.theme.displayFont) { v -> theme { it.copy(displayFont = v) } }
                        Label("Body text")
                        EnumChips(BodyFont.entries, cfg.theme.bodyFont) { v -> theme { it.copy(bodyFont = v) } }
                        SliderRow("Text scale", cfg.theme.textScale, 0.8f..1.3f, { v -> theme { it.copy(textScale = v) } }, { "%.0f%%".format(it * 100) })
                        SliderRow("Weight", cfg.theme.fontWeight.toFloat(), 100f..800f, { v -> theme { it.copy(fontWeight = ((v / 100f).roundToInt() * 100)) } }, { "${it.roundToInt()}" }, steps = 6)
                        SliderRow("Letter spacing", cfg.theme.letterSpacing, 0f..3f, { v -> theme { it.copy(letterSpacing = v) } })
                    }
                }
                item("icons") { IconsSection(cfg, update) }
                item("layout") {
                    Column {
                        SectionHeader("Layout")
                        SliderRow("Grid columns", cfg.gridCols.toFloat(), 3f..6f, { v -> update { it.copy(gridCols = v.roundToInt()) } }, { "${it.roundToInt()}" }, steps = 2)
                        SliderRow("Grid rows", cfg.gridRows.toFloat(), 4f..8f, { v -> update { it.copy(gridRows = v.roundToInt()) } }, { "${it.roundToInt()}" }, steps = 3)
                        SliderRow("Drawer columns", cfg.drawerCols.toFloat(), 3f..6f, { v -> update { it.copy(drawerCols = v.roundToInt()) } }, { "${it.roundToInt()}" }, steps = 2)
                        SwitchRow("CORE dashboard page", cfg.dashboardEnabled, { v -> update { it.copy(dashboardEnabled = v) } })
                        SwitchRow("Dock", cfg.dockEnabled, { v -> update { it.copy(dockEnabled = v) } })
                        SliderRow("Default page", cfg.defaultPage.toFloat(), 0f..4f, { v -> update { it.copy(defaultPage = v.roundToInt()) } }, { if (it < 0.5f && cfg.dashboardEnabled) "CORE" else "${it.roundToInt() + 1}" }, steps = 3)
                        SwitchRow("NEXUS status strip", cfg.statusStrip, { v -> update { it.copy(statusStrip = v) } }, "Launcher telemetry only — other apps keep the system bar")
                        EnumChips(StatusPosition.entries, cfg.statusPosition) { v -> update { it.copy(statusPosition = v) } }
                        SwitchRow("Immersive home", cfg.hideSystemStatusBar, { v -> update { it.copy(hideSystemStatusBar = v) } }, "Hides the system status bar on the home screen; swipe from the top to reveal it")
                        SwitchRow("Show seconds on clock", cfg.clockSeconds, { v -> update { it.copy(clockSeconds = v) } })
                        SwitchRow("Lock home layout", cfg.lockLayout, { v -> update { it.copy(lockLayout = v) } })
                        Spacer(Modifier.height(6.dp))
                        NxButton("Reset home layout", { host.resetLayout() })
                    }
                }
                item("motion") { MotionSection(cfg, update) }
                item("signals") {
                    Column {
                        SectionHeader("Notification feed")
                        SwitchRow("Popup previews", cfg.notifPopups, { v -> update { it.copy(notifPopups = v) } }, "Shown only while NEXUS is on screen")
                        SwitchRow("Hide notification content", cfg.notifRedact, { v -> update { it.copy(notifRedact = v) } }, "Show app names only — useful in public")
                        SwitchRow("Icon badges", cfg.badges, { v -> update { it.copy(badges = v) } })
                        SliderRow("Animation speed", cfg.notifAnimSpeed, 0.5f..2f, { v -> update { it.copy(notifAnimSpeed = v) } }, { "%.1f×".format(it) })
                        SliderRow("Card opacity", cfg.notifOpacity, 0.3f..1f, { v -> update { it.copy(notifOpacity = v) } })
                    }
                }
                item("control") { ControlSection(cfg, update) }
                item("gestures") { GesturesSection(cfg, update) }
                item("perf") {
                    Column {
                        SectionHeader("Performance")
                        EnumChips(PerfMode.entries, cfg.perfMode) { v -> update { it.copy(perfMode = v) } }
                        Text(
                            when (cfg.perfMode) {
                                PerfMode.AUTO -> "Balanced, dropping to Battery Saver automatically under power-saver or heat."
                                PerfMode.ULTRA -> "Continuous ambient animation, full particles, max refresh rate. Throttles itself when the phone gets hot."
                                PerfMode.BALANCED -> "Full effects; ambient background animates for a few seconds after you interact, then rests."
                                PerfMode.BATTERY_SAVER -> "Static background, minimal effects, no blur. Lowest power."
                            },
                            style = nx.body(12f, color = p.textDim),
                        )
                        Text("Now: ${nx.quality.reason}", style = nx.label(9.5f, p.accent), modifier = Modifier.padding(top = 4.dp))
                        SwitchRow("Frame stats overlay", cfg.showFrameStats, { v -> update { it.copy(showFrameStats = v) } }, "Measures FPS and janky frames on the home screen")
                        SliderRow("Global animation speed", cfg.animSpeed, 0.5f..2f, { v -> update { it.copy(animSpeed = v) } }, { "%.1f×".format(it) })
                    }
                }
                item("weather") {
                    Column {
                        SectionHeader("Weather")
                        val ctx = LocalContext.current
                        SwitchRow("Weather module", cfg.weatherEnabled, { v ->
                            update { it.copy(weatherEnabled = v) }
                            if (v && !ctx.nexus.weather.hasPermission()) host.requestLocation()
                        }, "Open-Meteo · approximate location · no account")
                        SwitchRow("Celsius", cfg.weatherCelsius, { v -> update { it.copy(weatherCelsius = v) } })
                    }
                }
                item("backup") {
                    Column {
                        SectionHeader("Backup")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NxButton("Export", { host.exportBackup() })
                            NxButton("Import", { host.importBackup() })
                            NxButton("Reset all", { update { NexusConfig() } })
                        }
                        Text("Backups contain settings, themes and home layout. Widgets and custom icon images are device-specific and are not transferred.", style = nx.body(11.5f, color = p.textDim), modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item("about") { AboutSection() }
            }
        }

        ChargingOverlay(
            visible = chargePreview, battery = battery, style = cfg.chargingAnim, intensity = cfg.chargingIntensity,
            durationSec = cfg.chargingDurationSec, onDismiss = { chargePreview = false },
        )
    }
}

@Composable
internal fun Label(text: String) {
    val nx = LocalNexus.current
    Text(text.uppercase(), style = nx.label(9.5f, nx.palette.textFaint), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T : Enum<T>> EnumChips(values: List<T>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        values.forEach { v -> HoloChip(v.pretty(), v == selected, { onSelect(v) }) }
    }
}

@Composable
private fun LivePreview(onCharge: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val apps by ctx.nexus.apps.apps.collectAsStateWithLifecycle()
    val entrance = remember { mutableFloatStateOf(1f) }
    var unlockTick by remember { mutableLongStateOf(0L) }
    var fx by remember { mutableStateOf<LaunchFxEvent?>(null) }
    var center by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(unlockTick) {
        if (unlockTick > 0) animate(0f, 1f, animationSpec = tween(nx.dur(1100), easing = LinearEasing)) { v, _ -> entrance.floatValue = v }
    }

    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(236.dp), padding = 0.dp, highlight = true) {
        CompositionLocalProvider(LocalEntrance provides entrance) {
            Box(Modifier.fillMaxSize().clip(NxShapes.panel)) {
                NexusBackground(animate = true, modifier = Modifier.fillMaxSize())
                Column(
                    Modifier.fillMaxSize().onSizeChanged { center = Offset(it.width / 2f, it.height / 2f) }.padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
                        // the real time, as the home screen would show it
                        val now by com.nexus.launcher.ui.home.rememberNow(false)
                        val is24 = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
                        val time = now.format(java.time.format.DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "hh:mm"))
                        if (nx.matrixClock) DotMatrixText(time, pitch = 4.dp, color = p.text)
                        else Text(time, style = nx.display(34f, p.text, weight = 300))
                        Spacer(Modifier.weight(1f))
                        Text("LIVE PREVIEW", style = nx.label(9f, p.accent))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().entrance(1), horizontalArrangement = Arrangement.SpaceEvenly) {
                        apps.take(4).forEach { a -> AppIcon(a, a.label, nx.config.iconSize * 0.85f, nx.config.showLabels, badge = if (a == apps.first()) 1 else 0) }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.entrance(2), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NxButton("Launch", { fx = LaunchFxEvent(System.nanoTime(), center, 120f, nx.config.launchAnim) })
                        NxButton("Unlock", { entrance.floatValue = 0f; unlockTick = System.nanoTime() })
                        NxButton("Charge", onCharge)
                    }
                }
                EntranceOverlay(entrance)
                LaunchFxOverlay(fx) { fx = null }
            }
        }
    }
}

@Composable
private fun SystemSection(host: StudioHost) {
    val nx = LocalNexus.current
    val ctx = LocalContext.current
    val tick = host.refreshTick
    val isDefault = remember(tick) { SystemControls.isDefaultHome(ctx) }
    val notif = remember(tick) { SystemControls.notificationAccessGranted(ctx) }
    val a11y = remember(tick) { SystemControls.accessibilityEnabled(ctx) }
    val write = remember(tick) { SystemControls.canWriteSettings(ctx) }
    val dnd = remember(tick) { SystemControls.dndAccessGranted(ctx) }
    Column {
        SectionHeader("System link")
        GlassPanel(Modifier.fillMaxWidth(), highlight = !isDefault) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isDefault) "NEXUS is your Home app" else "NEXUS is not the default launcher", style = nx.body(14.5f))
                    Text(if (isDefault) "Home button opens NEXUS" else "Press to choose NEXUS as Home", style = nx.body(11.5f, color = nx.palette.textDim))
                }
                if (!isDefault) NxButton("Set default", { host.setDefaultLauncher() }, primary = true) else StatusDot(true)
            }
        }
        PermRow("Notification access", "Feed, badges, media controls, popups", notif) { host.openNotificationAccess() }
        PermRow("Gesture helper (accessibility)", "Double-tap lock, open shade/QS reliably. Reads no screen content.", a11y) { host.openAccessibility() }
        PermRow("Modify system settings", "Brightness dial and auto-rotate toggle", write) { host.openWriteSettings() }
        PermRow("Do Not Disturb access", "Real DND toggle in the control center", dnd) { host.openDndAccess() }
    }
}

@Composable
private fun PermRow(title: String, subtitle: String, granted: Boolean, onGrant: () -> Unit) {
    SettingRow(title, subtitle) {
        if (granted) StatusDot(true) else NxButton("Grant", onGrant)
    }
}

@Composable
private fun ThemesSection(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column {
        SectionHeader("Theme presets")
        listOf(
            "Everyday · ${ThemePresets.everyday.size}" to ThemePresets.everyday,
            "Futuristic · ${ThemePresets.futuristic.size}" to ThemePresets.futuristic,
            "Yours" to cfg.savedThemes,
        ).filter { it.second.isNotEmpty() }.forEach { (title, group) ->
            Label(title)
            group.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    row.forEach { t -> ThemeCard(t, cfg.presetName == t.name, Modifier.weight(1f)) { update { it.copy(theme = t.spec, presetName = t.name) } } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (cfg.savedThemes.isNotEmpty()) {
            Label("Saved")
            cfg.savedThemes.forEach { t ->
                SettingRow(t.name) { NxButton("Delete", { update { c -> c.copy(savedThemes = c.savedThemes.filterNot { it.name == t.name }) } }) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!saving) NxButton("Save current look", { saving = true; name = "" })
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(NxShapes.chip).background(p.panel).padding(12.dp)) {
                if (name.isEmpty()) Text("Theme name", style = nx.body(14f, color = p.textFaint))
                BasicTextField(name, { name = it.take(24) }, singleLine = true, textStyle = nx.body(14f), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(8.dp))
            NxButton("Save", {
                val n = name.ifBlank { "Custom ${cfg.savedThemes.size + 1}" }
                update { c -> c.copy(savedThemes = c.savedThemes.filterNot { it.name == n } + NamedTheme(n, c.theme), presetName = n) }
                saving = false
            }, primary = true)
        }
    }
}

@Composable
private fun ThemeCard(t: NamedTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val accent = Color(t.spec.accent)
    val accent2 = Color(t.spec.accent2)
    Column(
        modifier
            .clip(NxShapes.tile)
            .background(Color(t.spec.background))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else accent.copy(alpha = 0.3f), NxShapes.tile)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row {
            Box(Modifier.size(14.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(14.dp).clip(CircleShape).background(accent2))
        }
        Spacer(Modifier.height(10.dp))
        Text(t.name.uppercase(), style = nx.label(9.5f, accent), maxLines = 1)
        Text("${t.spec.backgroundStyle.pretty()} · ${t.spec.displayFont.pretty()}", style = nx.body(10.5f, color = Color.White.copy(alpha = 0.55f)), maxLines = 1)
    }
}

@Composable
internal fun ColorEditor(label: String, value: Long, onChange: (Long) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    var expanded by remember { mutableStateOf(false) }
    val color = Color(value)
    val hsv = remember(value) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hex by remember(value) { mutableStateOf("%06X".format(value and 0xFFFFFFL)) }
    fun emit(h: Float, s: Float, v: Float) {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        onChange(argb.toLong() and 0xFFFFFFFFL)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(NxShapes.chip).background(color).border(1.dp, p.border, NxShapes.chip))
            Spacer(Modifier.width(12.dp))
            Text(label, style = nx.body(14f), modifier = Modifier.weight(1f))
            Text("#${"%06X".format(value and 0xFFFFFFL)}", style = nx.label(10.5f, p.textDim))
        }
        if (expanded) {
            val swatches = listOf(0xFFFFFFFFL, 0xFFD71921L, 0xFF00E5FFL, 0xFFB26BFFL, 0xFF4D7CFFL, 0xFF3DFF9AL, 0xFFFFB800L, 0xFFC9D1D9L, 0xFF000000L, 0xFF05070AL)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                swatches.forEach { s ->
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(s)).border(1.dp, p.border, CircleShape).clickable { onChange(s) })
                }
            }
            SliderRow("Hue", hsv[0], 0f..360f, { emit(it, hsv[1], hsv[2]) }, { "${it.roundToInt()}°" })
            SliderRow("Saturation", hsv[1], 0f..1f, { emit(hsv[0], it, hsv[2]) })
            SliderRow("Brightness", hsv[2], 0f..1f, { emit(hsv[0], hsv[1], it) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HEX #", style = nx.label(10f))
                Box(Modifier.weight(1f).clip(NxShapes.chip).background(p.panel).padding(10.dp)) {
                    BasicTextField(hex, { t ->
                        hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        if (hex.length == 6) onChange(0xFF000000L or hex.toLong(16))
                    }, singleLine = true, textStyle = nx.body(14f), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconsSection(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit) {
    val ctx = LocalContext.current
    val packs = remember { IconPack.installed(ctx) }
    Column {
        SectionHeader("Icons")
        Label("Style")
        EnumChips(IconStyle.entries, cfg.iconStyle) { v -> update { it.copy(iconStyle = v) } }
        Label("Shape")
        EnumChips(IconShape.entries, cfg.iconShape) { v -> update { it.copy(iconShape = v) } }
        SliderRow("Icon size", cfg.iconSize, 40f..72f, { v -> update { it.copy(iconSize = v.roundToInt().toFloat()) } }, { "${it.roundToInt()}dp" })
        SwitchRow("Labels", cfg.showLabels, { v -> update { it.copy(showLabels = v) } })
        SliderRow("Label size", cfg.labelSize, 9f..14f, { v -> update { it.copy(labelSize = v) } }, { "%.1f".format(it) })
        SwitchRow("Accent tint", cfg.iconTint, { v -> update { it.copy(iconTint = v) } }, "Monochrome-holographic look for every icon")
        Label("Icon pack")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HoloChip("NEXUS (none)", cfg.iconPack.isEmpty(), { update { it.copy(iconPack = "") } })
            packs.forEach { (pkg, label) -> HoloChip(label, cfg.iconPack == pkg, { update { it.copy(iconPack = pkg) } }) }
        }
        if (packs.isEmpty()) Text("No ADW/Nova-compatible icon packs installed.", style = LocalNexus.current.body(11.5f, color = LocalNexus.current.palette.textDim))
        Text("Per-app icons and names: long-press an icon › Change icon / Rename.", style = LocalNexus.current.body(11.5f, color = LocalNexus.current.palette.textDim), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MotionSection(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit) {
    Column {
        SectionHeader("App launch")
        EnumChips(LaunchAnim.entries, cfg.launchAnim) { v -> update { it.copy(launchAnim = v) } }
        SliderRow("Pre-launch flourish", cfg.launchLeadMs.toFloat(), 0f..150f, { v -> update { it.copy(launchLeadMs = v.roundToInt()) } }, { "${it.roundToInt()} ms" })
        SectionHeader("Post-unlock reveal")
        SwitchRow("Play after unlocking", cfg.unlockEnabled, { v -> update { it.copy(unlockEnabled = v) } }, "Runs after Android's secure lock screen — never replaces it")
        EnumChips(UnlockAnim.entries, cfg.unlockAnim) { v -> update { it.copy(unlockAnim = v) } }
        SectionHeader("Charging")
        SwitchRow("Charging animation", cfg.chargingEnabled, { v -> update { it.copy(chargingEnabled = v) } }, "Shown in NEXUS when you plug in, or on unlock shortly after")
        EnumChips(ChargingAnim.entries, cfg.chargingAnim) { v -> update { it.copy(chargingAnim = v) } }
        SliderRow("Duration", cfg.chargingDurationSec.toFloat(), 2f..15f, { v -> update { it.copy(chargingDurationSec = v.roundToInt()) } }, { "${it.roundToInt()} s" })
        SliderRow("Intensity", cfg.chargingIntensity, 0.1f..1f, { v -> update { it.copy(chargingIntensity = v) } })
        SliderRow("Show after unlock within", cfg.chargingWindowSec.toFloat(), 10f..180f, { v -> update { it.copy(chargingWindowSec = v.roundToInt()) } }, { "${it.roundToInt()} s" })
        SliderRow("Screen saver intro on the charger", cfg.dreamChargeIntroSec.toFloat(), 0f..30f, { v -> update { it.copy(dreamChargeIntroSec = v.roundToInt()) } }, { if (it < 1f) "Off" else "${it.roundToInt()} s" })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlSection(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit) {
    val nx = LocalNexus.current
    val enabled = cfg.controlModules
    val order = enabled + ControlModules.all.filter { it !in enabled }
    Column {
        SectionHeader("Control deck style")
        EnumChips(DeckStyle.entries, cfg.deckStyle) { v -> update { it.copy(deckStyle = v) } }
        Label("Preview")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                com.nexus.launcher.ui.control.Tile(Icons.Rounded.FlashlightOn, "Flashlight", "ON", active = true, onClick = {}, onLong = {}, style = cfg.deckStyle)
            }
            Box(Modifier.weight(1f)) {
                com.nexus.launcher.ui.control.Tile(Icons.Rounded.Wifi, "Wi-Fi", "OFF", active = false, panel = true, onClick = {}, onLong = {}, style = cfg.deckStyle)
            }
        }
        Label("Columns")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(2, 3).forEach { n -> HoloChip("$n", cfg.deckColumns == n, { update { it.copy(deckColumns = n) } }) }
        }
        SwitchRow("Clock & battery header", cfg.deckHeader, { v -> update { it.copy(deckHeader = v) } })
        SectionHeader("Control center modules")
        order.forEach { m ->
            val on = m in enabled
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.lowercase().replaceFirstChar { it.uppercase() }, style = nx.body(14f, color = if (on) nx.palette.text else nx.palette.textFaint), modifier = Modifier.weight(1f))
                if (on) {
                    IconButton(onClick = { update { c -> c.copy(controlModules = move(c.controlModules, m, -1)) } }) { Icon(Icons.Rounded.KeyboardArrowUp, "Up", tint = nx.palette.textDim) }
                    IconButton(onClick = { update { c -> c.copy(controlModules = move(c.controlModules, m, 1)) } }) { Icon(Icons.Rounded.KeyboardArrowDown, "Down", tint = nx.palette.textDim) }
                }
                HoloSwitch(on, { v -> update { c -> c.copy(controlModules = if (v) c.controlModules + m else c.controlModules - m) } })
            }
        }
    }
}

private fun move(list: List<String>, item: String, delta: Int): List<String> {
    val i = list.indexOf(item)
    val j = i + delta
    if (i < 0 || j !in list.indices) return list
    return list.toMutableList().apply { this[i] = list[j]; this[j] = item }
}

@Composable
private fun GesturesSection(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit) {
    val actions = GestureAction.entries
    Column {
        SectionHeader("Gestures")
        Label("Swipe up"); EnumChips(actions, cfg.swipeUp) { v -> update { it.copy(swipeUp = v) } }
        SwitchRow("Split swipe-down (left / right half)", cfg.splitSwipeDown, { v -> update { it.copy(splitSwipeDown = v) } })
        Label(if (cfg.splitSwipeDown) "Swipe down · left half" else "Swipe down"); EnumChips(actions, cfg.swipeDown) { v -> update { it.copy(swipeDown = v) } }
        if (cfg.splitSwipeDown) { Label("Swipe down · right half"); EnumChips(actions, cfg.swipeDownRight) { v -> update { it.copy(swipeDownRight = v) } } }
        Label("Double tap"); EnumChips(actions, cfg.doubleTap) { v -> update { it.copy(doubleTap = v) } }
        Label("Pinch in"); EnumChips(actions, cfg.pinchIn) { v -> update { it.copy(pinchIn = v) } }
        Label("Pinch out"); EnumChips(actions, cfg.pinchOut) { v -> update { it.copy(pinchOut = v) } }
        Label("Haptic feedback"); EnumChips(HapticLevel.entries, cfg.haptics) { v -> update { it.copy(haptics = v) } }
    }
}

@Composable
private fun AboutSection() {
    val nx = LocalNexus.current
    val p = nx.palette
    Column {
        SectionHeader("What Android allows")
        listOf(
            "Lock screen: NEXUS plays its reveal after you unlock; it cannot and does not replace the secure lock screen.",
            "Notifications: the NEXUS feed reads notifications with your permission; the system shade still works and is one tap away.",
            "Status bar: NEXUS shows its own telemetry on the home screen; other apps keep the normal system status bar.",
            "Wi-Fi, Bluetooth, mobile data, NFC: Android 10+ blocks third-party toggles, so these open the official system panels.",
            "App-open animations: NEXUS sets the window transition through ActivityOptions; the OS may override it (e.g. gesture navigation).",
            "Charging animation: appears inside NEXUS when it is on screen — never over the lock screen.",
        ).forEach { Text("◇ $it", style = nx.body(12f, color = p.textDim), modifier = Modifier.padding(vertical = 3.dp)) }
        Spacer(Modifier.height(10.dp))
        Text("NEXUS 1.0 · built for Nothing Phone · no root, no tracking", style = nx.label(9f, p.textFaint))
    }
}


@Composable
private fun InterfaceSection(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    Column {
        SectionHeader("Interface mode")
        com.nexus.launcher.core.InterfaceMode.entries.forEach { m ->
            val sel = cfg.interfaceMode == m
            val (title, body) = when (m) {
                com.nexus.launcher.core.InterfaceMode.CIVILIAN -> "CIVILIAN" to "Classic grid + dashboard. Long-press opens menus. Easiest."
                com.nexus.launcher.core.InterfaceMode.OPERATOR -> "OPERATOR" to "Orbit rings, radial launcher, HUD, gyroscope depth and the neural-link unlock ritual."
                com.nexus.launcher.core.InterfaceMode.ARCHITECT -> "ARCHITECT" to "Operator, but no app drawer: swipe up opens the shell. Apps live in orbit, glyphs, the radial or commands."
            }
            GlassPanel(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { update { it.copy(interfaceMode = m) } },
                highlight = sel, brackets = sel,
            ) {
                Column {
                    Text(title, style = nx.label(11f, if (sel) p.accent else p.text, weight = 700))
                    Text(body, style = nx.body(12f, color = p.textDim))
                }
            }
        }
        Text("Stuck? Open NEXUS Studio from any launcher, or type `mode civilian` in the shell.", style = nx.body(11f, color = p.textFaint), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun OperatorSection(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit, host: StudioHost) {
    Column {
        SectionHeader("Operator systems")
        SwitchRow("HUD overlay", cfg.hud, { v -> update { it.copy(hud = v) } }, "Reticles, edge rulers, compass from the real heading")
        SwitchRow("Telemetry ticker", cfg.hudTicker, { v -> update { it.copy(hudTicker = v) } })
        SwitchRow("Gyroscope depth", cfg.parallax, { v -> update { it.copy(parallax = v) } }, "Layers shift as you tilt the phone (sensor off when NEXUS is hidden)")
        SliderRow("Depth strength", cfg.parallaxStrength, 0.1f..1.5f, { v -> update { it.copy(parallaxStrength = v) } })
        SwitchRow("Neural-link unlock ritual", cfg.linkSync, { v -> update { it.copy(linkSync = v) } }, "Hold the core after unlocking. Always on in Architect. Back skips it.")
        SliderRow("Link hold time", cfg.linkSyncMs.toFloat(), 400f..3000f, { v -> update { it.copy(linkSyncMs = v.roundToInt()) } }, { "${it.roundToInt()} ms" })
        SwitchRow("Predictive ring", cfg.predictive, { v -> update { it.copy(predictive = v) } }, "Learns which apps you open at each hour — stored only on this phone")
        SwitchRow("Haptic signatures", cfg.hapticSignatures, { v -> update { it.copy(hapticSignatures = v) } }, "Distinct vibration patterns for launch, unlock, link, glyphs, charging")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            NxButton("Open shell", { host.openInLauncher("console") })
            NxButton("Wipe predictions", { host.clearUsage() })
        }
    }
}

@Composable
private fun GlyphSection(cfg: NexusConfig, update: ((NexusConfig) -> NexusConfig) -> Unit, host: StudioHost) {
    val nx = LocalNexus.current
    val p = nx.palette
    Column {
        SectionHeader("Glyphs")
        Text("Draw a symbol on the glyph pad (double-tap home, or hold the orbit core) to launch apps or actions. Built-ins: ○ shell · ⚡ torch · ^ control deck · V signals · L lock · Z app index.", style = nx.body(12f, color = p.textDim))
        Spacer(Modifier.height(8.dp))
        cfg.glyphs.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                com.nexus.launcher.ui.glyph.GlyphPreview(g.points, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = nx.body(14f))
                    Text(g.target.substringBefore(':').uppercase(), style = nx.label(9f, p.textFaint))
                }
                NxButton("Delete", { update { c -> c.copy(glyphs = c.glyphs.filterNot { it.id == g.id }) } })
            }
        }
        if (cfg.glyphs.isEmpty()) Text("No custom glyphs yet.", style = nx.body(12f, color = p.textFaint))
        Spacer(Modifier.height(8.dp))
        NxButton("Train a glyph", { host.openInLauncher("train") }, primary = true)
    }
}
