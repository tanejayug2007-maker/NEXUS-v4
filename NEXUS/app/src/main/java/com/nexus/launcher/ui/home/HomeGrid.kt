package com.nexus.launcher.ui.home

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.HomeItem
import com.nexus.launcher.core.HomePage
import com.nexus.launcher.core.ItemType
import com.nexus.launcher.ui.components.AppIcon
import com.nexus.launcher.ui.components.FolderIcon
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.longPressTrack
import com.nexus.launcher.ui.components.nexusItemGestures
import com.nexus.launcher.ui.components.rememberBounds
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.components.trackBounds
import com.nexus.launcher.ui.fx.entrance
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

/** Callbacks a grid page needs; implemented by HomeRoot. */
class GridCallbacks(
    val labelOf: (AppInfo) -> String,
    val launch: (AppInfo, Rect) -> Unit,
    val openFolder: (HomeItem, Rect) -> Unit,
    val nexusTap: (HomeItem) -> Unit,
    val menu: (HomeItem, Rect) -> Unit,
    val drop: (HomeItem, Int, Int) -> Unit,
    val radialStart: (Offset) -> Unit,
    val radialMove: (Offset) -> Unit,
    val radialEnd: (Offset) -> Unit,
    val placeAt: (Int, Int) -> Unit,
    val widgetView: (Int) -> View,
    val widgetSize: (Int, Int, Int) -> Unit,
    val cellMeasured: (IntSize) -> Unit,
)

@Composable
fun GridPage(
    page: HomePage,
    cols: Int,
    rows: Int,
    apps: Map<String, AppInfo>,
    badges: Map<String, Int>,
    placingId: String?,
    locked: Boolean,
    cb: GridCallbacks,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val density = LocalDensity.current
    val haptic = rememberHaptics()

    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        val cellW = constraints.maxWidth / cols.toFloat()
        val cellH = constraints.maxHeight / rows.toFloat()
        val cellWdp = with(density) { cellW.toDp() }
        val cellHdp = with(density) { cellH.toDp() }
        LaunchedEffect(cellW, cellH) { cb.cellMeasured(IntSize(cellW.toInt(), cellH.toInt())) }

        var dragId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }

        // Empty-space long press (a sibling beneath the items, so icons never trigger it).
        val bg = rememberBounds()
        Box(
            Modifier.fillMaxSize().trackBounds(bg).longPressTrack(
                bg,
                onStart = { o -> haptic(Haptic.PRESS); cb.radialStart(o) },
                onMove = { o -> cb.radialMove(o) },
                onEnd = { o -> cb.radialEnd(o) },
            )
        )

        // Drop target highlight while dragging
        val dragged = page.items.firstOrNull { it.id == dragId }
        if (dragged != null) {
            Canvas(Modifier.fillMaxSize()) {
                val tx = targetCell(dragged.x, dragged.w, cellW, dragOffset.x, cols)
                val ty = targetCell(dragged.y, dragged.h, cellH, dragOffset.y, rows)
                drawRoundRect(
                    nx.palette.accent, Offset(tx * cellW + 4f, ty * cellH + 4f), Size(dragged.w * cellW - 8f, dragged.h * cellH - 8f),
                    CornerRadius(14f), alpha = 0.6f,
                    style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                )
            }
        }

        page.items.forEachIndexed { index, item ->
            key(item.id) {
                val target = IntOffset((item.x * cellW).roundToInt(), (item.y * cellH).roundToInt())
                val pos = remember { Animatable(target, IntOffset.VectorConverter) }
                val targetNow by rememberUpdatedState(target)
                val scope = rememberCoroutineScope()
                LaunchedEffect(target) { pos.animateTo(target, spring(dampingRatio = 0.75f, stiffness = 380f)) }
                val bounds = rememberBounds()
                val isDragging = dragId == item.id

                Box(
                    Modifier
                        .offset { if (isDragging) pos.value + IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) else pos.value }
                        .size(cellWdp * item.w, cellHdp * item.h)
                        .zIndex(if (isDragging) 10f else 0f)
                        .graphicsLayer {
                            val s = if (isDragging) 1.1f else 1f
                            scaleX = s; scaleY = s
                        }
                        .entrance(index + 2)
                        .trackBounds(bounds)
                        .then(
                            if (item.type == ItemType.WIDGET) Modifier else Modifier.nexusItemGestures(
                                key = item.id,
                                dragEnabled = !locked,
                                onTap = {
                                    when (item.type) {
                                        ItemType.APP -> item.app?.let { k -> apps[k.id]?.let { cb.launch(it, bounds.rect) } }
                                        ItemType.FOLDER -> cb.openFolder(item, bounds.rect)
                                        ItemType.WIDGET -> Unit
                                        ItemType.NEXUS -> cb.nexusTap(item)
                                    }
                                },
                                onLongPressStart = { haptic(Haptic.PRESS) },
                                onLongPress = { cb.menu(item, bounds.rect) },
                                onDragStart = { dragId = item.id; dragOffset = Offset.Zero },
                                onDrag = { d -> dragOffset += d },
                                onDragEnd = { total ->
                                    val tx = targetCell(item.x, item.w, cellW, total.x, cols)
                                    val ty = targetCell(item.y, item.h, cellH, total.y, rows)
                                    scope.launch {
                                        pos.snapTo(pos.value + IntOffset(total.x.roundToInt(), total.y.roundToInt()))
                                        pos.animateTo(targetNow, spring(dampingRatio = 0.75f, stiffness = 380f))
                                    }
                                    dragId = null; dragOffset = Offset.Zero
                                    haptic(Haptic.CONFIRM)
                                    cb.drop(item, tx, ty)
                                },
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (item.type) {
                        ItemType.APP -> {
                            val app = item.app?.let { apps[it.id] }
                            if (app != null) {
                                AppIcon(
                                    app = app, label = cb.labelOf(app), sizeDp = nx.config.iconSize,
                                    showLabel = nx.config.showLabels, badge = badges[app.component.packageName] ?: 0,
                                )
                            }
                        }
                        ItemType.FOLDER -> {
                            FolderIcon(
                                apps = item.apps.mapNotNull { apps[it.id] }, title = item.title,
                                sizeDp = nx.config.iconSize, showLabel = nx.config.showLabels,
                            )
                        }
                        ItemType.NEXUS -> {
                            val kind = runCatching { com.nexus.launcher.core.NexusWidgetKind.valueOf(item.provider) }.getOrNull()
                            if (kind != null) com.nexus.launcher.ui.widgets.NexusModule(kind, item.title.substringBefore('|').toIntOrNull() ?: 0, Modifier.padding(4.dp), frame = item.title.substringAfter('|', "").toIntOrNull()?.let { com.nexus.launcher.core.WidgetFrame.entries.getOrNull(it) }, data = item.data)
                        }
                        ItemType.WIDGET -> WidgetCell(item, cellWdp.value * item.w, cellHdp.value * item.h, cb)
                    }
                }
            }
        }

        // Placement mode: tap any cell to move the selected item there.
        if (placingId != null) {
            Canvas(Modifier.fillMaxSize()) {
                for (y in 0 until rows) for (x in 0 until cols) {
                    drawRoundRect(
                        nx.palette.accent, Offset(x * cellW + 6f, y * cellH + 6f), Size(cellW - 12f, cellH - 12f),
                        CornerRadius(12f), alpha = 0.35f, style = Stroke(width = 1.5f),
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().pointerInput(cols, rows) {
                    detectTapGestures { o ->
                        haptic(Haptic.CONFIRM)
                        cb.placeAt(floor(o.x / cellW).toInt().coerceIn(0, cols - 1), floor(o.y / cellH).toInt().coerceIn(0, rows - 1))
                    }
                }
            )
        }
    }
}

private fun targetCell(start: Int, span: Int, cell: Float, delta: Float, max: Int): Int {
    val centerPx = (start + span / 2f) * cell + delta
    val center = floor(centerPx / cell).toInt()
    return (center - span / 2).coerceIn(0, (max - span).coerceAtLeast(0))
}

@Composable
private fun WidgetCell(item: HomeItem, widthDp: Float, heightDp: Float, cb: GridCallbacks) {
    val nx = LocalNexus.current
    LaunchedEffect(item.widgetId, widthDp.toInt(), heightDp.toInt()) {
        cb.widgetSize(item.widgetId, widthDp.toInt(), heightDp.toInt())
    }
    GlassPanel(Modifier.fillMaxSize().padding(4.dp), shape = NxShapes.tile, padding = 2.dp, brackets = false) {
        Box(Modifier.fillMaxSize().clip(NxShapes.tile).background(nx.palette.bg.copy(alpha = 0.05f))) {
            AndroidView(factory = { cb.widgetView(item.widgetId) }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun EmptyPageHint(modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("LONG-PRESS TO ADD WIDGETS · DRAG APPS HERE", style = nx.label(9.5f, nx.palette.textFaint))
    }
}
