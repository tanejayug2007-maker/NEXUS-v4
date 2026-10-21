package com.nexus.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.Predictor
import com.nexus.launcher.core.PrivateLock
import com.nexus.launcher.core.RadialSlots
import com.nexus.launcher.core.RadialSource
import com.nexus.launcher.core.Search
import com.nexus.launcher.nexus
import com.nexus.launcher.system.DeviceAuth
import com.nexus.launcher.ui.components.AppIconImage
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.SectionHeader
import com.nexus.launcher.ui.components.SliderRow
import com.nexus.launcher.ui.components.SwitchRow
import com.nexus.launcher.ui.privatespace.PinPad
import com.nexus.launcher.ui.privatespace.PrivateAppsEditor
import com.nexus.launcher.ui.privatespace.PrivateGateView
import com.nexus.launcher.ui.privatespace.privateReady
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.roundToInt

private typealias StudioUpd = ((NexusConfig) -> NexusConfig) -> Unit

/**
 * Radial launcher: your own apps in fixed slots (the default since milestone 7). Each slot can be
 * assigned, moved up/down or cleared; "Recent" and "Dock" remain as automatic modes.
 */
@Composable
fun RadialSection(cfg: NexusConfig, update: StudioUpd) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val apps by ctx.nexus.publicApps.collectAsState()
    val layout by ctx.nexus.store.layout.collectAsState(null)
    val usage by ctx.nexus.store.usage.collectAsState(emptyMap())
    val byId = remember(apps) { apps.associateBy { it.key.id } }
    var editing by remember { mutableStateOf<Int?>(null) }
    val n = RadialSlots.pinnable(cfg.radialSlots, cfg.radialEditSlot)
    val slots = RadialSlots.normalize(cfg.radialApps, n)
    fun setSlots(next: List<String>) = update { it.copy(radialApps = next, radialSource = RadialSource.CUSTOM, radialPinnedV7 = true) }

    Column {
        SectionHeader("Radial launcher")
        SwitchRow("Enable radial launcher", cfg.radialMenu, { v -> update { it.copy(radialMenu = v) } }, "Long-press empty space, flick toward an app, release. Off = long-press opens the normal menu.")
        if (!cfg.radialMenu) return@Column
        SliderRow("Slots", cfg.radialSlots.toFloat(), 4f..10f, { v -> update { it.copy(radialSlots = v.roundToInt()) } }, { "${it.roundToInt()}" }, steps = 5)
        SliderRow("Size", cfg.radialRadius, 80f..150f, { v -> update { it.copy(radialRadius = v) } }, { "${it.roundToInt()}dp" })
        SwitchRow("Show app name while aiming", cfg.radialLabels, { v -> update { it.copy(radialLabels = v) } })
        SwitchRow("Top slot opens home menu", cfg.radialEditSlot, { v -> update { it.copy(radialEditSlot = v) } })

        Label("Which apps")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(RadialSource.CUSTOM to "My apps (pinned)", RadialSource.PREDICTED to "Recent", RadialSource.DOCK to "Dock").forEach { (src, label) ->
                com.nexus.launcher.ui.components.HoloChip(label, cfg.radialSource == src, { update { it.copy(radialSource = src, radialPinnedV7 = true) } })
            }
        }
        Text(
            when (cfg.radialSource) {
                RadialSource.CUSTOM -> "Your apps stay in their slots until you change them here or via long-press › Add to radial."
                RadialSource.PREDICTED -> "Automatic: apps you use most at this time of day. Changes by itself."
                RadialSource.DOCK -> "Automatic: your dock apps first, then most used."
            },
            style = nx.body(12f, color = p.textDim), modifier = Modifier.padding(top = 8.dp),
        )
        if (cfg.radialSource != RadialSource.CUSTOM) return@Column

        Spacer(Modifier.height(14.dp))
        slots.forEachIndexed { i, id ->
            val app = byId[id]
            SlotRow(
                index = i, app = app, missing = id.isNotEmpty() && app == null,
                selected = editing == i,
                onEdit = { editing = if (editing == i) null else i },
                onUp = { setSlots(RadialSlots.move(slots, n, i, -1)) },
                onDown = { setSlots(RadialSlots.move(slots, n, i, +1)) },
                onClear = { setSlots(RadialSlots.clear(slots, n, i)) },
                first = i == 0, last = i == n - 1,
            )
            if (editing == i) SingleAppPicker(apps, current = id) { picked ->
                setSlots(RadialSlots.assign(slots, n, i, picked)); editing = null
            }
        }
        SwitchRow("Fill empty slots with recent apps", cfg.radialFillEmpty, { v -> update { it.copy(radialFillEmpty = v) } }, "Off = an empty slot stays empty")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            NxButton("Reset to defaults", {
                val dock = layout?.dock.orEmpty().map { it.id }.filter { it in byId }
                val most = Predictor.rank(usage, java.time.LocalTime.now().hour, 12).filter { it in byId }
                setSlots(RadialSlots.defaults(dock, most + apps.map { it.key.id }, n))
            })
            NxButton("Clear all", { setSlots(List(n) { "" }) })
        }
    }
}

@Composable
private fun SlotRow(
    index: Int, app: AppInfo?, missing: Boolean, selected: Boolean,
    onEdit: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onClear: () -> Unit,
    first: Boolean, last: Boolean,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(NxShapes.chip)
            .background(if (selected) p.accent.copy(alpha = 0.12f) else p.panel.copy(alpha = 0.6f))
            .border(1.dp, if (selected) p.accent else p.border, NxShapes.chip)
            .clickable(role = Role.Button, onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(index + 1), style = nx.label(10f, p.accent))
        Spacer(Modifier.width(12.dp))
        if (app != null) AppIconImage(app, 30f) else Box(Modifier.size(30.dp).clip(NxShapes.chip).border(1.dp, p.border, NxShapes.chip))
        Spacer(Modifier.width(12.dp))
        Text(
            when {
                app != null -> app.label
                missing -> "Unavailable (uninstalled or private)"
                else -> "Empty · tap to choose"
            },
            style = nx.body(14f, color = if (app != null) p.text else p.textFaint), modifier = Modifier.weight(1f), maxLines = 1,
        )
        SmallIcon(Icons.Rounded.KeyboardArrowUp, "Move up", enabled = !first, onUp)
        SmallIcon(Icons.Rounded.KeyboardArrowDown, "Move down", enabled = !last, onDown)
        SmallIcon(Icons.Rounded.Close, "Clear slot", enabled = app != null || missing, onClear)
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalNexus.current.palette
    Box(
        Modifier.size(36.dp).clip(NxShapes.chip).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) p.textDim else p.textFaint.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
    }
}

/** Searchable single choice for one slot. */
@Composable
private fun SingleAppPicker(apps: List<AppInfo>, current: String, onPick: (String) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    var q by remember { mutableStateOf("") }
    val list = remember(apps, q) { if (q.isBlank()) apps else Search.rank(apps, q) { it.label } }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(NxShapes.panel).background(p.panel).padding(12.dp)) {
        Box(Modifier.fillMaxWidth().clip(NxShapes.capsule).background(p.bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (q.isEmpty()) Text(nx.caps("Search apps"), style = nx.body(13f, color = p.textFaint))
            BasicTextField(q, { q = it }, singleLine = true, textStyle = nx.body(14f), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
            list.take(80).forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clip(NxShapes.chip).clickable(role = Role.RadioButton) { onPick(a.key.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(a, 30f)
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, style = nx.body(14f), modifier = Modifier.weight(1f))
                    if (a.key.id == current) Text("● CURRENT", style = nx.label(9f, p.accent))
                }
            }
        }
    }
}

/**
 * Private Space settings. Anything that reveals or changes the space (which apps, PIN, lock
 * method, turning it off) is behind the same lock as the space itself.
 */
@Composable
fun PrivateSpaceSection(cfg: NexusConfig, update: StudioUpd, host: StudioHost) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    // leaving Studio (Home, Recents, screen off) locks the management view again
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { open = false }
    Column {
        SectionHeader("Private Space")
        Text(
            "Hide apps from the drawer, search, orbit, radial and home pages. They open only after your PIN or phone lock. " +
                "A launcher-level space: see “What it does not hide” below.",
            style = nx.body(12.5f, color = p.textDim),
        )
        Spacer(Modifier.height(12.dp))
        SwitchRow("Show PRIVATE entry in the drawer", cfg.privateEntryVisible, { v -> update { it.copy(privateEntryVisible = v) } }, "Off: open it by typing “private” in drawer search")
        SwitchRow("Hide their notifications in NEXUS", cfg.privateHideNotifications, { v -> update { it.copy(privateHideNotifications = v) } }, "Signals panel and popups. Android's own shade still shows them.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            NxButton(if (cfg.privateReady()) "Open on home" else "Set up", { host.openInLauncher("private") }, primary = true)
            NxButton(if (open) "Lock settings" else "Manage (unlock)", { open = !open })
        }
        if (open) {
            Spacer(Modifier.height(14.dp))
            PrivateGateView(cfg, update, title = "Private Space settings") {
                PrivateManage(cfg, update, ctx)
            }
        }
        Spacer(Modifier.height(14.dp))
        Label("What it does not hide")
        Text(
            "• The apps stay installed: Android Settings › Apps, the Play Store, Recents and the system share sheet still list them.\n" +
                "• Their notifications still reach Android's own notification shade and lock screen.\n" +
                "• Another launcher would show them. For a separate, encrypted profile use Android's own Private space " +
                "(Settings › Security & privacy, Android 15+), which NEXUS doesn't control.",
            style = nx.body(12f, color = p.textDim),
        )
    }
}

@Composable
private fun PrivateManage(cfg: NexusConfig, update: StudioUpd, ctx: android.content.Context) {
    val nx = LocalNexus.current
    val p = nx.palette
    val priv by ctx.nexus.privateApps.collectAsState()
    val pub by ctx.nexus.publicApps.collectAsState()
    var changingPin by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("Lock method")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.nexus.launcher.ui.components.HoloChip("NEXUS PIN", cfg.privateLock == PrivateLock.PIN, { changingPin = true; first = null; msg = null })
            val device = remember { DeviceAuth.available(ctx) }
            if (device) com.nexus.launcher.ui.components.HoloChip("Phone lock", cfg.privateLock == PrivateLock.DEVICE, {
                update { it.copy(privateLock = PrivateLock.DEVICE, privatePinHash = "") }; changingPin = false
            })
        }
        if (changingPin) {
            PinPad(
                title = if (first == null) "New PIN" else "Repeat new PIN", message = msg, confirmLabel = if (first == null) "Next" else "Save",
                onSubmit = { pin ->
                    when {
                        !com.nexus.launcher.core.PrivatePin.isValidPin(pin) -> msg = "Use 4–12 digits"
                        first == null -> { first = pin; msg = null }
                        first != pin -> { first = null; msg = "Didn't match · start again" }
                        else -> {
                            update { it.copy(privateLock = PrivateLock.PIN, privatePinHash = com.nexus.launcher.core.PrivatePin.hash(pin)) }
                            changingPin = false; first = null; msg = null
                        }
                    }
                    true
                },
            )
        }
        Label("Apps in Private Space · ${priv.size}")
        PrivateAppsEditor(priv, pub, { app, inside ->
            update { c -> c.copy(privateApps = if (inside) c.privateApps + app.key.id else c.privateApps - app.key.id) }
        })
        NxButton("Turn off Private Space (unhide all)", {
            update { it.copy(privateApps = emptySet(), privatePinHash = "", privateLock = PrivateLock.PIN) }
        })
    }
}
