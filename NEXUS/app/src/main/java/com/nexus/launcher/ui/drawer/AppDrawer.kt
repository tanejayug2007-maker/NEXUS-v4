package com.nexus.launcher.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.DrawerStyle
import com.nexus.launcher.core.Search
import com.nexus.launcher.ui.components.AppIcon
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.nexusItemGestures
import com.nexus.launcher.ui.components.rememberBounds
import com.nexus.launcher.ui.components.trackBounds
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.launch

/**
 * App drawer: follows the finger on the way up, blurs the home behind it (API 31+), searches
 * by prefix / word / initials / subsequence, has an A–Z rail and closes by dragging down.
 */
@Composable
fun AppDrawer(
    progress: DrawerController,
    open: Boolean,
    focusSearch: Boolean,
    apps: List<AppInfo>,
    hidden: Set<String>,
    badges: Map<String, Int>,
    labelOf: (AppInfo) -> String,
    onLaunch: (AppInfo, Rect) -> Unit,
    onMenu: (AppInfo, Rect) -> Unit,
    onWebSearch: (String) -> Unit,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    /** Show the ◈ PRIVATE chip in the header. */
    privateEntry: Boolean = false,
    /** A Private Space exists: searching "private" offers it even when the chip is hidden. */
    privateSearchable: Boolean = false,
    onPrivate: () -> Unit = {},
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val heightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val travel = DrawerMotion.travelPx(heightPx)
    val isOpen by rememberUpdatedState(open)
    val closeNow by rememberUpdatedState(onClose)
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val grid = rememberLazyGridState()

    // The drawer is composed ahead of time (closed), so only reset after it has really been open.
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) wasOpen = true
        else if (wasOpen) { query = ""; keyboard?.hide(); grid.scrollToItem(0) }
    }
    LaunchedEffect(open, focusSearch) {
        if (open && focusSearch) runCatching { focus.requestFocus(); keyboard?.show() }
    }

    val visibleApps = remember(apps, hidden, query) {
        if (query.isBlank()) apps.filter { it.key.id !in hidden }
        else Search.rank(apps, query) { labelOf(it) }
    }
    val letters = remember(visibleApps, query) {
        if (query.isNotBlank()) emptyList<Pair<Char, Int>>() else visibleApps.mapIndexed { i, a -> labelOf(a).firstOrNull()?.uppercaseChar() to i }
            .filter { it.first != null }.distinctBy { it.first }.map { it.first!! to it.second }
    }

    // Pull-to-close from the top of the list. Deltas here are screen-space; the velocity is
    // tracked from them too, because the list's own tracker sees the panel moving with the
    // finger and reads a fast flick as almost still.
    val closeTracker = remember { VelocityTracker1D(isDataDifferential = false) }
    var closeDrag by remember { mutableFloatStateOf(0f) }
    fun closeDragBy(dy: Float) {
        if (!progress.isDragging) { closeDrag = 0f; closeTracker.resetTracking() }
        closeDrag += dy
        closeTracker.addDataPoint(SystemClock.uptimeMillis(), closeDrag)
        progress.drag(DrawerMotion.progressForDrag(progress.value, dy, travel))
    }
    fun closeRelease(velocityY: Float) {
        val keepOpen = isOpen && DrawerMotion.shouldOpen(progress.value, velocityY, travel, density.density)
        progress.settle(keepOpen, DrawerMotion.progressVelocity(velocityY, travel))
        if (!keepOpen) closeNow()
    }
    val closeConnection = remember(progress, travel) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // list scrolled upwards while the drawer is pulled down: lift the drawer first
                if (available.y < 0f && progress.isDragging && progress.value < 1f && source == NestedScrollSource.UserInput) {
                    closeDragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    closeDragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (progress.isDragging) {
                    closeRelease(closeTracker.calculateVelocity())
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val shown by remember { derivedStateOf { progress.value > 0f } }

    Box(
        Modifier
            .fillMaxSize()
            // Always measured (so the grid is composed before the first swipe), placed only while
            // visible: a closed drawer costs no drawing and never intercepts the home's touches.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { if (shown) placeable.place(0, 0) }
            }
            .pointerInput(open) {
                if (open) detectTapGestures { } // swallow touches so the home below never gets them
                else catchClosingDrawer(progress, travel, onReopen) // a swipe up mid-close reopens it
            }
            .graphicsLayer {
                val v = progress.value
                alpha = (v * 1.4f).coerceIn(0f, 1f)
                translationY = (1f - v) * travel
            }
            .background(p.bg.copy(alpha = 0.82f))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding()
        ) {
            // Header: drag here to close
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(progress, travel) {
                        detectVerticalDragGestures(
                            onDragStart = { closeDrag = 0f; closeTracker.resetTracking() },
                            onVerticalDrag = { ch, dy ->
                                ch.consume()
                                closeDrag += dy
                                closeTracker.addDataPoint(ch.uptimeMillis, closeDrag)
                                progress.drag(DrawerMotion.progressForDrag(progress.value, dy, travel))
                            },
                            onDragEnd = { closeRelease(closeTracker.calculateVelocity()) },
                            onDragCancel = { progress.settle(isOpen) },
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 3.dp).clip(NxShapes.capsule).background(p.accent.copy(alpha = 0.4f)))
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(NxShapes.capsule)
                        .background(p.panel)
                        .border(1.dp, p.border, NxShapes.capsule)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = p.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(nx.caps("Search ${apps.size} apps"), style = nx.label(11f, p.textFaint))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = nx.body(15f),
                            cursorBrush = SolidColor(p.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                val first = visibleApps.firstOrNull()
                                if (first != null) onLaunch(first, Rect.Zero) else if (query.isNotBlank()) onWebSearch(query)
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(Icons.Rounded.Close, "Clear", tint = p.textDim, modifier = Modifier.size(18.dp).clickable { query = "" })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (!nx.tech) (if (query.isBlank()) "${visibleApps.size} apps" else "${visibleApps.size} results") else if (query.isBlank()) "// INDEX · ${visibleApps.size} MODULES" else "// MATCHES · ${visibleApps.size}",
                        style = nx.label(9f, p.textFaint),
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp).weight(1f),
                    )
                    if (privateEntry) PrivateChip(onPrivate)
                }
            }

            val style = nx.config.drawerStyle
            val webRow: @Composable () -> Unit = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clip(NxShapes.chip)
                        .background(p.accent.copy(alpha = 0.08f))
                        .clickable { onWebSearch(query) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Language, null, tint = p.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("SEARCH WEB · “$query”", style = nx.label(10.5f, p.text))
                }
            }
            val cols = when (style) {
                DrawerStyle.LIST, DrawerStyle.TERMINAL -> 1
                else -> nx.config.drawerCols.coerceIn(3, 7)
            }
            val cascade = nx.quality.tier >= 1 // tier 0 = Battery Saver or system animations off
            val cell: @Composable (AppInfo, Int) -> Unit = { app, index ->
                val bounds = rememberBounds()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (cascade) Modifier.drawerCascade(progress, index / cols) else Modifier)
                        .pressFeedback()
                        .trackBounds(bounds)
                        .nexusItemGestures(
                            key = app.key.id,
                            onTap = { onLaunch(app, bounds.rect) },
                            onLongPress = { onMenu(app, bounds.rect) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (style) {
                        DrawerStyle.LIST -> ListRow(app, labelOf(app), badges[app.component.packageName] ?: 0, app.key.id in hidden)
                        DrawerStyle.TERMINAL -> TerminalRow(app, labelOf(app), app.key.id in hidden)
                        else -> AppIcon(
                            app = app, label = labelOf(app), sizeDp = nx.config.iconSize * 0.9f, showLabel = true,
                            badge = badges[app.component.packageName] ?: 0,
                            modifier = Modifier.graphicsLayer { alpha = if (app.key.id in hidden) 0.5f else 1f },
                        )
                    }
                }
            }

            Row(Modifier.weight(1f)) {
                when (style) {
                    DrawerStyle.HONEYCOMB -> Honeycomb(
                        visibleApps, labelOf, badges, onLaunch, onMenu,
                        Modifier.weight(1f).fillMaxHeight().nestedScroll(closeConnection).windowInsetsPadding(WindowInsets.navigationBars),
                    )
                    DrawerStyle.CAROUSEL -> CoverFlow(
                        visibleApps, labelOf, onLaunch, onMenu,
                        Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.navigationBars),
                    )
                    else -> {
                        val categories = style == DrawerStyle.CATEGORIES && query.isBlank()
                        val groups = remember(visibleApps, categories) { if (categories) groupByCategory(visibleApps) else emptyList() }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            state = grid,
                            contentPadding = PaddingValues(start = 8.dp, end = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(if (style == DrawerStyle.TERMINAL) 0.dp else 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .nestedScroll(closeConnection)
                                .windowInsetsPadding(WindowInsets.navigationBars),
                        ) {
                            if (privateSearchable && query.isNotBlank() && "private".startsWith(query.trim().lowercase()) && query.trim().length >= 3) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "private") { PrivateRow(onPrivate) }
                            }
                            if (query.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }, key = "web") { webRow() }
                            if (categories) {
                                groups.forEach { (title, list) ->
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "h:$title") {
                                        Text(nx.tag(title) + " · ${list.size}", style = nx.label(10f, p.accent), modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 2.dp))
                                    }
                                    items(list, key = { "c:" + it.key.id }, contentType = { "app" }) { app -> cell(app, 0) }
                                }
                            } else {
                                itemsIndexed(visibleApps, key = { _, a -> a.key.id }, contentType = { _, _ -> "app" }) { i, app -> cell(app, i) }
                            }
                        }
                        if (letters.size > 6 && !categories) {
                            Column(
                                Modifier.fillMaxHeight().width(22.dp).padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.SpaceEvenly,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                letters.forEach { (ch, index) ->
                                    Text(
                                        ch.toString(),
                                        style = nx.label(9f, p.textDim),
                                        modifier = Modifier.clickable { scope.launch { grid.scrollToItem(index) } },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun PrivateChip(onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(
        Modifier.clip(NxShapes.chip).border(1.dp, p.accent2.copy(alpha = 0.6f), NxShapes.chip).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = p.accent2, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(nx.caps("Private"), style = nx.label(9f, p.accent2))
    }
}

@Composable
private fun PrivateRow(onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).clip(NxShapes.chip)
            .background(p.accent2.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = p.accent2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(nx.caps("Open Private Space"), style = nx.label(10.5f, p.text))
    }
}

private fun groupByCategory(apps: List<AppInfo>): List<Pair<String, List<AppInfo>>> {
    fun name(app: AppInfo): String = when (app.info.applicationInfo.category) {
        ApplicationInfo.CATEGORY_GAME -> "GAMES"
        ApplicationInfo.CATEGORY_AUDIO -> "AUDIO"
        ApplicationInfo.CATEGORY_VIDEO -> "VIDEO"
        ApplicationInfo.CATEGORY_IMAGE -> "IMAGING"
        ApplicationInfo.CATEGORY_SOCIAL -> "SOCIAL"
        ApplicationInfo.CATEGORY_NEWS -> "NEWS"
        ApplicationInfo.CATEGORY_MAPS -> "NAVIGATION"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "PRODUCTIVITY"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "ACCESSIBILITY"
        else -> if (app.info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) "SYSTEM" else "OTHER"
    }
    return apps.groupBy(::name).toList().sortedBy { (k, _) -> if (k == "OTHER") "~" else k }
}

@Composable
private fun ListRow(app: AppInfo, label: String, badge: Int, hidden: Boolean) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(
        Modifier.fillMaxWidth().clip(NxShapes.chip).background(p.panel.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer { alpha = if (hidden) 0.5f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, label, 38f, showLabel = false, badge = badge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = nx.body(15f), maxLines = 1)
            Text(app.component.packageName, style = nx.label(8.5f, p.textFaint), maxLines = 1)
        }
        Text("›", style = nx.label(14f, p.accent))
    }
}

@Composable
private fun TerminalRow(app: AppInfo, label: String, hidden: Boolean) {
    val nx = LocalNexus.current
    val p = nx.palette
    val mono = nx.label(12f, p.text).copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp).graphicsLayer { alpha = if (hidden) 0.5f else 1f }) {
        Text("> ", style = mono.copy(color = p.accent))
        Text(label.lowercase().replace(' ', '_'), style = mono, modifier = Modifier.weight(1f), maxLines = 1)
        Text(app.component.packageName.substringAfterLast('.').take(10), style = mono.copy(color = p.textFaint), maxLines = 1)
    }
}

/** Hex-packed grid: alternating rows are offset by half a cell. */
@Composable
private fun Honeycomb(
    apps: List<AppInfo>,
    labelOf: (AppInfo) -> String,
    badges: Map<String, Int>,
    onLaunch: (AppInfo, Rect) -> Unit,
    onMenu: (AppInfo, Rect) -> Unit,
    modifier: Modifier,
) {
    val nx = LocalNexus.current
    val perRow = 4
    val rows = remember(apps) {
        val out = mutableListOf<List<AppInfo>>()
        var i = 0; var r = 0
        while (i < apps.size) { val n = if (r % 2 == 0) perRow else perRow - 1; out += apps.subList(i, minOf(i + n, apps.size)); i += n; r++ }
        out
    }
    val size = nx.config.iconSize * 1.05f
    LazyColumn(modifier, contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy((-10).dp)) {
        items(rows.size, key = { "hr$it" }) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                rows[r].forEach { app ->
                    val bounds = rememberBounds()
                    Box(
                        Modifier.padding(horizontal = 6.dp).trackBounds(bounds)
                            .nexusItemGestures(key = app.key.id, onTap = { onLaunch(app, bounds.rect) }, onLongPress = { onMenu(app, bounds.rect) })
                    ) {
                        AppIcon(app, labelOf(app), size, showLabel = false, badge = badges[app.component.packageName] ?: 0)
                    }
                }
            }
        }
    }
}

/** 3D cover-flow: swipe sideways, the centred app shows its name; tap to launch. */
@Composable
private fun CoverFlow(
    apps: List<AppInfo>,
    labelOf: (AppInfo) -> String,
    onLaunch: (AppInfo, Rect) -> Unit,
    onMenu: (AppInfo, Rect) -> Unit,
    modifier: Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    if (apps.isEmpty()) return
    val pager = rememberPagerState { apps.size }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        HorizontalPager(
            state = pager,
            pageSize = PageSize.Fixed(132.dp),
            contentPadding = PaddingValues(horizontal = 120.dp),
            modifier = Modifier.fillMaxWidth().height(220.dp),
        ) { page ->
            val app = apps[page]
            val bounds = rememberBounds()
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        rotationY = (off * 48f).coerceIn(-70f, 70f)
                        val s = 1f - (kotlin.math.abs(off) * 0.22f).coerceAtMost(0.5f)
                        scaleX = s; scaleY = s
                        alpha = 1f - (kotlin.math.abs(off) * 0.3f).coerceAtMost(0.8f)
                        cameraDistance = 10f * density
                    }
                    .trackBounds(bounds)
                    .nexusItemGestures(key = app.key.id, onTap = { onLaunch(app, bounds.rect) }, onLongPress = { onMenu(app, bounds.rect) }),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(app, labelOf(app), 104f, showLabel = false, badge = 0)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(labelOf(apps[pager.currentPage.coerceIn(0, apps.size - 1)]).uppercase(), style = nx.label(13f, p.text, weight = 600))
        Text("${pager.currentPage + 1} / ${apps.size}", style = nx.label(9f, p.textDim))
    }
}
