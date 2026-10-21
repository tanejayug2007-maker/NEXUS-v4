package com.nexus.launcher.ui.notifications

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
 * NEXUS notification dashboard — a launcher view of notifications read through the official
 * NotificationListenerService. It does NOT replace the Android notification shade, which
 * stays one tap away ("SYSTEM SHADE").
 */
@Composable
fun NotificationFeed(
    visible: Boolean,
    appForPkg: (String) -> AppInfo?,
    onOpen: (NexusNotification) -> Unit,
    onGrantAccess: () -> Unit,
    onSystemShade: () -> Unit,
    onClose: () -> Unit,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val items by NotificationHub.items.collectAsStateWithLifecycle()
    val connected by NotificationHub.connected.collectAsStateWithLifecycle()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(nx.dur(220))) + slideInVertically(tween(nx.dur((320 / nx.config.notifAnimSpeed).toInt()))) { -it / 3 },
        exit = fadeOut(tween(nx.dur(180))) + slideOutVertically(tween(nx.dur(220))) { -it / 4 },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { onClose() }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onVerticalDrag = { c, d -> total += d; c.consume() }, onDragEnd = { if (total < -80f) onClose() })
                }
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nx.tag(if (nx.tech) "Signals" else "Notifications"), style = nx.label(10f, p.accent))
                        Text(
                            if (connected) "${items.count { !it.ongoing }} incoming" else "Feed offline",
                            style = nx.display(22f, p.text, weight = 300),
                        )
                    }
                    NxButton("System shade", onSystemShade)
                    if (connected && items.any { it.clearable }) {
                        Spacer(Modifier.width(8.dp))
                        NxButton("Clear", { NotificationHub.dismissAll() })
                    }
                }
                if (!connected) {
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
                } else {
                    val groups = items.groupBy { it.pkg }.toList().sortedByDescending { (_, l) -> l.maxOf { it.postTime } }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { },
                    ) {
                        if (groups.isEmpty()) item("empty") {
                            Text("ALL CLEAR · NO SIGNALS", style = nx.label(11f, p.textFaint), modifier = Modifier.padding(20.dp))
                        }
                        items(groups, key = { it.first }) { (pkg, list) ->
                            val open = expanded[pkg] == true
                            GroupCard(
                                app = appForPkg(pkg), label = list.first().appLabel, list = list, open = open,
                                onToggle = { expanded[pkg] = !open }, onOpen = onOpen,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
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
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val shown = if (open) list else list.take(1)
    GlassPanel(modifier.fillMaxWidth().graphicsLayer { alpha = nx.config.notifOpacity.coerceIn(0.3f, 1f) + 0.2f }, padding = 12.dp, brackets = false) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onToggle)) {
                if (app != null) AppIconImage(app, 22f) else Box(Modifier.size(22.dp).clip(NxShapes.chip).background(p.accent.copy(alpha = 0.3f)))
                Spacer(Modifier.width(10.dp))
                Text(label.uppercase(), style = nx.label(10f, p.accent), modifier = Modifier.weight(1f), maxLines = 1)
                Text(ago(list.first().postTime), style = nx.label(9f, p.textFaint))
                if (list.size > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("${list.size}", style = nx.label(10f, p.text))
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = p.textDim, modifier = Modifier.size(18.dp))
                }
            }
            shown.forEach { n -> NotificationRow(n, onOpen) }
        }
    }
}

@Composable
private fun NotificationRow(n: NexusNotification, onOpen: (NexusNotification) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val haptic = rememberHaptics()
    val offset = remember(n.key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
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
            .background(p.accent.copy(alpha = 0.04f))
            .clickable { haptic(Haptic.TICK); onOpen(n) }
            .padding(10.dp)
    ) {
        Column {
            if (nx.config.notifRedact) {
                Text("Content hidden", style = nx.body(13.5f, color = p.textDim))
            } else {
                if (n.title.isNotBlank()) Text(n.title, style = nx.body(14f, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.text.isNotBlank()) Text(n.text, style = nx.body(13f, color = p.textDim), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
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
