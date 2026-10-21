package com.nexus.launcher.ui.home

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.launcher.HomeEvent
import com.nexus.launcher.LauncherActions
import com.nexus.launcher.LauncherViewModel
import com.nexus.launcher.MenuTarget
import com.nexus.launcher.Overlay
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.GestureAction
import com.nexus.launcher.core.HapticSignature
import com.nexus.launcher.core.InterfaceMode
import com.nexus.launcher.core.ItemType
import com.nexus.launcher.core.StatusPosition
import com.nexus.launcher.nexus
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.ui.components.AppIcon
import com.nexus.launcher.ui.components.FrameStatsOverlay
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.Haptic
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.NxShapes
import com.nexus.launcher.ui.components.nexusItemGestures
import com.nexus.launcher.ui.components.pinchGesture
import com.nexus.launcher.ui.components.rememberBounds
import com.nexus.launcher.ui.components.rememberHaptics
import com.nexus.launcher.ui.components.trackBounds
import com.nexus.launcher.ui.console.ConsoleOverlay
import com.nexus.launcher.ui.control.ControlCenter
import com.nexus.launcher.ui.drawer.AppDrawer
import com.nexus.launcher.ui.fx.ChargingOverlay
import com.nexus.launcher.ui.fx.EntranceOverlay
import com.nexus.launcher.ui.fx.HudChrome
import com.nexus.launcher.ui.fx.LaunchFxOverlay
import com.nexus.launcher.ui.fx.LinkSyncOverlay
import com.nexus.launcher.ui.fx.LocalEntrance
import com.nexus.launcher.ui.fx.LocalHeading
import com.nexus.launcher.ui.fx.LocalTilt
import com.nexus.launcher.ui.fx.NexusBackground
import com.nexus.launcher.ui.fx.entrance
import com.nexus.launcher.ui.fx.parallax
import com.nexus.launcher.ui.fx.rememberAmbientClock
import com.nexus.launcher.ui.fx.rememberTilt
import com.nexus.launcher.ui.glyph.GlyphPad
import com.nexus.launcher.ui.notifications.NotificationFeed
import com.nexus.launcher.ui.notifications.NotificationPopup
import com.nexus.launcher.ui.theme.LocalNexus
import com.nexus.launcher.ui.widgets.WidgetPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** What each pager position shows. */
private sealed interface PageKind {
    data object Orbit : PageKind
    data object Core : PageKind
    data class Grid(val index: Int) : PageKind
}

@Composable
fun HomeRoot(vm: LauncherViewModel, actions: LauncherActions) {
    val nx = LocalNexus.current
    val cfg = nx.config
    val ctx = LocalContext.current
    val system = ctx.nexus.system
    val mode = cfg.interfaceMode
    val operator = mode != InterfaceMode.CIVILIAN

    val layout by vm.layout.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val byKey by vm.appsByKey.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val placing by vm.placing.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val ambient by vm.ambient.collectAsStateWithLifecycle()
    val launchFx by vm.launchFx.collectAsStateWithLifecycle()
    val charging by vm.charging.collectAsStateWithLifecycle()
    val popup by vm.popup.collectAsStateWithLifecycle()
    val entranceTick by vm.entranceTick.collectAsStateWithLifecycle()
    val linkPending by vm.linkPending.collectAsStateWithLifecycle()
    val predicted by vm.predicted.collectAsStateWithLifecycle()
    val battery by system.battery.collectAsStateWithLifecycle()
    val net by system.net.collectAsStateWithLifecycle()
    val alerts by NotificationHub.items.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val haptic = rememberHaptics()
    val density = LocalDensity.current

    val labelOf: (AppInfo) -> String = remember(cfg.labelOverrides) { { a: AppInfo -> cfg.labelOverrides[a.key.id] ?: a.label } }
    val appForPkg: (String) -> AppInfo? = remember(apps) { { pkg: String -> apps.firstOrNull { it.component.packageName == pkg && !it.isWork } } }

    // ---- depth: gyroscope parallax (only while resumed, never in Battery Saver) ------------
    val (tilt, heading) = rememberTilt(cfg.parallax && nx.quality.tier >= 1)

    // ---- post-unlock reveal --------------------------------------------------------------
    LaunchedEffect(entranceTick) {
        if (entranceTick > 0L) {
            HapticSignature.play(ctx, HapticSignature.Kind.UNLOCK, if (cfg.hapticSignatures) cfg.haptics else com.nexus.launcher.core.HapticLevel.OFF)
            val d = if (nx.quality.heavyFx) 1300 else 450
            animate(0f, 1f, animationSpec = tween(nx.dur(d), easing = LinearEasing)) { v, _ -> vm.entranceProgress.floatValue = v }
            vm.entranceProgress.floatValue = 1f
        }
    }

    // ---- overlay progress ----------------------------------------------------------------
    val drawer = remember { Animatable(0f) }
    val drawerOpen = overlay is Overlay.Drawer || ((overlay as? Overlay.Menu)?.target as? MenuTarget.DrawerApp)?.fromDrawer == true
    LaunchedEffect(drawerOpen) { drawer.animateTo(if (drawerOpen) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 420f)) }
    val dim = remember { Animatable(0f) }
    val dimTarget = (overlay !is Overlay.None && overlay !is Overlay.Drawer) || linkPending
    LaunchedEffect(dimTarget) { dim.animateTo(if (dimTarget) 1f else 0f, tween(nx.dur(240))) }
    val drawerVisible by remember { derivedStateOf { drawer.value > 0.001f } }

    // ---- radial marking menu -------------------------------------------------------------
    var radial by remember { mutableStateOf<RadialState?>(null) }
    val radialN = cfg.radialSlots.coerceIn(4, 10)
    val radialApps: List<AppInfo?> = remember(predicted, layout, byKey, cfg.radialSource, cfg.radialApps, radialN, cfg.radialEditSlot) {
        val dock = layout?.dock.orEmpty().mapNotNull { byKey[it.id] }
        val custom = cfg.radialApps.mapNotNull { byKey[it] }
        val source = when (cfg.radialSource) {
            com.nexus.launcher.core.RadialSource.PREDICTED -> predicted + dock + apps.take(10)
            com.nexus.launcher.core.RadialSource.DOCK -> dock + predicted + apps.take(10)
            com.nexus.launcher.core.RadialSource.CUSTOM -> custom
        }.distinctBy { it.key }
        if (cfg.radialEditSlot) listOf<AppInfo?>(null) + source.take(radialN - 1) else source.take(radialN)
    }
    val deadPx = with(density) { 34.dp.toPx() }
    fun radialStart(o: Offset) {
        if (!cfg.radialMenu) { vm.open(Overlay.Menu(MenuTarget.Empty(vm.currentGridPage), Rect.Zero)); return }
        radial = RadialState(o, o)
    }
    fun radialMove(o: Offset) {
        val r = radial ?: return
        val before = radialSlot(r, deadPx, radialN)
        val next = r.copy(pointer = o)
        if (radialSlot(next, deadPx, radialN) != before) haptic(Haptic.TICK)
        radial = next
    }
    fun radialEnd(o: Offset) {
        val r = radial ?: return
        radial = null
        when (val slot = radialSlot(r.copy(pointer = o), deadPx, radialN)) {
            -1 -> Unit
            0 -> if (cfg.radialEditSlot) vm.open(Overlay.Menu(MenuTarget.Empty(vm.currentGridPage), Rect.Zero))
                 else radialApps.getOrNull(0)?.let { actions.launchApp(it, Rect(o, 60f)) }
            else -> radialApps.getOrNull(slot)?.let { actions.launchApp(it, Rect(o, 60f)) }
        }
    }

    fun handle(a: GestureAction) {
        if (a != GestureAction.NONE) haptic(Haptic.TICK)
        when (a) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER -> vm.open(if (mode == InterfaceMode.ARCHITECT) Overlay.Console else Overlay.Drawer())
            GestureAction.SEARCH -> vm.open(Overlay.Drawer(search = true))
            GestureAction.NOTIFICATIONS -> vm.open(Overlay.Notifications)
            GestureAction.CONTROL_CENTER -> vm.open(Overlay.Control)
            GestureAction.CONSOLE -> vm.open(Overlay.Console)
            GestureAction.GLYPH_PAD -> vm.open(Overlay.Glyph())
            else -> actions.systemGesture(a)
        }
    }

    fun runTarget(target: String) {
        vm.close()
        when {
            target.startsWith("app:") -> byKey[target.removePrefix("app:")]?.let { actions.launchApp(it, Rect.Zero) }
            target.startsWith("act:") -> runCatching { GestureAction.valueOf(target.removePrefix("act:")) }.getOrNull()?.let { handle(it) }
        }
    }

    val l = layout
    if (l == null) {
        NexusBackground(animate = false, modifier = Modifier.fillMaxSize())
        return
    }

    val kinds: List<PageKind> = remember(cfg.orbitEnabled, cfg.dashboardEnabled, l.pages.size) {
        buildList {
            if (cfg.orbitEnabled) add(PageKind.Orbit)
            if (cfg.dashboardEnabled) add(PageKind.Core)
            l.pages.indices.forEach { add(PageKind.Grid(it)) }
        }
    }
    val pager = rememberPagerState(initialPage = cfg.defaultPage.coerceIn(0, max(0, kinds.size - 1))) { kinds.size }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            if (e == HomeEvent.DEFAULT_PAGE) pager.animateScrollToPage(cfg.defaultPage.coerceIn(0, max(0, pager.pageCount - 1)))
        }
    }
    LaunchedEffect(pager.currentPage, kinds) {
        vm.currentGridPage = (kinds.getOrNull(pager.currentPage) as? PageKind.Grid)?.index ?: 0
        vm.poke()
    }

    val gridCb = remember(labelOf, byKey, operator, cfg.radialMenu, radialApps) {
        GridCallbacks(
            labelOf = labelOf,
            launch = { app, r -> actions.launchApp(app, r) },
            openFolder = { item, r -> vm.open(Overlay.Folder(item.id, r)) },
            nexusTap = { item ->
                when (item.provider) {
                    "CLOCK" -> actions.openClockApp()
                    "BATTERY", "SYSTEM" -> vm.open(Overlay.Control)
                    "WEATHER" -> {
                        if (vm.config.value?.weatherEnabled != true) vm.updateConfig { it.copy(weatherEnabled = true) }
                        actions.requestWeather()
                    }
                    "MEDIA" -> NotificationHub.media.value?.pkg?.let { actions.openApp(it) }
                    "SIGNALS" -> vm.open(Overlay.Notifications)
                    "SEARCH" -> vm.open(Overlay.Drawer(search = true))
                    "CALENDAR" -> actions.openCalendar()
                    "DATE" -> actions.openCalendar()
                    "WORLD_CLOCK" -> actions.openClockApp()
                    "PHOTO" -> if (item.data.isBlank()) actions.pickWidgetPhoto(item.id)
                    "NOTE" -> vm.open(Overlay.Menu(MenuTarget.Item(item, vm.currentGridPage), Rect.Zero))
                }
            },
            menu = { item, r -> vm.open(Overlay.Menu(MenuTarget.Item(item, vm.currentGridPage), r)) },
            drop = { item, x, y -> vm.drop(vm.currentGridPage, item.id, x, y) },
            radialStart = { o -> radialStart(o) },
            radialMove = { o -> radialMove(o) },
            radialEnd = { o -> radialEnd(o) },
            placeAt = { x, y -> vm.placeAt(vm.currentGridPage, x, y) },
            widgetView = { id -> actions.widgetView(id) },
            widgetSize = { id, w, h -> actions.widgetSize(id, w, h) },
            cellMeasured = { s -> vm.cellSizePx = s },
        )
    }
    val coreCb = remember(operator, cfg.radialMenu, radialApps) {
        CoreCallbacks(
            openSearch = { vm.open(if (mode == InterfaceMode.ARCHITECT) Overlay.Console else Overlay.Drawer(search = true)) },
            openNotifications = { vm.open(Overlay.Notifications) },
            openControl = { vm.open(Overlay.Control) },
            enableWeather = {
                if (vm.config.value?.weatherEnabled != true) vm.updateConfig { it.copy(weatherEnabled = true) }
                actions.requestWeather()
            },
            radialStart = { o -> radialStart(o) },
            radialMove = { o -> radialMove(o) },
            radialEnd = { o -> radialEnd(o) },
            openMediaApp = { pkg -> actions.openApp(pkg) },
        )
    }
    val orbitCb = remember(labelOf, cfg.orbitCoreTap, cfg.orbitCoreHold, cfg.radialMenu, radialApps) {
        OrbitCallbacks(
            labelOf = labelOf,
            launch = { app, r -> actions.launchApp(app, r) },
            menu = { app, r -> vm.open(Overlay.Menu(MenuTarget.DrawerApp(app, fromDrawer = false), r)) },
            coreTap = { handle(cfg.orbitCoreTap) },
            coreHold = { handle(cfg.orbitCoreHold) },
            radialStart = { o -> radialStart(o) },
            radialMove = { o -> radialMove(o) },
            radialEnd = { o -> radialEnd(o) },
        )
    }
    val orbitPool = remember(l, byKey, apps, cfg.orbitPinned) {
        val pinned = cfg.orbitPinned.mapNotNull { byKey[it] }
        val onHome = l.pages.flatMap { pg -> pg.items.flatMap { if (it.type == ItemType.FOLDER) it.apps else listOfNotNull(it.app) } } + l.dock
        pinned + onHome.mapNotNull { byKey[it.id] } + apps.filter { it.key.id !in cfg.hiddenApps }
    }

    // Pre-render every icon bitmap off the main thread so scrolling never decodes.
    val engine = ctx.nexus.icons
    val prewarmSpec = com.nexus.launcher.ui.components.rememberIconSpec(cfg.iconSize)
    val prewarmDrawer = com.nexus.launcher.ui.components.rememberIconSpec(cfg.iconSize * 0.9f)
    LaunchedEffect(apps, prewarmSpec, prewarmDrawer) {
        // Let the icons that are actually on screen (orbit, dock, grid) claim the CPU first;
        // the drawer/grid prewarm then fills the cache in the background.
        kotlinx.coroutines.delay(1200)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            apps.forEach { a ->
                val o = cfg.iconOverrides[a.key.id]
                engine.load(a, prewarmSpec, o)
                engine.load(a, prewarmDrawer, o)
            }
        }
    }

    val insets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
    val topPx = with(density) { insets.asPaddingValues().calculateTopPadding().toPx() }
    val bottomPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val hudClock = rememberAmbientClock(ambient && cfg.hud && nx.quality.ambientEnabled, maxFps = 30)
    val pStrength = cfg.parallaxStrength

    CompositionLocalProvider(
        com.nexus.launcher.LocalLauncherActions provides actions,
        LocalEntrance provides vm.entranceProgress,
        LocalTilt provides tilt,
        LocalHeading provides heading,
    ) {
        Box(Modifier.fillMaxSize()) {
            NexusBackground(
                animate = ambient,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // oversize slightly so parallax never reveals an edge
                    scaleX = 1.06f; scaleY = 1.06f
                }.parallax(-0.8f * pStrength),
            )

            // ================= HOME LAYER =================
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = max(drawer.value, dim.value)
                        val s = 1f - 0.05f * f
                        scaleX = s; scaleY = s
                        alpha = 1f - 0.35f * f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && nx.quality.blur && f > 0.02f) {
                            val r = 2f + 26f * nx.spec.blur * f
                            renderEffect = BlurEffect(r, r, TileMode.Decal)
                        }
                    }
                    .pinchGesture(cfg.pinchIn to cfg.pinchOut, onPinchIn = { handle(cfg.pinchIn) }, onPinchOut = { handle(cfg.pinchOut) })
                    .then(
                        if (cfg.doubleTap != GestureAction.NONE) Modifier.pointerInput(cfg.doubleTap, mode) {
                            detectTapGestures(onDoubleTap = { handle(cfg.doubleTap) }, onPress = { vm.poke() })
                        } else Modifier
                    )
                    .pointerInput(cfg.swipeUp, cfg.swipeDown, cfg.swipeDownRight, cfg.splitSwipeDown, mode) {
                        val tracker = VelocityTracker()
                        var total = 0f
                        var startX = 0f
                        var draggingDrawer = false
                        val drawerFollows = cfg.swipeUp == GestureAction.APP_DRAWER && mode != InterfaceMode.ARCHITECT
                        detectVerticalDragGestures(
                            onDragStart = { o -> total = 0f; startX = o.x; draggingDrawer = false; tracker.resetTracking(); vm.poke() },
                            onVerticalDrag = { change, dy ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                total += dy
                                change.consume()
                                if (drawerFollows && total < 0f) {
                                    draggingDrawer = true
                                    val v = (-total / (size.height * 0.55f)).coerceIn(0f, 1f)
                                    scope.launch { drawer.snapTo(v) }
                                }
                            },
                            onDragEnd = {
                                val vy = tracker.calculateVelocity().y
                                val threshold = 56.dp.toPx()
                                when {
                                    draggingDrawer -> {
                                        if (drawer.value > 0.28f || vy < -1100f) vm.open(Overlay.Drawer())
                                        else scope.launch { drawer.animateTo(0f) }
                                    }
                                    total < -threshold || vy < -1400f -> handle(cfg.swipeUp)
                                    total > threshold || vy > 1400f -> handle(
                                        if (cfg.splitSwipeDown && startX > size.width / 2f) cfg.swipeDownRight else cfg.swipeDown
                                    )
                                    else -> Unit
                                }
                            },
                            onDragCancel = { if (draggingDrawer) scope.launch { drawer.animateTo(0f) } },
                        )
                    }
            ) {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.windowInsetsPadding(insets.only(WindowInsetsSides.Top)))
                    if (cfg.statusStrip && cfg.statusPosition == StatusPosition.TOP) {
                        StatusStrip(battery, net, alerts.count { !it.ongoing }, Modifier.entrance(0).parallax(0.3f * pStrength))
                    }
                    HorizontalPager(
                        state = pager,
                        beyondViewportPageCount = 1,
                        key = { kinds.getOrNull(it)?.toString() ?: "p$it" },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) { page ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                                    pageTransition(cfg.pageTransition, off, operator)
                                }
                                .parallax(0.5f * pStrength)
                        ) {
                            when (val k = kinds.getOrNull(page)) {
                                PageKind.Orbit -> OrbitPage(
                                    predicted = predicted, pool = orbitPool, battery = battery, badges = badges,
                                    animate = ambient, cb = orbitCb,
                                )
                                PageKind.Core -> CorePage(battery, net, coreCb)
                                is PageKind.Grid -> {
                                    val gp = l.pages.getOrNull(k.index)
                                    if (gp != null) Column(Modifier.fillMaxSize()) {
                                        if (k.index == 0 && cfg.homeHeader != com.nexus.launcher.core.HomeHeader.NONE) {
                                            HomeHeaderView(cfg.homeHeader, onSearch = { vm.open(Overlay.Drawer(search = true)) }, modifier = Modifier.entrance(1))
                                        }
                                        Box(Modifier.weight(1f)) {
                                            GridPage(
                                                page = gp, cols = cfg.gridCols, rows = cfg.gridRows, apps = byKey, badges = badges,
                                                placingId = placing, locked = cfg.lockLayout, cb = gridCb,
                                            )
                                            if (gp.items.isEmpty() && placing == null) EmptyPageHint()
                                        }
                                    }
                                }
                                null -> Unit
                            }
                        }
                    }
                    PageIndicator(kinds, pager.currentPage)
                    if (cfg.statusStrip && cfg.statusPosition == StatusPosition.BOTTOM) {
                        StatusStrip(battery, net, alerts.count { !it.ongoing })
                    }
                    if (cfg.dockEnabled && cfg.dockStyle != com.nexus.launcher.core.DockStyle.NONE) {
                        Dock(
                            style = cfg.dockStyle,
                            dock = l.dock.mapNotNull { byKey[it.id] }, labelOf = labelOf, badges = badges,
                            onLaunch = { a, r -> actions.launchApp(a, r) },
                            onMenu = { a, r -> vm.open(Overlay.Menu(MenuTarget.DockApp(a), r)) },
                            modifier = Modifier.entrance(12).parallax(0.25f * pStrength),
                        )
                    }
                    // The HUD draws its telemetry ticker in the bottom margin: keep the dock above it.
                    if (cfg.hud && cfg.hudTicker) Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
                }
                if (placing != null) {
                    PlacingBanner(onCancel = { vm.cancelPlacing() }, modifier = Modifier.align(Alignment.TopCenter))
                }
            }

            if (cfg.hud) {
                HudChrome(
                    battery = battery, net = net, clock = hudClock, topInsetPx = topPx, bottomInsetPx = bottomPx,
                    // paused under overlays (drawer, deck, feed…) so it doesn't show through them
                    ticker = cfg.hudTicker && overlay == Overlay.None, modifier = Modifier.entrance(14).parallax(-0.25f * pStrength),
                )
            }

            EntranceOverlay(vm.entranceProgress)

            // ================= OVERLAYS =================
            radial?.let { r -> RadialSelector(r, radialApps, labelOf, radialN, cfg.radialRadius, cfg.radialLabels, cfg.radialEditSlot) }

            val folderTarget = when (val o = overlay) {
                is Overlay.Folder -> o.itemId to o.origin
                is Overlay.Menu -> (o.target as? MenuTarget.FolderApp)?.let { it.folderId to Rect.Zero }
                else -> null
            }
            folderTarget?.let { (fid, origin) ->
                val folder = l.pages.flatMap { it.items }.firstOrNull { it.id == fid && it.type == ItemType.FOLDER }
                if (folder != null) {
                    FolderOverlay(
                        folder = folder, origin = origin, apps = byKey, badges = badges, labelOf = labelOf,
                        onLaunch = { a, r -> vm.close(); actions.launchApp(a, r) },
                        onAppMenu = { a, r -> vm.open(Overlay.Menu(MenuTarget.FolderApp(folder.id, a), r)) },
                        onRename = { t -> vm.renameFolder(folder.id, t) },
                        onClose = { vm.close() },
                    )
                }
            }

            NotificationFeed(
                visible = overlay == Overlay.Notifications,
                appForPkg = appForPkg,
                onOpen = { n -> actions.openNotification(n) },
                onGrantAccess = { actions.openNotificationAccess() },
                onSystemShade = { vm.close(); actions.systemGesture(GestureAction.SYSTEM_SHADE) },
                onClose = { vm.close() },
            )

            ControlCenter(
                visible = overlay == Overlay.Control,
                battery = battery, net = net, torch = actions.torch,
                onClose = { vm.close() },
                onSystemQs = { vm.close(); actions.systemGesture(GestureAction.SYSTEM_QUICK_SETTINGS) },
            )

            if (drawerVisible || drawerOpen) {
                AppDrawer(
                    progress = drawer,
                    open = drawerOpen,
                    focusSearch = (overlay as? Overlay.Drawer)?.search == true,
                    apps = apps, hidden = cfg.hiddenApps, badges = badges, labelOf = labelOf,
                    onLaunch = { a, r -> actions.launchApp(a, r); scope.launch { delay(350); vm.close() } },
                    onMenu = { a, r -> vm.open(Overlay.Menu(MenuTarget.DrawerApp(a), r)) },
                    onWebSearch = { q -> actions.webSearch(q); vm.close() },
                    onClose = { vm.close() },
                )
            }

            ConsoleOverlay(visible = overlay == Overlay.Console, vm = vm, actions = actions)

            val glyph = overlay as? Overlay.Glyph
            GlyphPad(visible = glyph != null, train = glyph?.train == true, vm = vm, onTarget = { t -> runTarget(t) }, onClose = { vm.close() })

            if (overlay == Overlay.WidgetPicker) {
                WidgetPicker(
                    onNexus = { kind, style -> vm.addNexusWidget(kind, style) },
                    providers = { actions.widgetProviders() },
                    cellWidthPx = vm.cellSizePx.width, cellHeightPx = vm.cellSizePx.height,
                    onPick = { info -> actions.pickWidget(info) },
                    onClose = { vm.close() },
                )
            }

            (overlay as? Overlay.Menu)?.let { m ->
                ItemMenu(target = m.target, anchor = m.anchor, layout = l, apps = byKey, vm = vm, actions = actions, labelOf = labelOf)
            }

            NotificationPopup(popup, appForPkg, onOpen = { n -> vm.dismissPopup(); actions.openNotification(n) }, onDismiss = { vm.dismissPopup() })

            LaunchFxOverlay(launchFx) { vm.clearLaunchFx() }

            ChargingOverlay(
                visible = charging, battery = battery, style = cfg.chargingAnim, intensity = cfg.chargingIntensity,
                durationSec = cfg.chargingDurationSec, onDismiss = { vm.hideCharging() },
            )

            LinkSyncOverlay(visible = linkPending, holdMs = cfg.linkSyncMs) { vm.completeLink() }

            if (!cfg.onboarded) {
                Onboarding { exp, mode ->
                    vm.updateConfig { c ->
                        val base = if (exp == com.nexus.launcher.core.Experience.FRIENDLY) com.nexus.launcher.core.Experiences.friendly(c)
                        else com.nexus.launcher.core.Experiences.futuristic(c)
                        base.copy(colorMode = mode, onboarded = true)
                    }
                }
            }

            if (cfg.showFrameStats) {
                FrameStatsOverlay(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp))
            }
        }
    }
}

@Composable
private fun PageIndicator(kinds: List<PageKind>, current: Int) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(
        Modifier.fillMaxWidth().height(18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        kinds.forEachIndexed { i, k ->
            val on = i == current
            val special = k !is PageKind.Grid
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (on) 18.dp else 6.dp, if (special) 6.dp else 3.dp)
                    .clip(if (special) NxShapes.chip else NxShapes.capsule)
                    .background(if (on) (if (k is PageKind.Orbit) p.accent2 else p.accent) else p.accent.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
private fun Dock(
    style: com.nexus.launcher.core.DockStyle,
    dock: List<AppInfo>,
    labelOf: (AppInfo) -> String,
    badges: Map<String, Int>,
    onLaunch: (AppInfo, Rect) -> Unit,
    onMenu: (AppInfo, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    if (dock.isEmpty()) return
    val row: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            dock.forEach { app ->
                val bounds = rememberBounds()
                Box(
                    Modifier
                        .trackBounds(bounds)
                        .nexusItemGestures(
                            key = app.key.id,
                            onTap = { onLaunch(app, bounds.rect) },
                            onLongPress = { onMenu(app, bounds.rect) },
                        )
                ) {
                    AppIcon(app, labelOf(app), nx.config.iconSize * 0.92f, showLabel = false, badge = badges[app.component.packageName] ?: 0)
                }
            }
        }
    }
    when (style) {
        com.nexus.launcher.core.DockStyle.GLASS ->
            GlassPanel(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), padding = 8.dp, brackets = true) { row() }
        com.nexus.launcher.core.DockStyle.SOLID -> Box(
            modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(NxShapes.panel).background(p.panelTop.copy(alpha = 0.92f)).padding(8.dp)
        ) { row() }
        com.nexus.launcher.core.DockStyle.FLOATING -> Box(
            modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp).clip(NxShapes.capsule)
                .background(p.panelTop.copy(alpha = if (p.isLight) 0.85f else 0.7f)).padding(horizontal = 6.dp, vertical = 8.dp)
        ) { row() }
        com.nexus.launcher.core.DockStyle.TRANSPARENT -> Box(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) { row() }
        com.nexus.launcher.core.DockStyle.NONE -> Unit
    }
}

@Composable
private fun PlacingBanner(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    GlassPanel(modifier.padding(top = 56.dp), highlight = true, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TAP A CELL · SWIPE FOR OTHER PAGES", style = nx.label(10f, nx.palette.text))
            Spacer(Modifier.width(12.dp))
            NxButton("Cancel", onCancel)
        }
    }
}


/** Page-to-page transitions. [off] is the page's distance from centre (-1..1 while visible). */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.pageTransition(
    t: com.nexus.launcher.core.PageTransition,
    off: Float,
    strong: Boolean,
) {
    val a = abs(off).coerceIn(0f, 1f)
    cameraDistance = 14f * density
    when (t) {
        com.nexus.launcher.core.PageTransition.SLIDE -> Unit
        com.nexus.launcher.core.PageTransition.TILT_3D -> {
            alpha = 1f - a * 0.55f
            rotationY = off * (if (strong) 18f else 9f)
            val s = 1f - a * 0.07f; scaleX = s; scaleY = s
        }
        com.nexus.launcher.core.PageTransition.CUBE -> {
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (off < 0) 0f else 1f, 0.5f)
            rotationY = off * -70f
            alpha = 1f - a * 0.3f
        }
        com.nexus.launcher.core.PageTransition.DEPTH -> {
            if (off > 0) {
                translationX = size.width * off
                val s = 1f - a * 0.25f; scaleX = s; scaleY = s
            }
            alpha = 1f - a * 0.8f
        }
        com.nexus.launcher.core.PageTransition.CAROUSEL -> {
            val s = 1f - a * 0.2f; scaleX = s; scaleY = s
            translationY = a * size.height * 0.05f
            rotationZ = off * -4f
            alpha = 1f - a * 0.4f
        }
        com.nexus.launcher.core.PageTransition.FLIP -> {
            translationX = size.width * off
            rotationY = off * 180f
            alpha = if (a > 0.5f) 0f else 1f
        }
        com.nexus.launcher.core.PageTransition.GLITCH -> {
            alpha = 1f - a * 0.9f
            translationX = if (a > 0.05f && a < 0.95f) ((a * 37f).toInt() % 5 - 2) * 6f else 0f
            scaleY = 1f - a * 0.15f
        }
    }
}
