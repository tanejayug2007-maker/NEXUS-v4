package com.nexus.launcher.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.DrawerStyle
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.RadialSlots
import com.nexus.launcher.core.RadialSource
import com.nexus.launcher.nexus
import com.nexus.launcher.ui.components.AppIcon
import com.nexus.launcher.ui.components.AppIconImage
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.fx.DotMatrixText
import com.nexus.launcher.ui.fx.EntranceOverlay
import com.nexus.launcher.ui.fx.LaunchFxEvent
import com.nexus.launcher.ui.fx.LaunchFxOverlay
import com.nexus.launcher.ui.fx.LocalEntrance
import com.nexus.launcher.ui.fx.NexusBackground
import com.nexus.launcher.ui.fx.entrance
import com.nexus.launcher.ui.theme.LocalNexus
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the live preview shows. */
enum class PreviewTab(val label: String) { HOME("Home"), DRAWER("Drawer"), RADIAL("Radial"), SIGNALS("Signals") }

/** The preview that best shows the settings section currently at the top of Studio. */
fun previewTabFor(sectionKey: String?): PreviewTab = when (sectionKey) {
    "radial" -> PreviewTab.RADIAL
    "drawer", "icons", "private" -> PreviewTab.DRAWER
    "signals", "control" -> PreviewTab.SIGNALS
    else -> PreviewTab.HOME
}

/**
 * Studio's live preview. It renders with the same theme objects as the launcher, so every change
 * (colour, icon shape, size, fonts, radial apps…) shows the moment it is made, flagged by a brief
 * "UPDATED" pulse. It follows the section being edited unless a tab is picked by hand.
 */
@Composable
fun StudioPreview(cfg: NexusConfig, autoTab: PreviewTab, onCharge: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    var manual by remember { mutableStateOf<PreviewTab?>(null) }
    LaunchedEffect(autoTab) { manual = null } // scrolling to another section hands control back
    val tab = manual ?: autoTab
    var compact by remember { mutableStateOf(false) }

    // "UPDATED" pulse whenever the config changes (not on first show)
    val pulse = remember { Animatable(0f) }
    var seen by remember { mutableStateOf<NexusConfig?>(null) }
    LaunchedEffect(cfg) {
        if (seen != null && seen != cfg) { pulse.snapTo(1f); pulse.animateTo(0f, tween(nx.dur(900))) }
        seen = cfg
    }

    GlassPanel(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(tween(nx.dur(220)))
            .drawWithContent {
                drawContent()
                val k = pulse.value
                if (k > 0.01f) drawOutline(NxShapes.panel.createOutline(size, layoutDirection, this), p.accent, alpha = 0.9f * k, style = Stroke(2.dp.toPx()))
            },
        padding = 0.dp, highlight = true,
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewTab.entries.forEach { t ->
                        val on = t == tab
                        Box(
                            Modifier.clip(NxShapes.chip).background(if (on) p.accent.copy(alpha = 0.18f) else p.bg.copy(alpha = 0.0f))
                                .border(1.dp, if (on) p.accent else p.border, NxShapes.chip)
                                .clickable(role = Role.Tab) { manual = t; compact = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) { Text(nx.caps(t.label), style = nx.label(9f, if (on) p.text else p.textDim)) }
                    }
                }
                Text(if (pulse.value > 0.05f) "UPDATED" else if (manual == null) "LIVE · AUTO" else "LIVE", style = nx.label(8.5f, p.accent))
                Box(Modifier.size(36.dp).clip(NxShapes.chip).clickable(role = Role.Button, onClickLabel = if (compact) "Expand preview" else "Collapse preview") { compact = !compact }, contentAlignment = Alignment.Center) {
                    Icon(if (compact) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess, null, tint = p.textDim, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(if (compact) 104.dp else 236.dp).padding(8.dp).clip(NxShapes.panel)) {
                NexusBackground(animate = true, modifier = Modifier.fillMaxSize())
                when (tab) {
                    PreviewTab.HOME -> HomePreview(compact, onCharge)
                    PreviewTab.DRAWER -> DrawerPreview(cfg, compact)
                    PreviewTab.RADIAL -> RadialPreview(cfg)
                    PreviewTab.SIGNALS -> SignalsPreview(cfg, compact)
                }
            }
        }
    }
}

@Composable
private fun previewApps(): List<AppInfo> {
    val apps by LocalContext.current.nexus.publicApps.collectAsStateWithLifecycle()
    return apps
}

@Composable
private fun HomePreview(compact: Boolean, onCharge: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val apps = previewApps()
    val entrance = remember { mutableFloatStateOf(1f) }
    var unlockTick by remember { mutableLongStateOf(0L) }
    var fx by remember { mutableStateOf<LaunchFxEvent?>(null) }
    var center by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(unlockTick) {
        if (unlockTick > 0) animate(0f, 1f, animationSpec = tween(nx.dur(1100), easing = LinearEasing)) { v, _ -> entrance.floatValue = v }
    }
    CompositionLocalProvider(LocalEntrance provides entrance) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().onSizeChanged { center = Offset(it.width / 2f, it.height / 2f) }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
                    val now by com.nexus.launcher.ui.home.rememberNow(false)
                    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
                    val time = now.format(java.time.format.DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "hh:mm"))
                    if (nx.matrixClock) DotMatrixText(time, pitch = if (compact) 3.dp else 4.dp, color = p.text)
                    else Text(time, style = nx.display(if (compact) 24f else 34f, p.text, weight = 300))
                    Spacer(Modifier.weight(1f))
                    if (compact) apps.take(3).forEach { a -> AppIconImage(a, nx.config.iconSize * 0.6f, Modifier.padding(start = 6.dp)) }
                }
                if (!compact) {
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
            }
            EntranceOverlay(entrance)
            LaunchFxOverlay(fx) { fx = null }
        }
    }
}

@Composable
private fun DrawerPreview(cfg: NexusConfig, compact: Boolean) {
    val nx = LocalNexus.current
    val p = nx.palette
    val apps = previewApps()
    Column(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.6f)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nx.caps("Drawer · ${cfg.drawerStyle.name.lowercase()}"), style = nx.label(9f, p.accent), modifier = Modifier.weight(1f))
            if (cfg.privateEntryVisible && cfg.privateApps.isNotEmpty()) Text("◈ PRIVATE", style = nx.label(8.5f, p.accent2))
        }
        Spacer(Modifier.height(10.dp))
        val rows = if (compact) 1 else 2
        when (cfg.drawerStyle) {
            DrawerStyle.LIST, DrawerStyle.TERMINAL -> apps.take(rows * 2).forEach { a ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(a, 26f); Spacer(Modifier.width(10.dp))
                    Text(if (cfg.drawerStyle == DrawerStyle.TERMINAL) "> " + a.label.lowercase() else a.label, style = nx.body(13f), maxLines = 1)
                }
            }
            else -> {
                val cols = cfg.drawerCols.coerceIn(3, 6)
                apps.take(cols * rows).chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { a -> AppIcon(a, a.label, nx.config.iconSize * 0.72f, showLabel = !compact, badge = 0) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadialPreview(cfg: NexusConfig) {
    val nx = LocalNexus.current
    val p = nx.palette
    val apps = previewApps()
    val byId = remember(apps) { apps.associateBy { it.key.id } }
    val ringSize = cfg.radialSlots.coerceIn(4, 10)
    val ring: List<AppInfo?> = remember(cfg.radialApps, cfg.radialSource, ringSize, cfg.radialEditSlot, cfg.radialFillEmpty, apps) {
        if (cfg.radialSource == RadialSource.CUSTOM) {
            RadialSlots.resolve(cfg.radialApps, ringSize, cfg.radialEditSlot, { byId[it] }, apps, cfg.radialFillEmpty) { it.key.id }
        } else {
            val auto = apps.take(ringSize)
            if (cfg.radialEditSlot) listOf<AppInfo?>(null) + auto.take(ringSize - 1) else auto
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val c = Offset(w / 2f, h / 2f)
        val r = minOf(w, h) * 0.36f
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(p.accent, r, c, alpha = 0.10f, style = Stroke(r * 0.42f))
            drawCircle(p.accent, r, c, alpha = 0.6f, style = Stroke(1.5f))
            drawCircle(p.accent2, 6f, c)
        }
        val iconDp = 30f
        val half = with(androidx.compose.ui.platform.LocalDensity.current) { (iconDp / 2f).dp.toPx() }
        ring.forEachIndexed { i, app ->
            val a = Math.toRadians((-90.0 + i * 360.0 / ring.size.coerceAtLeast(1)))
            Box(Modifier.offset { IntOffset((c.x + r * cos(a).toFloat() - half).roundToInt(), (c.y + r * sin(a).toFloat() - half).roundToInt()) }.size(iconDp.dp), contentAlignment = Alignment.Center) {
                when {
                    i == 0 && cfg.radialEditSlot -> Text("EDIT", style = nx.label(7.5f, p.accent))
                    app != null -> AppIconImage(app, iconDp)
                    else -> Box(Modifier.size(iconDp.dp).clip(NxShapes.chip).border(1.dp, p.border, NxShapes.chip))
                }
            }
        }
        Text(
            if (cfg.radialSource == RadialSource.CUSTOM) "PINNED · ${ring.count { it != null }} APPS" else "AUTO · ${cfg.radialSource.name}",
            style = nx.label(8.5f, p.textDim), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

@Composable
private fun SignalsPreview(cfg: NexusConfig, compact: Boolean) {
    val nx = LocalNexus.current
    val p = nx.palette
    val apps = previewApps()
    Column(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.7f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // media card (sample)
        GlassPanel(Modifier.fillMaxWidth(), highlight = true, padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(NxShapes.chip).background(p.accent.copy(alpha = 0.2f)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("SAMPLE · PLAYER", style = nx.label(8f, p.accent))
                    Text("Song title", style = nx.body(12.5f, weight = 600), maxLines = 1)
                }
                Icon(Icons.Rounded.SkipPrevious, null, tint = p.text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Pause, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.SkipNext, null, tint = p.text, modifier = Modifier.size(18.dp))
            }
        }
        // same opacity rule as the real notification cards, so the Card opacity slider shows here
        if (!compact) GlassPanel(
            Modifier.fillMaxWidth().graphicsLayer { alpha = (cfg.notifOpacity.coerceIn(0.3f, 1f) + 0.2f).coerceAtMost(1f) },
            padding = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    apps.firstOrNull()?.let { AppIconImage(it, 18f) }
                    Spacer(Modifier.width(8.dp))
                    Text("SAMPLE · MESSAGES", style = nx.label(8.5f, p.accent), modifier = Modifier.weight(1f))
                    Text("NOW", style = nx.label(8f, p.textFaint))
                }
                Text(if (cfg.notifRedact) "Content hidden" else "Alex: see you at 7?", style = nx.body(12.5f, color = p.textDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("↩ REPLY", "✓ MARK AS READ", "◌ MUTE").forEach {
                        Text(it, style = nx.label(8f, p.text), modifier = Modifier.clip(NxShapes.chip).border(1.dp, p.accent.copy(alpha = 0.6f), NxShapes.chip).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}
