package com.nexus.launcher.ui.notifications

import com.nexus.launcher.system.SystemControls
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.notifications.NexusNotification
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.ui.components.AppIconImage
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * NEXUS notification panel — the Control Deck design applied to notifications. A launcher view of
 * notifications read through the official NotificationListenerService. It does NOT replace the
 * Android notification shade, which stays one tap away ("SYSTEM SHADE").
 */
@Composable
fun NotificationFeed(
    visible: Boolean,
    appForPkg: (String) -> AppInfo?,
    onOpen: (NexusNotification) -> Unit,
    onGrantAccess: () -> Unit,
    onSystemShade: () -> Unit,
    onClose: () -> Unit,
    battery: com.nexus.launcher.core.BatteryState = com.nexus.launcher.core.BatteryState(),
    torch: com.nexus.launcher.system.TorchController? = null,
    onOpenDeck: () -> Unit = {},
    /** Packages in the Private Space whose notifications stay out of this panel. */
    hiddenPkgs: Set<String> = emptySet(),
    onOpenApp: (String) -> Unit = {},
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val all by NotificationHub.items.collectAsStateWithLifecycle()
    val items = remember(all, hiddenPkgs) { if (hiddenPkgs.isEmpty()) all else all.filterNot { it.pkg in hiddenPkgs } }
    val connected by NotificationHub.connected.collectAsStateWithLifecycle()
    val media by NotificationHub.media.collectAsStateWithLifecycle()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(nx.dur(220))) + slideInVertically(tween(nx.dur((320 / nx.config.notifAnimSpeed).toInt()), easing = FastOutSlowInEasing)) { -it / 8 },
        exit = fadeOut(tween(nx.dur(180))) + slideOutVertically(tween(nx.dur(220))) { -it / 12 },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(p.bg)
                .clickable(remember { MutableInteractionSource() }, indication = null) { onClose() }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onVerticalDrag = { c, d -> total += d; c.consume() }, onDragEnd = { if (total < -80f) onClose() })
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // The player's own media notification is replaced by the one media card (no duplicate song).
            val m = media?.takeIf { it.pkg !in hiddenPkgs }
            val mediaNotif = m?.let { mm -> items.firstOrNull { it.media && it.pkg == mm.pkg } }
            val groups = items.filterNot { m != null && it.media && it.pkg == m.pkg }
                .groupBy { it.pkg }.toList().sortedByDescending { (_, l) -> l.maxOf { it.postTime } }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
                modifier = Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { },
            ) {
                item("header") {
                    com.nexus.launcher.ui.control.DeckHeader(if (nx.tech) "Signals" else "Notifications", battery)
                }
                item("bar") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (!connected) (if (nx.tech) "FEED OFFLINE" else "Feed offline")
                            else "${items.count { !it.ongoing }} ${if (nx.tech) "INCOMING" else "incoming"}",
                            style = nx.label(10f, p.textDim), modifier = Modifier.weight(1f),
                        )
                        NxButton("System shade", onSystemShade)
                        if (connected && items.any { it.clearable }) {
                            Spacer(Modifier.width(8.dp))
                            NxButton("Clear", { NotificationHub.dismissAll() })
                        }
                    }
                }
                item("quick") { QuickStrip(torch, onOpenDeck) }
                if (!connected) {
                    item("grant") {
                        GlassPanel(Modifier.fillMaxWidth(), highlight = true) {
                            Column {
                                Text("Notification access is off", style = nx.body(15f))
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Turn on “NEXUS notification feed” to see notifications, badges and media controls here. " +
                                        "Everything stays on this phone.",
                                    style = nx.body(12.5f, color = p.textDim),
                                )
                                Spacer(Modifier.height(10.dp))
                                NxButton("Grant access", onGrantAccess, primary = true)
                            }
                        }
                    }
                } else {
                    if (m != null) item("media") {
                        MediaCard(
                            m, appForPkg(m.pkg),
                            onOpen = { if (mediaNotif != null) onOpen(mediaNotif) else { onClose(); onOpenApp(m.pkg) } },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (groups.isEmpty() && m == null) item("empty") {
                        Text(if (nx.tech) "ALL CLEAR · NO SIGNALS" else "No notifications", style = nx.label(11f, p.textFaint), modifier = Modifier.padding(20.dp))
                    }
                    items(groups, key = { it.first }) { (pkg, list) ->
                        val open = expanded[pkg] == true
                        GroupCard(
                            app = appForPkg(pkg), label = list.first().appLabel, list = list, open = open,
                            onToggle = { expanded[pkg] = !open }, onOpen = onOpen, onLeave = onClose,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item("foot") {
                    Text(
                        if (nx.tech) "NEXUS VIEW · ANDROID'S SHADE IS UNCHANGED · SWIPE UP TO CLOSE" else "NEXUS view · Android's shade is unchanged · swipe up to close",
                        style = nx.label(8.5f, p.textFaint), modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Compact Control Deck tiles at the top of the notification panel. Real toggles / official panels only. */
@Composable
private fun QuickStrip(torch: com.nexus.launcher.system.TorchController?, onOpenDeck: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = rememberHaptics()
    var tick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { tick++ }
    val wifi = remember(tick) { SystemControls.wifiEnabled(ctx) }
    val bt = remember(tick) { SystemControls.bluetoothEnabled(ctx) }
    val dnd = remember(tick) { SystemControls.dndOn(ctx) }
    val torchOn = torch?.on?.collectAsStateWithLifecycle()?.value == true
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniTile(Icons.Rounded.Wifi, "Wi-Fi", wifi, panel = true, Modifier.weight(1f)) { SystemControls.openWifi(ctx) }
        MiniTile(Icons.Rounded.Bluetooth, "BT", bt, panel = true, Modifier.weight(1f)) { SystemControls.openBluetooth(ctx) }
        MiniTile(Icons.Rounded.FlashlightOn, "Torch", torchOn, panel = false, Modifier.weight(1f)) { haptic(Haptic.TICK); torch?.toggle() }
        MiniTile(Icons.Rounded.DoNotDisturbOn, "DND", dnd, panel = false, Modifier.weight(1f)) { haptic(Haptic.TICK); SystemControls.toggleDnd(ctx); tick++ }
        MiniTile(Icons.Rounded.Tune, "Deck", false, panel = false, Modifier.weight(1f)) { onOpenDeck() }
    }
}

@Composable
private fun MiniTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, panel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val energy by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, tween(nx.dur(320)), label = "mini")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier
            .height(58.dp)
            .graphicsLayer { val sc = if (pressed) 0.95f else 1f; scaleX = sc; scaleY = sc }
            .clip(NxShapes.tile)
            .background(Brush.linearGradient(listOf(p.panelTop, p.panel)))
            .drawBehind { if (energy > 0.01f) drawRect(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.30f * energy), p.accent.copy(alpha = 0.08f * energy)))) }
            .border(1.dp, androidx.compose.ui.graphics.lerp(p.border, p.accent.copy(alpha = 0.8f), energy), NxShapes.tile)
            .clickable(source, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label ${if (active) "on" else "off"}" }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = androidx.compose.ui.graphics.lerp(p.textDim, p.accent, energy), modifier = Modifier.size(18.dp))
        Text((if (panel) "◇ " else "") + nx.caps(label), style = nx.label(7.5f, if (active) p.accent else p.textFaint), maxLines = 1)
    }
}

@Composable
private fun GroupCard(
    app: AppInfo?,
    label: String,
    list: List<NexusNotification>,
    open: Boolean,
    onToggle: () -> Unit,
    onOpen: (NexusNotification) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val shown = if (open) list else list.take(1)
    // Arrival pulse: a fresh notification lights the card edge once (skipped with reduced motion).
    val newest = list.maxOf { it.postTime }
    val pulse = remember(list.first().key) { Animatable(if (System.currentTimeMillis() - newest < 5000 && nx.quality.tier > 0) 1f else 0f) }
    LaunchedEffect(list.first().key) { if (pulse.value > 0f) pulse.animateTo(0f, tween(nx.dur(900))) }
    GlassPanel(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = (nx.config.notifOpacity.coerceIn(0.3f, 1f) + 0.2f).coerceAtMost(1f) }
            .drawWithContent {
                drawContent()
                // cyan edge bar on the left, like the deck's active tiles
                drawRect(p.accent, Offset(0f, 14.dp.toPx()), Size(2.dp.toPx(), size.height - 28.dp.toPx()), alpha = 0.75f)
                val k = pulse.value
                if (k > 0.01f) drawOutline(NxShapes.panel.createOutline(size, layoutDirection, this), p.accent, alpha = 0.8f * k, style = Stroke(2.dp.toPx()))
            }
            .animateContentSize(tween(nx.dur(240))),
        padding = 12.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onToggle)) {
                if (app != null) AppIconImage(app, 24f) else Box(Modifier.size(24.dp).clip(NxShapes.chip).background(p.accent.copy(alpha = 0.3f)))
                Spacer(Modifier.width(10.dp))
                Text(label.uppercase(), style = nx.label(10f, p.accent), modifier = Modifier.weight(1f), maxLines = 1)
                Text(ago(list.first().postTime), style = nx.label(9f, p.textFaint))
                if (list.size > 1) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.border(1.dp, p.accent2.copy(alpha = 0.6f), NxShapes.chip).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("${list.size}", style = nx.label(9f, p.accent2))
                    }
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Collapse" else "Expand", tint = p.textDim, modifier = Modifier.size(18.dp))
                }
            }
            shown.forEach { n -> key(n.key) { NotificationRow(n, onOpen, onLeave) } }
        }
    }
}

@Composable
private fun NotificationRow(n: NexusNotification, onOpen: (NexusNotification) -> Unit, onLeave: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val offset = remember(n.key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var more by remember(n.key) { mutableStateOf(false) }
    var canExpand by remember(n.key) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .graphicsLayer { translationX = offset.value; alpha = 1f - (abs(offset.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.9f) }
            .pointerInput(n.key, n.clearable) {
                if (!n.clearable) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { c, d -> c.consume(); scope.launch { offset.snapTo(offset.value + d) } },
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > size.width * 0.38f) {
                                haptic(Haptic.CONFIRM)
                                offset.animateTo(if (offset.value > 0) size.width * 1.2f else -size.width * 1.2f, tween(160))
                                NotificationHub.dismiss(n.key)
                            } else offset.animateTo(0f)
                        }
                    },
                )
            }
            .clip(NxShapes.chip)
            .background(p.accent.copy(alpha = 0.05f))
            .clickable { haptic(Haptic.TICK); onOpen(n) }
            .padding(10.dp)
    ) {
        Column {
            if (nx.config.notifRedact) {
                Text("Content hidden", style = nx.body(13.5f, color = p.textDim))
            } else {
                if (n.title.isNotBlank()) Text(n.title, style = nx.body(14f, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.text.isNotBlank()) Text(
                    n.text, style = nx.body(13f, color = p.textDim), maxLines = if (more) 12 else 2, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) canExpand = true },
                    modifier = Modifier.animateContentSize(tween(nx.dur(220))),
                )
            }
            // Messaging cards and anything with the app's own actions expand to reply / read / mute.
            val actionable = n.messaging || n.actions.isNotEmpty()
            if (canExpand || more || actionable) Text(
                when {
                    more -> nx.caps("▴ less")
                    n.messaging && n.actions.any { it.kind == com.nexus.launcher.notifications.ActionKind.REPLY } -> nx.caps("▾ reply · actions")
                    actionable -> nx.caps("▾ actions")
                    else -> nx.caps("▾ more")
                },
                style = nx.label(8.5f, p.accent),
                modifier = Modifier.padding(top = 6.dp).clip(NxShapes.chip).clickable { more = !more }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            if (more && actionable) SignalActions(n, onLeave)
        }
    }
}

/** Launcher-controlled popup preview shown at the top while NEXUS is visible. */
@Composable
fun NotificationPopup(n: NexusNotification?, appForPkg: (String) -> AppInfo?, onOpen: (NexusNotification) -> Unit, onDismiss: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    AnimatedVisibility(
        n != null,
        enter = slideInVertically(tween(nx.dur((260 / nx.config.notifAnimSpeed).toInt()))) { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val last = remember { arrayOfNulls<NexusNotification>(1) }
        val shown = n ?: last[0] ?: return@AnimatedVisibility
        last[0] = shown
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            GlassPanel(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(shown.key) { detectVerticalDragGestures { c, d -> c.consume(); if (d < -6f) onDismiss() } }
                    .clickable { onOpen(shown) },
                highlight = true, padding = 12.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val app = appForPkg(shown.pkg)
                    if (app != null) AppIconImage(app, 30f)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shown.appLabel.uppercase(), style = nx.label(9f, p.accent))
                        if (nx.config.notifRedact) Text("New notification", style = nx.body(14f))
                        else {
                            Text(shown.title.ifBlank { shown.text }, style = nx.body(14f, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (shown.title.isNotBlank() && shown.text.isNotBlank()) Text(shown.text, style = nx.body(12.5f, color = p.textDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun ago(t: Long): String =
    DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString().uppercase()
