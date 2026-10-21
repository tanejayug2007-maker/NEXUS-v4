package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.launcher.nexus
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ProviderRow(val info: AppWidgetProviderInfo, val label: String, val app: String)

@Composable
fun WidgetPicker(
    onNexus: (com.nexus.launcher.core.NexusWidgetKind, Int) -> Unit,
    providers: () -> List<AppWidgetProviderInfo>,
    cellWidthPx: Int,
    cellHeightPx: Int,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onClose: () -> Unit,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val rows by produceState(emptyList<ProviderRow>()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            providers().map { info ->
                val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                    .getOrDefault(info.provider.packageName)
                ProviderRow(info, runCatching { info.loadLabel(pm) }.getOrNull() ?: info.provider.className.substringAfterLast('.'), appLabel)
            }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
        }
    }
    val filtered = remember(rows, query) {
        if (query.isBlank()) rows else rows.filter { it.label.contains(query, true) || it.app.contains(query, true) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg.copy(alpha = 0.92f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(nx.tag(if (nx.tech) "Widget bay" else "Widgets"), style = nx.label(10f, p.accent))
                    Text("Add a module", style = nx.display(22f, p.text, weight = 300))
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = p.text) }
            }
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(NxShapes.capsule).background(p.panel).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) Text("FILTER ${rows.size} WIDGETS", style = nx.label(10.5f, p.textFaint))
                BasicTextField(query, { query = it }, singleLine = true, textStyle = nx.body(14f), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (query.isBlank()) {
                    item("nx-h") { Text(nx.caps("NEXUS widgets · many styles each"), style = nx.label(9.5f, p.accent), modifier = Modifier.padding(vertical = 8.dp)) }
                    items(com.nexus.launcher.core.NexusWidgetKind.entries, key = { "nx-" + it.name }) { kind ->
                        NexusKindRow(kind) { style -> onNexus(kind, style) }
                    }
                    item("app-h") { Text(nx.caps("App widgets"), style = nx.label(9.5f, p.accent), modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                }
                items(filtered, key = { it.info.provider.flattenToString() + it.info.profile.hashCode() }) { row ->
                    WidgetRow(row, cellWidthPx, cellHeightPx) { onPick(row.info) }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(row: ProviderRow, cellW: Int, cellH: Int, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, row.info.provider) {
        value = withContext(Dispatchers.IO) {
            val d = runCatching { row.info.loadPreviewImage(ctx, 0) ?: row.info.loadIcon(ctx, 0) }.getOrNull()
            ctx.nexus.icons.plain(d, 160)
        }
    }
    val (sx, sy) = spanFor(row.info, cellW, cellH)
    GlassPanel(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick), brackets = false, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(NxShapes.chip).background(p.accent.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                preview?.let { Image(it, null, Modifier.size(60.dp), contentScale = ContentScale.Fit) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.label, style = nx.body(14f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.app, style = nx.body(12f, color = p.textDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${sx}×${sy}", style = nx.label(11f, p.accent))
        }
    }
}

/** Cells a widget needs. Uses Android 12 target cells when the widget declares them. */
fun spanFor(info: AppWidgetProviderInfo, cellW: Int, cellH: Int): Pair<Int, Int> {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        return info.targetCellWidth to info.targetCellHeight
    }
    val w = if (cellW > 0) ((info.minWidth + cellW - 1) / cellW).coerceAtLeast(1) else 2
    val h = if (cellH > 0) ((info.minHeight + cellH - 1) / cellH).coerceAtLeast(1) else 1
    return w to h
}


@Composable
private fun NexusKindRow(kind: com.nexus.launcher.core.NexusWidgetKind, onPick: (Int) -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    GlassPanel(Modifier.fillMaxWidth().padding(vertical = 5.dp), brackets = false, padding = 12.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kind.name, style = nx.label(11f, p.text), modifier = Modifier.weight(1f))
                Text("${kind.w}×${kind.h}", style = nx.label(10f, p.accent))
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                items(nexusStyleNames(kind).size) { i ->
                    com.nexus.launcher.ui.components.HoloChip(nexusStyleNames(kind)[i], false, { onPick(i) })
                }
            }
        }
    }
}
