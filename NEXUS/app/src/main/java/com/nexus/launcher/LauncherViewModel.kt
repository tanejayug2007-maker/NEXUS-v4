package com.nexus.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.AppKey
import com.nexus.launcher.core.FxQuality
import com.nexus.launcher.core.GlyphBinding
import com.nexus.launcher.core.InterfaceMode
import com.nexus.launcher.core.Predictor
import com.nexus.launcher.core.HomeItem
import com.nexus.launcher.core.HomeLayout
import com.nexus.launcher.core.HomePage
import com.nexus.launcher.core.ItemType
import com.nexus.launcher.core.LayoutOps
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.PerfMode
import com.nexus.launcher.notifications.NexusNotification
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.ui.fx.LaunchFxEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What is layered over the home screen. Exactly one at a time keeps Back/Home predictable. */
sealed interface Overlay {
    data object None : Overlay
    data class Drawer(val search: Boolean = false) : Overlay
    data class Folder(val itemId: String, val origin: Rect) : Overlay
    data object Notifications : Overlay
    data object Control : Overlay
    data class Menu(val target: MenuTarget, val anchor: Rect) : Overlay
    data object WidgetPicker : Overlay
    data object Console : Overlay
    data class Glyph(val train: Boolean = false) : Overlay
}

enum class LineKind { INPUT, OUT, ERR, SYS }
data class ConsoleLine(val id: Long, val text: String, val kind: LineKind)

sealed interface MenuTarget {
    data class Item(val item: HomeItem, val page: Int) : MenuTarget
    data class DrawerApp(val app: AppInfo, val fromDrawer: Boolean = true) : MenuTarget
    data class DockApp(val app: AppInfo) : MenuTarget
    data class FolderApp(val folderId: String, val app: AppInfo) : MenuTarget
    data class Empty(val page: Int) : MenuTarget
}

enum class HomeEvent { DEFAULT_PAGE }

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val nx = application as NexusApp
    private val store = nx.store

    val config: StateFlow<NexusConfig?> = nx.config
    val apps: StateFlow<List<AppInfo>> = nx.apps.apps
    val appsByKey: StateFlow<Map<String, AppInfo>> =
        apps.map { list -> list.associateBy { it.key.id } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _layout = MutableStateFlow<HomeLayout?>(null)
    val layout: StateFlow<HomeLayout?> = _layout

    private val _overlay = MutableStateFlow<Overlay>(Overlay.None)
    val overlay: StateFlow<Overlay> = _overlay

    private val _placing = MutableStateFlow<String?>(null)
    val placing: StateFlow<String?> = _placing

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events

    private val _launchFx = MutableStateFlow<LaunchFxEvent?>(null)
    val launchFx: StateFlow<LaunchFxEvent?> = _launchFx

    private val _charging = MutableStateFlow(false)
    val charging: StateFlow<Boolean> = _charging

    private val _popup = MutableStateFlow<NexusNotification?>(null)
    val popup: StateFlow<NexusNotification?> = _popup

    private val _ambient = MutableStateFlow(true)
    val ambient: StateFlow<Boolean> = _ambient

    private val _resumed = MutableStateFlow(false)
    val resumed: StateFlow<Boolean> = _resumed

    /** Post-unlock reveal progress. Snapshot state so it can be reset synchronously in onResume. */
    val entranceProgress = mutableFloatStateOf(1f)
    private val _entranceTick = MutableStateFlow(0L)
    val entranceTick: StateFlow<Long> = _entranceTick

    private val _linkPending = MutableStateFlow(false)
    /** True while the post-unlock "neural link" ritual is waiting for the user's hold. */
    val linkPending: StateFlow<Boolean> = _linkPending

    private val _console = MutableStateFlow(
        listOf(
            ConsoleLine(0, "NEXUS//SHELL v2.0 — type `help`", LineKind.SYS),
        )
    )
    val console: StateFlow<List<ConsoleLine>> = _console
    private var lineId = 1L

    private val hour = MutableStateFlow(java.time.LocalTime.now().hour)

    /** Apps NEXUS expects you to open now (local, time-of-day launch counts). */
    val predicted: StateFlow<List<AppInfo>> = combine(nx.store.usage, appsByKey, hour) { usage, map, h ->
        Predictor.rank(usage, h, 12).mapNotNull { map[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val quality: StateFlow<FxQuality> = combine(
        config.filterNotNull().map { it.perfMode }.distinctUntilChanged(),
        nx.system.powerSave, nx.system.thermal, nx.system.reduceMotion,
    ) { mode, ps, th, rm -> FxQuality.compute(mode, ps, th, rm) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FxQuality.compute(PerfMode.AUTO, false, 0, false))

    val badges: StateFlow<Map<String, Int>> = NotificationHub.items
        .map { list -> list.filter { !it.ongoing }.groupingBy { it.pkg }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Last measured grid cell, used to size new widgets. */
    var cellSizePx: IntSize = IntSize(0, 0)
    var currentGridPage: Int = 0

    private var persistJob: Job? = null
    private var ambientJob: Job? = null
    private var popupJob: Job? = null

    private val cols get() = config.value?.gridCols ?: 4
    private val rows get() = config.value?.gridRows ?: 5

    init {
        // Follows the stored layout: first load, Studio "reset layout" (null) and backup imports.
        viewModelScope.launch {
            store.layout.collect { stored ->
                if (stored == null) {
                    nx.apps.loaded.first { it }
                    val fresh = buildDefaultLayout()
                    _layout.value = fresh
                    store.saveLayout(fresh)
                } else if (stored != _layout.value && persistJob?.isActive != true) {
                    _layout.value = stored
                }
            }
        }
        viewModelScope.launch {
            nx.apps.removed.collect { (pkg, user) -> updateLayout { LayoutOps.removePackage(it, pkg, user) } }
        }
        viewModelScope.launch {
            config.filterNotNull().map { it.gridCols to it.gridRows }.distinctUntilChanged().collect { (c, r) ->
                updateLayout { LayoutOps.normalize(it, c, r) }
            }
        }
        viewModelScope.launch {
            NotificationHub.posted.collect { n ->
                val cfg = config.value ?: return@collect
                if (!_resumed.value || !cfg.notifPopups || _overlay.value == Overlay.Notifications) return@collect
                _popup.value = n
                popupJob?.cancel()
                popupJob = launch { delay(4200); if (_popup.value?.key == n.key) _popup.value = null }
            }
        }
    }

    // ---- lifecycle ------------------------------------------------------------------------

    fun setResumed(r: Boolean) {
        _resumed.value = r
        if (r) { hour.value = java.time.LocalTime.now().hour; poke() } else {
            _ambient.value = false
            flushLayout()
        }
    }

    /** Keeps the ambient background alive for a few seconds after interaction (Balanced mode). */
    fun poke() {
        _ambient.value = true
        ambientJob?.cancel()
        if (!quality.value.ambientContinuous) {
            ambientJob = viewModelScope.launch { delay(7000); _ambient.value = false }
        }
    }

    /** Called once per unlock. Operator/Architect may insert the neural-link ritual first. */
    fun onUnlocked() {
        val cfg = config.value ?: return
        val needsLink = cfg.interfaceMode == InterfaceMode.ARCHITECT ||
            (cfg.interfaceMode == InterfaceMode.OPERATOR && cfg.linkSync)
        if (needsLink) {
            entranceProgress.floatValue = 0f
            close()
            _linkPending.value = true
        } else if (cfg.unlockEnabled) {
            triggerEntrance()
        }
    }

    fun triggerEntrance() {
        entranceProgress.floatValue = 0f
        _entranceTick.value = SystemClock.uptimeMillis()
    }

    fun completeLink() {
        if (!_linkPending.value) return
        _linkPending.value = false
        if (config.value?.unlockEnabled == true) triggerEntrance() else entranceProgress.floatValue = 1f
    }

    fun recordLaunch(app: AppInfo) {
        if (config.value?.predictive == false) return
        val h = java.time.LocalTime.now().hour
        nx.appScope.launch { nx.store.recordLaunch(app.key.id, h) }
    }

    // ---- console ----------------------------------------------------------------------------

    fun consoleAppend(lines: List<Pair<String, LineKind>>) {
        val add = lines.map { (t, k) -> ConsoleLine(lineId++, t, k) }
        _console.value = (_console.value + add).takeLast(300)
    }

    fun consoleClear() { _console.value = emptyList() }

    // ---- glyphs -----------------------------------------------------------------------------

    fun addGlyph(name: String, target: String, points: List<Float>) {
        updateConfig { c -> c.copy(glyphs = c.glyphs + GlyphBinding(LayoutOps.newId(), name.take(24), target, points)) }
    }

    fun removeGlyph(id: String) { updateConfig { c -> c.copy(glyphs = c.glyphs.filterNot { it.id == id }) } }

    fun showCharging() { _charging.value = true }
    fun hideCharging() { _charging.value = false }
    fun dismissPopup() { _popup.value = null }

    fun showLaunchFx(center: Offset, size: Float) {
        val cfg = config.value ?: return
        _launchFx.value = LaunchFxEvent(SystemClock.uptimeMillis(), center, size, cfg.launchAnim)
    }

    fun clearLaunchFx() { _launchFx.value = null }

    // ---- overlays ---------------------------------------------------------------------------

    fun open(o: Overlay) { _overlay.value = o; poke() }
    fun close() { _overlay.value = Overlay.None }

    /** Back: close the top layer; at rest Back does nothing (a launcher must never finish). */
    fun onBack() {
        when {
            _linkPending.value -> completeLink()
            _overlay.value != Overlay.None -> close()
            _placing.value != null -> _placing.value = null
            else -> _events.tryEmit(HomeEvent.DEFAULT_PAGE)
        }
    }

    /** Home pressed while NEXUS is already showing: close layers, then go to the default page. */
    fun onHomePressed(alreadyOnTop: Boolean) {
        _placing.value = null
        completeLink()
        val hadOverlay = _overlay.value != Overlay.None
        close()
        if (alreadyOnTop && !hadOverlay) _events.tryEmit(HomeEvent.DEFAULT_PAGE)
    }

    // ---- layout edits -----------------------------------------------------------------------

    private fun updateLayout(t: (HomeLayout) -> HomeLayout) {
        val cur = _layout.value ?: return
        val next = t(cur)
        if (next == cur) return
        _layout.value = next
        persistJob?.cancel()
        persistJob = viewModelScope.launch { delay(250); store.saveLayout(next) }
    }

    private fun flushLayout() {
        val l = _layout.value ?: return
        persistJob?.cancel()
        nx.appScope.launch { store.saveLayout(l) }
    }

    private fun locked() = config.value?.lockLayout == true

    fun drop(page: Int, itemId: String, x: Int, y: Int) {
        if (locked()) return
        updateLayout { LayoutOps.drop(it, page, itemId, x, y, cols, rows) }
    }

    fun startPlacing(itemId: String) { if (!locked()) { _placing.value = itemId; close() } }

    fun placeAt(page: Int, x: Int, y: Int) {
        val id = _placing.value ?: return
        updateLayout { LayoutOps.move(it, id, page, x, y, cols, rows) }
        _placing.value = null
    }

    fun cancelPlacing() { _placing.value = null }

    fun removeItem(itemId: String, onWidgetRemoved: (Int) -> Unit) {
        val l = _layout.value ?: return
        LayoutOps.locate(l, itemId)?.second?.let { if (it.type == ItemType.WIDGET) onWidgetRemoved(it.widgetId) }
        updateLayout { LayoutOps.remove(it, itemId) }
        close()
    }

    fun addToHome(app: AppInfo) {
        updateLayout { LayoutOps.addApp(it, app.key, currentGridPage, cols, rows) }
        close()
    }

    fun toggleDock(app: AppInfo) { updateLayout { LayoutOps.toggleDock(it, app.key, config.value?.dockMax ?: 5) }; close() }

    fun renameFolder(id: String, title: String) {
        val l = _layout.value ?: return
        val item = LayoutOps.locate(l, id)?.second ?: return
        updateLayout { LayoutOps.replace(it, item.copy(title = title.take(32))) }
    }

    fun removeFromFolder(folderId: String, app: AppKey) {
        updateLayout { LayoutOps.removeFromFolder(it, folderId, app, cols, rows) }
        close()
    }

    fun resize(itemId: String, w: Int, h: Int) = updateLayout { LayoutOps.resize(it, itemId, w, h, cols, rows) }

    fun addWidget(widgetId: Int, provider: String, w: Int, h: Int) {
        updateLayout { LayoutOps.addWidget(it, currentGridPage, widgetId, provider, w, h, cols, rows) }
    }

    fun addNexusWidget(kind: com.nexus.launcher.core.NexusWidgetKind, style: Int) {
        val frame = config.value?.widgetFrame?.ordinal ?: 0
        updateLayout { l ->
            val added = LayoutOps.addNexusWidget(l, currentGridPage, kind, style, cols, rows)
            // record the default frame on the newly added item
            val newItem = added.pages.flatMap { it.items }.firstOrNull { it.type == ItemType.NEXUS && l.pages.none { p -> p.items.any { x -> x.id == it.id } } }
            if (newItem == null) added else LayoutOps.replace(added, newItem.copy(title = "$style|$frame"))
        }
        close()
    }

    fun setItemData(itemId: String, data: String) {
        val l = _layout.value ?: return
        val item = LayoutOps.locate(l, itemId)?.second ?: return
        updateLayout { LayoutOps.replace(it, item.copy(data = data.take(2000))) }
    }

    /** Widget style index and frame are packed as "style|frame" in HomeItem.title. */
    fun setNexusFrame(itemId: String, frame: Int) {
        val l = _layout.value ?: return
        val item = LayoutOps.locate(l, itemId)?.second ?: return
        val style = item.title.substringBefore('|').toIntOrNull() ?: 0
        updateLayout { LayoutOps.replace(it, item.copy(title = "$style|$frame")) }
    }

    fun toggleOrbitPin(app: AppInfo) {
        updateConfig { c -> c.copy(orbitPinned = if (app.key.id in c.orbitPinned) c.orbitPinned - app.key.id else c.orbitPinned + app.key.id) }
        close()
    }

    fun toggleRadial(app: AppInfo) {
        updateConfig { c ->
            val next = if (app.key.id in c.radialApps) c.radialApps - app.key.id else (c.radialApps + app.key.id).takeLast(9)
            c.copy(radialApps = next, radialSource = com.nexus.launcher.core.RadialSource.CUSTOM)
        }
        close()
    }

    fun setNexusStyle(itemId: String, style: Int) {
        val l = _layout.value ?: return
        val item = LayoutOps.locate(l, itemId)?.second ?: return
        val frame = item.title.substringAfter('|', "").toIntOrNull()
        val st = style.coerceIn(0, 7).toString()
        updateLayout { LayoutOps.replace(it, item.copy(title = if (frame == null) st else "$st|$frame")) }
    }

    fun addPage() = updateLayout { LayoutOps.addPage(it) }.also { close() }
    fun removeEmptyPages() = updateLayout { LayoutOps.removeEmptyPages(it) }.also { close() }

    fun sendToPage(itemId: String, page: Int) {
        val l = _layout.value ?: return
        val (_, item) = LayoutOps.locate(l, itemId) ?: return
        val target = if (page >= l.pages.size) LayoutOps.addPage(l) else l
        val spot = LayoutOps.findFree(target.pages[page], item.w, item.h, cols, rows) ?: return
        updateLayout { LayoutOps.move(target, itemId, page, spot.first, spot.second, cols, rows) }
        close()
    }

    fun widgetIdsInLayout(): List<Int> = _layout.value?.let { LayoutOps.widgetIds(it) } ?: emptyList()

    // ---- per-app config ---------------------------------------------------------------------

    fun updateConfig(t: (NexusConfig) -> NexusConfig) {
        viewModelScope.launch { store.updateConfig(t) }
    }

    fun hideApp(app: AppInfo, hide: Boolean) {
        updateConfig { c -> c.copy(hiddenApps = if (hide) c.hiddenApps + app.key.id else c.hiddenApps - app.key.id) }
        close()
    }

    fun setLabel(app: AppInfo, label: String) {
        updateConfig { c ->
            c.copy(labelOverrides = if (label.isBlank() || label == app.label) c.labelOverrides - app.key.id else c.labelOverrides + (app.key.id to label.take(40)))
        }
    }

    fun setIconOverride(appId: String, uri: Uri?) {
        updateConfig { c -> c.copy(iconOverrides = if (uri == null) c.iconOverrides - appId else c.iconOverrides + (appId to uri.toString())) }
    }

    fun refreshWeather() {
        val cfg = config.value ?: return
        if (!cfg.weatherEnabled) return
        viewModelScope.launch { nx.weather.refreshIfStale() }
    }

    // ---- first run --------------------------------------------------------------------------

    private fun buildDefaultLayout(): HomeLayout {
        val pm = getApplication<Application>().packageManager
        val list = apps.value.filter { !it.isWork }
        fun byPkg(pkg: String?): AppInfo? = pkg?.let { p -> list.firstOrNull { it.component.packageName == p } }

        @Suppress("DEPRECATION")
        fun resolve(i: Intent): AppInfo? {
            val pkg = runCatching { pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
            return if (pkg == null || pkg == "android") null else byPkg(pkg)
        }

        val dock = listOfNotNull(
            resolve(Intent(Intent.ACTION_DIAL)),
            resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
            resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinctBy { it.key }.take(4)

        val preferred = listOfNotNull(
            list.firstOrNull { it.component.packageName == getApplication<Application>().packageName },
            resolve(Intent(Settings.ACTION_SETTINGS)),
            byPkg("com.android.vending"),
            resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)),
            resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)),
            byPkg("com.google.android.gm"),
            byPkg("com.google.android.apps.maps"),
            byPkg("com.google.android.youtube"),
            byPkg("com.nothing.weather"),
            byPkg("com.android.deskclock") ?: byPkg("com.google.android.deskclock"),
        )
        val chosen = (preferred + list).distinctBy { it.key }.filter { it !in dock }.take(8)
        var page = HomePage()
        chosen.forEachIndexed { i, a ->
            page = page.copy(items = page.items + HomeItem(id = LayoutOps.newId(), type = ItemType.APP, x = i % 4, y = i / 4, app = a.key))
        }
        return HomeLayout(pages = listOf(page), dock = dock.map { it.key })
    }
}
