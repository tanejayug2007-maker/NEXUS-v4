package com.nexus.launcher.apps.settingshub

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.StatusBarManager
import android.graphics.drawable.Icon
import com.nexus.launcher.R
import com.nexus.launcher.system.tiles.NexusCalculatorTile
import com.nexus.launcher.system.tiles.NexusEcoTile
import com.nexus.launcher.system.tiles.NexusSettingsHubTile
import com.nexus.launcher.system.tiles.NexusShellTile
import com.nexus.launcher.system.tiles.NexusTorchTile
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.core.BatteryState
import com.nexus.launcher.nexus
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.system.TorchController
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.HudRule
import com.nexus.launcher.ui.components.NexusAppHeader
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.nexusAppBackground
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.theme.LocalNexus
import com.nexus.launcher.ui.theme.NexusHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A row the hub can actually open on this phone. */
data class ResolvedRow(val entry: HubEntry, val intent: Intent, val category: String)

/** Live state of the settings NEXUS can read (and, where allowed, change) without privileges. */
data class LiveState(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val location: Boolean = false,
    val dnd: Boolean = false,
    val dndAccess: Boolean = false,
    val canWrite: Boolean = false,
    val brightness: Float = 0.5f,
    val autoBrightness: Boolean = false,
    val autoRotate: Boolean = false,
    val volume: Float = 0.5f,
) {
    companion object {
        fun read(c: Context) = LiveState(
            wifi = SystemControls.wifiEnabled(c),
            bluetooth = SystemControls.bluetoothEnabled(c),
            location = SystemControls.locationEnabled(c),
            dnd = SystemControls.dndOn(c),
            dndAccess = SystemControls.dndAccessGranted(c),
            canWrite = SystemControls.canWriteSettings(c),
            brightness = SystemControls.brightness(c),
            autoBrightness = SystemControls.autoBrightness(c),
            autoRotate = SystemControls.autoRotate(c),
            volume = SystemControls.mediaVolume(c),
        )
    }
}

/**
 * NEXUS Settings Hub — a NEXUS-styled front door to the REAL Android / Nothing OS settings.
 * It does not replace or imitate the Settings app: every row either changes the real setting
 * through a public API (badge LIVE), opens Android's own panel (PANEL) or opens the genuine
 * Settings page (SYSTEM). Rows this phone can't open are hidden, never shown as dead links.
 */
class SettingsHubActivity : ComponentActivity() {

    private val torch by lazy { TorchController(this) }
    private var live by mutableStateOf(LiveState())
    private var rows by mutableStateOf<List<ResolvedRow>>(emptyList())
    private var nothingRows by mutableStateOf<List<NothingRow>>(emptyList())
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val (r, n) = withContext(Dispatchers.Default) { resolveRows(this@SettingsHubActivity) to NothingDiscovery.find(this@SettingsHubActivity) }
            rows = r
            nothingRows = n
        }
        setContent {
            NexusHost {
                val battery by nexus.system.battery.collectAsStateWithLifecycle()
                val torchOn by torch.on.collectAsStateWithLifecycle()
                HubScreen(
                    live = live, battery = battery, torchOn = torchOn, torchAvailable = torch.available,
                    rows = rows, nothingRows = nothingRows, refreshTick = refresh,
                    actions = actions,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        nexus.system.startForeground()
        torch.start()
    }

    override fun onResume() {
        super.onResume()
        // Settings may have changed in the pages we opened; re-read once (no polling).
        live = LiveState.read(this)
        refresh++
    }

    override fun onStop() {
        torch.stop()
        nexus.system.stopForeground()
        super.onStop()
    }

    private fun reread() { live = LiveState.read(this) }

    private val actions = object : HubActions {
        override fun open(intent: Intent) {
            if (!SystemControls.start(this@SettingsHubActivity, Intent(intent))) SystemControls.toast(this@SettingsHubActivity, "This page isn't available on this phone")
        }
        override fun toggleTorch() { if (!torch.toggle()) SystemControls.toast(this@SettingsHubActivity, "No flashlight available") }
        override fun toggleDnd() { SystemControls.toggleDnd(this@SettingsHubActivity); reread() }
        override fun toggleRotate() {
            if (!SystemControls.setAutoRotate(this@SettingsHubActivity, !live.autoRotate)) grantWrite() else reread()
        }
        override fun toggleAutoBrightness() {
            if (!SystemControls.setAutoBrightness(this@SettingsHubActivity, !live.autoBrightness)) grantWrite() else reread()
        }
        override fun setBrightness(v: Float) {
            if (SystemControls.setBrightness(this@SettingsHubActivity, v)) live = live.copy(brightness = v, autoBrightness = false) else grantWrite()
        }
        override fun setVolume(v: Float) { SystemControls.setMediaVolume(this@SettingsHubActivity, v); live = live.copy(volume = v) }
        override fun addTile(tile: TileOffer) {
            val c = this@SettingsHubActivity
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                SystemControls.toast(c, "Pull down Quick Settings › edit (pencil) › drag the NEXUS tile in"); return
            }
            val sbm = c.getSystemService(StatusBarManager::class.java)
            sbm.requestAddTileService(
                ComponentName(c, tile.service), c.getString(tile.label), Icon.createWithResource(c, tile.icon), c.mainExecutor,
            ) { result ->
                val msg = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added to Quick Settings"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "That tile is already in Quick Settings"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Not added"
                    else -> "Add it manually: Quick Settings › edit (pencil)"
                }
                SystemControls.toast(c, msg)
            }
        }
        override fun grantWrite() { SystemControls.start(this@SettingsHubActivity, SystemControls.writeSettingsIntent(this@SettingsHubActivity)) }
    }

    companion object {
        /** Picks the first action that resolves on this phone for every catalog entry. */
        fun resolveRows(c: Context): List<ResolvedRow> {
            val pm = c.packageManager
            return SettingsCatalog.categories.flatMap { cat ->
                cat.entries.mapNotNull { e ->
                    e.actions.asSequence().map { a ->
                        Intent(a).apply { if (e.packageUri) data = Uri.parse("package:${c.packageName}") }
                    }.firstOrNull { resolves(pm, it) }?.let { ResolvedRow(e, it, cat.id) }
                }
            }
        }

        @Suppress("DEPRECATION")
        fun resolves(pm: PackageManager, i: Intent): Boolean =
            runCatching { pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) != null }.getOrDefault(false)
    }
}

interface HubActions {
    fun open(intent: Intent)
    fun toggleTorch()
    fun toggleDnd()
    fun toggleRotate()
    fun toggleAutoBrightness()
    fun setBrightness(v: Float)
    fun setVolume(v: Float)
    fun grantWrite()
    /** Asks Android to add one NEXUS tile to Quick Settings (system confirmation dialog). */
    fun addTile(tile: TileOffer)
}

/** A NEXUS Quick Settings tile the hub can offer to add. */
data class TileOffer(val service: Class<*>, val label: Int, val icon: Int, val what: String)

val TILE_OFFERS = listOf(
    TileOffer(NexusTorchTile::class.java, R.string.tile_torch, R.drawable.ic_tile_torch, "Real flashlight toggle"),
    TileOffer(NexusEcoTile::class.java, R.string.tile_eco, R.drawable.ic_tile_eco, "Low-power NEXUS graphics on/off"),
    TileOffer(NexusCalculatorTile::class.java, R.string.tile_calc, R.drawable.ic_tile_calc, "Opens NEXUS Calculator"),
    TileOffer(NexusSettingsHubTile::class.java, R.string.tile_hub, R.drawable.ic_tile_settings, "Opens this hub"),
    TileOffer(NexusShellTile::class.java, R.string.tile_shell, R.drawable.ic_tile_shell, "Opens NEXUS//SHELL (NEXUS as Home)"),
)

/** A Nothing OS page or app found on this phone at runtime (nothing is assumed or hard-coded). */
data class NothingRow(val title: String, val subtitle: String, val intent: Intent)

object NothingDiscovery {
    /**
     * Launchable apps from Nothing's packages (e.g. Essential Space, Nothing X, Weather) plus any
     * exported, permission-free Settings activity whose name mentions Glyph. Read-only metadata.
     */
    @Suppress("DEPRECATION")
    fun find(c: Context): List<NothingRow> {
        val pm = c.packageManager
        val apps = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        }.getOrDefault(emptyList())
            .filter { it.activityInfo.packageName.startsWith("com.nothing") }
            .map { ri ->
                NothingRow(
                    ri.loadLabel(pm).toString(), "Nothing app",
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)),
                )
            }
        val glyph = listOf("com.android.settings").plus(apps.map { it.intent.component!!.packageName }).distinct()
            .flatMap { pkg ->
                val acts: Array<ActivityInfo> = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities }.getOrNull() ?: emptyArray()
                acts.filter { it.exported && it.permission == null && it.name.contains("glyph", ignoreCase = true) && it.enabled }
                    .map { a -> NothingRow(prettyName(a.name), "Glyph settings (found on this phone)", Intent().setComponent(ComponentName(a.packageName, a.name))) }
            }
            .distinctBy { it.title }
            .take(4)
        return (glyph + apps.sortedBy { it.title.lowercase() }).distinctBy { it.intent.component }
    }

    private fun prettyName(cls: String): String = cls.substringAfterLast('.').substringAfterLast('$')
        .removeSuffix("Activity").replace(Regex("([a-z])([A-Z])"), "$1 $2").ifBlank { "Glyph" }
}

// ---- UI --------------------------------------------------------------------------------------

@Composable
private fun HubScreen(
    live: LiveState, battery: BatteryState, torchOn: Boolean, torchAvailable: Boolean,
    rows: List<ResolvedRow>, nothingRows: List<NothingRow>, refreshTick: Int, actions: HubActions,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    var query by remember { mutableStateOf("") }
    val matches = remember(query, rows) {
        val ids = SettingsCatalog.search(query).map { it.id }.toSet()
        rows.filter { it.entry.id in ids }
    }
    val nothingMatches = remember(query, nothingRows) {
        nothingRows.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || "nothing glyph".contains(query.trim().lowercase()) }
    }

    LazyColumn(
        Modifier.fillMaxSize().nexusAppBackground().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column {
                NexusAppHeader("SYS", "Settings Hub", subtitle = "Real Android settings · NEXUS interface")
                HudRule(Modifier.padding(top = 8.dp))
            }
        }
        item(key = "core") { CoreHud(live, battery, refreshTick) }
        item(key = "search") { SearchField(query) { query = it } }
        if (query.isBlank()) {
            item(key = "legend") { Legend() }
            item(key = "live") { LiveControls(live, torchOn, torchAvailable, actions) }
        }
        SettingsCatalog.categories.forEach { cat ->
            val catRows = matches.filter { it.category == cat.id }
            if (catRows.isNotEmpty()) {
                item(key = "cat_${cat.id}") { CategoryPanel(cat, catRows, live, actions) }
            }
        }
        if (query.isBlank()) {
            item(key = "tiles") { TilesPanel(actions) }
        }
        if (nothingMatches.isNotEmpty()) {
            item(key = "nothing") { NothingPanel(nothingMatches, actions) }
        }
        if (query.isNotBlank() && matches.isEmpty() && nothingMatches.isEmpty()) {
            item(key = "empty") {
                Text(if (nx.tech) "NO MATCH // TRY “ALL SETTINGS”" else "No matching setting", style = nx.label(11f, p.textFaint), modifier = Modifier.padding(12.dp))
            }
        }
        item(key = "footer") {
            Text(
                "NEXUS does not replace Android Settings. SYSTEM and PANEL rows open Android's own screens; LIVE controls use public APIs only.",
                style = nx.body(11f, color = p.textFaint), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

/** Circular HUD: battery ring + four status segments. Draws once per change; one sweep on open. */
@Composable
private fun CoreHud(live: LiveState, battery: BatteryState, refreshTick: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    val sweep = remember { Animatable(if (nx.quality.tier > 0) 0f else 1f) }
    LaunchedEffect(Unit) { if (sweep.value < 1f) sweep.animateTo(1f, tween(nx.dur(900), easing = FastOutSlowInEasing)) }
    val level = battery.level.coerceIn(0, 100) / 100f
    val segments = listOf("WIFI" to live.wifi, "BT" to live.bluetooth, "LOC" to live.location, "DND" to live.dnd)

    GlassPanel(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Battery ${battery.level} percent" }) {
                    val sw = 7.dp.toPx()
                    val inset = sw / 2 + 2.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(p.border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                    drawArc(
                        if (level < 0.15f && !battery.charging) p.accent2 else p.accent,
                        -90f, 360f * level * sweep.value, false, Offset(inset, inset), arcSize,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                    // inner status ring: 4 segments, lit when that radio/mode is on
                    val r2 = inset + 14.dp.toPx()
                    val s2 = Size(size.width - r2 * 2, size.height - r2 * 2)
                    segments.forEachIndexed { i, (_, on) ->
                        drawArc(if (on) p.accent2 else p.border, -90f + i * 90f + 6f, 78f, false, Offset(r2, r2), s2,
                            style = Stroke(3.dp.toPx()), alpha = if (on) 0.9f else 0.6f)
                    }
                    // tick marks
                    for (k in 0 until 60) {
                        val a = Math.toRadians(k * 6.0 - 90).toFloat()
                        val ro = size.minDimension / 2 - 1.dp.toPx()
                        val ri = ro - (if (k % 5 == 0) 4.dp else 2.dp).toPx()
                        val c = center
                        drawLine(p.textFaint, Offset(c.x + ri * kotlin.math.cos(a), c.y + ri * kotlin.math.sin(a)),
                            Offset(c.x + ro * kotlin.math.cos(a), c.y + ro * kotlin.math.sin(a)), 1f, alpha = 0.5f)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${battery.level}%", style = nx.display(26f, p.text))
                    Text(if (battery.charging) nx.caps("charging") else nx.caps("battery"), style = nx.label(8.5f, if (battery.charging) p.accent else p.textDim))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                segments.forEach { (name, on) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(NxShapes.capsule).background(if (on) p.accent2 else p.border))
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = nx.label(10f, p.textDim), modifier = Modifier.width(40.dp))
                        Text(if (on) "ON" else "OFF", style = nx.label(10f, if (on) p.text else p.textFaint, weight = 600))
                    }
                }
                if (battery.temperatureC > 0f) {
                    Text("${"%.1f".format(battery.temperatureC)} °C · ${battery.health.ifEmpty { "—" }}", style = nx.label(9.5f, p.textFaint))
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(
        Modifier.fillMaxWidth().clip(NxShapes.chip).background(p.panel).border(1.dp, p.border, NxShapes.chip).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = p.textDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(if (nx.tech) "SEARCH SETTINGS" else "Search settings", style = nx.label(11f, p.textFaint))
            BasicTextField(query, onChange, singleLine = true, textStyle = nx.body(15f), cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search settings" })
        }
    }
}

@Composable
private fun Legend() {
    val nx = LocalNexus.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Capability.entries.forEach { c ->
            Column(Modifier.weight(1f)) {
                Badge(c)
                Spacer(Modifier.height(3.dp))
                Text(c.explanation, style = nx.body(10f, color = nx.palette.textFaint), maxLines = 2)
            }
        }
    }
}

@Composable
private fun Badge(c: Capability) {
    val nx = LocalNexus.current
    val p = nx.palette
    val color = when (c) { Capability.LIVE -> p.accent; Capability.PANEL -> p.accent2; Capability.SYSTEM -> p.textDim }
    Text(c.badge, style = nx.label(8.5f, color, weight = 700),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.6f), NxShapes.chip).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
private fun LiveControls(live: LiveState, torchOn: Boolean, torchAvailable: Boolean, a: HubActions) {
    val nx = LocalNexus.current
    val p = nx.palette
    GlassPanel(Modifier.fillMaxWidth(), highlight = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nx.tag("Live controls"), style = nx.label(11f, p.accent), modifier = Modifier.weight(1f))
                Badge(Capability.LIVE)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleTile(Icons.Outlined.FlashlightOn, "Torch", torchOn, enabled = torchAvailable, needsGrant = false, a::toggleTorch, Modifier.weight(1f))
                ToggleTile(Icons.Outlined.DoNotDisturbOn, "DND", live.dnd, enabled = true, needsGrant = !live.dndAccess, a::toggleDnd, Modifier.weight(1f))
                ToggleTile(Icons.Outlined.ScreenRotation, "Rotate", live.autoRotate, enabled = true, needsGrant = !live.canWrite, a::toggleRotate, Modifier.weight(1f))
                ToggleTile(Icons.Outlined.BrightnessAuto, "Auto", live.autoBrightness, enabled = true, needsGrant = !live.canWrite, a::toggleAutoBrightness, Modifier.weight(1f))
            }
            LiveSlider(Icons.Outlined.Brightness6, "Brightness", live.brightness, enabled = live.canWrite, a::setBrightness, a::grantWrite)
            LiveSlider(Icons.AutoMirrored.Outlined.VolumeUp, "Media volume", live.volume, enabled = true, a::setVolume) {}
            if (!live.canWrite || !live.dndAccess) {
                Text("GRANT = opens Android's permission page. Optional; nothing is granted automatically.", style = nx.body(10f, color = p.textFaint))
            }
        }
    }
}

@Composable
private fun ToggleTile(icon: ImageVector, label: String, on: Boolean, enabled: Boolean, needsGrant: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    Column(
        modifier
            .clip(NxShapes.tile)
            .background(if (on) p.accent.copy(alpha = 0.22f) else p.panel)
            .border(1.dp, if (on) p.accent else p.border, NxShapes.tile)
            .clickable(enabled = enabled, role = Role.Switch) { haptic(Haptic.TICK); onClick() }
            .padding(vertical = 10.dp)
            .semantics { contentDescription = "$label ${if (needsGrant) "needs permission" else if (on) "on" else "off"}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (!enabled) p.textFaint else if (on) p.accent else p.textDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(nx.caps(label), style = nx.label(9f, if (on) p.text else p.textDim), maxLines = 1)
        Text(if (needsGrant) "GRANT" else if (on) "ON" else "OFF", style = nx.label(8f, if (needsGrant) p.accent2 else p.textFaint, weight = 700))
    }
}

@Composable
private fun LiveSlider(icon: ImageVector, label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit, onGrant: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    var local by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.textDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(nx.caps(label), style = nx.label(9.5f), modifier = Modifier.widthIn(min = 72.dp))
        if (enabled) {
            Slider(
                value = local, onValueChange = { local = it; onChange(it) },
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.border),
            )
            Text("${(local * 100).toInt()}%", style = nx.label(9.5f, p.text), modifier = Modifier.width(36.dp))
        } else {
            Spacer(Modifier.weight(1f))
            Text("GRANT", style = nx.label(9f, p.accent2, weight = 700),
                modifier = Modifier.clip(NxShapes.chip).clickable(role = Role.Button, onClick = onGrant).padding(8.dp))
        }
    }
}

@Composable
private fun CategoryPanel(cat: HubCategory, rows: List<ResolvedRow>, live: LiveState, a: HubActions) {
    val nx = LocalNexus.current
    val p = nx.palette
    GlassPanel(Modifier.fillMaxWidth(), padding = 10.dp) {
        Column {
            Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(iconFor(cat.id), null, tint = p.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (nx.tech) "${cat.code} // ${cat.title.uppercase()}" else cat.title, style = if (nx.tech) nx.label(10.5f, p.accent) else nx.body(15f, weight = 600, color = p.accent))
            }
            rows.forEach { r -> HubRow(r.entry.title, r.entry.subtitle, r.entry.capability, stateFor(r.entry.id, live)) { a.open(r.intent) } }
        }
    }
}

@Composable
private fun TilesPanel(a: HubActions) {
    val nx = LocalNexus.current
    val p = nx.palette
    GlassPanel(Modifier.fillMaxWidth(), padding = 10.dp) {
        Column {
            Text(if (nx.tech) "QS // NEXUS TILES" else "Quick Settings tiles", style = if (nx.tech) nx.label(10.5f, p.accent) else nx.body(15f, weight = 600, color = p.accent),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            Text("Real tiles in the real Quick Settings panel, drawn in Nothing OS's own tile style.",
                style = nx.body(11f, color = p.textFaint), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            TILE_OFFERS.forEach { t ->
                val label = androidx.compose.ui.res.stringResource(t.label)
                HubRow(label, t.what, Capability.LIVE, "ADD") { a.addTile(t) }
            }
        }
    }
}

@Composable
private fun NothingPanel(rows: List<NothingRow>, a: HubActions) {
    val nx = LocalNexus.current
    val p = nx.palette
    GlassPanel(Modifier.fillMaxWidth(), padding = 10.dp) {
        Column {
            Text(if (nx.tech) "NTH // NOTHING OS" else "Nothing OS", style = if (nx.tech) nx.label(10.5f, p.accent) else nx.body(15f, weight = 600, color = p.accent),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            rows.forEach { r -> HubRow(r.title, r.subtitle, Capability.SYSTEM, null) { a.open(r.intent) } }
        }
    }
}

@Composable
private fun HubRow(title: String, subtitle: String, cap: Capability, state: String?, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().clip(NxShapes.chip)
            .clickable(role = Role.Button, onClickLabel = "Open") { haptic(Haptic.TICK); onClick() }
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = nx.body(15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = nx.body(11.5f, color = p.textDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (state != null) {
            Text(state, style = nx.label(9.5f, if (state == "ON") p.accent else p.textFaint, weight = 600))
            Spacer(Modifier.width(8.dp))
        }
        Badge(cap)
    }
}

private fun stateFor(id: String, l: LiveState): String? = when (id) {
    "wifi_panel" -> if (l.wifi) "ON" else "OFF"
    "bluetooth" -> if (l.bluetooth) "ON" else "OFF"
    "location" -> if (l.location) "ON" else "OFF"
    "dnd" -> if (l.dnd) "ON" else "OFF"
    else -> null
}

private fun iconFor(category: String): ImageVector = when (category) {
    "network" -> Icons.Outlined.Wifi
    "devices" -> Icons.Outlined.Bluetooth
    "display" -> Icons.Outlined.Brightness6
    "sound" -> Icons.AutoMirrored.Outlined.VolumeUp
    "power" -> Icons.Outlined.BatteryFull
    "apps" -> Icons.Outlined.Apps
    "storage" -> Icons.Outlined.Storage
    "security" -> Icons.Outlined.Security
    "access" -> Icons.Outlined.Accessibility
    "system" -> Icons.Outlined.Memory
    else -> Icons.Outlined.Settings
}
