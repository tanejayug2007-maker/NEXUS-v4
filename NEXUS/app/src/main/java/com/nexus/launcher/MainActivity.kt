package com.nexus.launcher

import android.Manifest
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.SearchManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.BackgroundStyle
import com.nexus.launcher.core.GestureAction
import com.nexus.launcher.core.HapticSignature
import com.nexus.launcher.core.LaunchAnim
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.PerfMode
import com.nexus.launcher.notifications.NexusNotification
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.system.TorchController
import com.nexus.launcher.ui.home.HomeRoot
import com.nexus.launcher.ui.settings.StudioActivity
import com.nexus.launcher.ui.theme.NexusTheme
import com.nexus.launcher.ui.widgets.NexusWidgetHost
import com.nexus.launcher.ui.widgets.spanFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The HOME activity. singleTask + HOME category make it the real launcher: the Home button
 * always lands here, Back never finishes it, and it is restored automatically after reboot.
 */
class MainActivity : ComponentActivity(), LauncherActions {

    private val vm: LauncherViewModel by viewModels()
    private val app get() = application as NexusApp
    private lateinit var widgets: NexusWidgetHost
    override val torch: TorchController by lazy { TorchController(this) }

    private var pendingWidgetId = -1
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private var pendingIconTarget: String? = null
    private var handledScreenOff = 0L
    private var lastChargeShownFor = 0L
    private var appliedRefreshPref: Boolean? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val id = pendingWidgetId
        val info = pendingWidgetInfo ?: widgets.info(id)
        if (res.resultCode == RESULT_OK && id != -1 && info != null) configureOrAdd(id, info) else cancelPendingWidget()
    }

    private val pickIconLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingIconTarget ?: return@registerForActivityResult
        pendingIconTarget = null
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setIconOverride(target, uri)
        }
    }

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var pendingPhotoTarget: String? = null
    private val pickPhotoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingPhotoTarget ?: return@registerForActivityResult
        pendingPhotoTarget = null
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setItemData(target, uri.toString())
        }
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refreshWeather() else SystemControls.toast(this, "Weather needs approximate location")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING_WIDGET, -1) ?: -1
        pendingIconTarget = savedInstanceState?.getString(KEY_PENDING_ICON)

        widgets = NexusWidgetHost(this)
        widgets.onWidgetLongPress = { id -> openWidgetMenu(id) }

        // A launcher must never finish on Back: we always consume it.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = vm.onBack()
        })

        setContent {
            val cfg by vm.config.collectAsStateWithLifecycle()
            val quality by vm.quality.collectAsStateWithLifecycle()
            val c = cfg
            if (c == null) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
            } else {
                NexusTheme(c, quality) { HomeRoot(vm, this@MainActivity) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.config.filterNotNull().distinctUntilChanged().collect { applyWindow(it) } }
                launch { app.system.userPresent.collect { onUserPresent() } }
                launch { app.system.powerConnected.collect { if (isResumed()) maybeShowCharging() } }
                launch { vm.toast.collect { SystemControls.toast(this@MainActivity, it) } }
            }
        }

        // Remove widget ids the host still holds but the layout no longer references.
        lifecycleScope.launch {
            vm.layout.filterNotNull().first()
            val used = vm.widgetIdsInLayout().toSet()
            runCatching { widgets.host.appWidgetIds }.getOrNull()?.forEach { id ->
                if (id !in used && id != pendingWidgetId) widgets.delete(id)
            }
        }
    }

    private fun isResumed() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    override fun onStart() {
        super.onStart()
        widgets.startListening()
        app.system.startForeground()
        torch.start()
    }

    override fun onResume() {
        super.onResume()
        vm.setResumed(true)
        val km = getSystemService(KeyguardManager::class.java)
        val off = app.system.lastScreenOff
        if (off > handledScreenOff && !km.isKeyguardLocked) {
            handledScreenOff = off
            vm.onUnlocked()
        }
        handleOpenExtra(intent)
        maybeShowCharging()
        vm.refreshWeather()
    }

    private fun onUserPresent() {
        val off = app.system.lastScreenOff
        if (isResumed() && off > handledScreenOff) {
            handledScreenOff = off
            vm.onUnlocked()
            maybeShowCharging()
        }
    }

    private fun maybeShowCharging() {
        val cfg = vm.config.value ?: return
        if (!cfg.chargingEnabled) return
        val t = app.system.lastPowerConnected
        if (t == 0L || t <= lastChargeShownFor) return
        if (SystemClock.elapsedRealtime() - t > cfg.chargingWindowSec * 1000L) return
        if (!app.system.battery.value.charging) return
        lastChargeShownFor = t
        vm.showCharging()
        if (cfg.hapticSignatures) HapticSignature.play(this, HapticSignature.Kind.CHARGE, cfg.haptics)
    }

    override fun onPause() {
        vm.setResumed(false)
        super.onPause()
    }

    override fun onStop() {
        widgets.stopListening()
        app.system.stopForeground()
        torch.stop()
        vm.hideCharging()
        super.onStop()
    }

    override fun onDestroy() {
        widgets.clearViews()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        vm.config.value?.let { applyWindow(it) } // dark/light system switch
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_OPEN)) { handleOpenExtra(intent); return }
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.onHomePressed(alreadyOnTop = hasWindowFocus())
        }
    }

    /** Studio can ask the launcher to open the shell or the glyph trainer. */
    private fun handleOpenExtra(i: Intent?) {
        if (i == null) return
        val what = i.getStringExtra(EXTRA_OPEN) ?: return
        i.removeExtra(EXTRA_OPEN)
        when (what) {
            "console" -> vm.open(Overlay.Console)
            "glyph" -> vm.open(Overlay.Glyph())
            "train" -> vm.open(Overlay.Glyph(train = true))
            "private" -> vm.open(Overlay.PrivateSpace)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING_WIDGET, pendingWidgetId)
        pendingIconTarget?.let { outState.putString(KEY_PENDING_ICON, it) }
    }

    @Deprecated("Widget configuration still reports through onActivityResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE_WIDGET) {
            val id = pendingWidgetId
            val info = pendingWidgetInfo ?: widgets.info(id)
            if (resultCode == RESULT_OK && id != -1 && info != null) finishAddWidget(id, info) else cancelPendingWidget()
        }
    }

    // ---- window ---------------------------------------------------------------------------

    private fun applyWindow(cfg: NexusConfig) {
        val wallpaper = cfg.theme.backgroundStyle == BackgroundStyle.WALLPAPER
        if (wallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(if (cfg.colorMode == com.nexus.launcher.core.ColorMode.LIGHT) cfg.theme.lightBackground.toInt() else Color.BLACK))
        }
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val light = when (cfg.colorMode) {
            com.nexus.launcher.core.ColorMode.LIGHT -> true
            com.nexus.launcher.core.ColorMode.DARK -> false
            com.nexus.launcher.core.ColorMode.SYSTEM -> !night
        }
        // dark status-bar icons on light themes (wallpaper mode keeps light icons: wallpapers are usually busy)
        ctl.isAppearanceLightStatusBars = light && !wallpaper
        ctl.isAppearanceLightNavigationBars = light && !wallpaper
        if (cfg.hideSystemStatusBar) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            ctl.show(WindowInsetsCompat.Type.statusBars())
        }
        // Ultra asks for the panel's highest refresh rate; other modes let Nothing OS decide.
        val ultra = cfg.perfMode == PerfMode.ULTRA
        if (appliedRefreshPref != ultra) {
            appliedRefreshPref = ultra
            val lp = window.attributes
            lp.preferredDisplayModeId = if (ultra) highestRefreshModeId() else 0
            window.attributes = lp
        }
    }

    @Suppress("DEPRECATION")
    private fun highestRefreshModeId(): Int {
        val d = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay) ?: return 0
        val cur = d.mode
        return d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate }?.modeId ?: 0
    }

    // ---- LauncherActions ------------------------------------------------------------------

    override fun launchApp(app: AppInfo, bounds: Rect) {
        val cfg = vm.config.value ?: return
        val q = vm.quality.value
        val dm = resources.displayMetrics
        val b = if (bounds == Rect.Zero) {
            val s = 120f
            Rect(dm.widthPixels / 2f - s / 2, dm.heightPixels / 2f - s / 2, dm.widthPixels / 2f + s / 2, dm.heightPixels / 2f + s / 2)
        } else bounds
        val local = android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
        val loc = IntArray(2).also { window.decorView.getLocationOnScreen(it) }
        val screen = android.graphics.Rect(local).apply { offset(loc[0], loc[1]) }

        val reduce = this.app.system.reduceMotion.value
        val fx = cfg.launchAnim != LaunchAnim.SYSTEM_DEFAULT && !reduce
        if (fx) vm.showLaunchFx(b.center, b.width)
        val lead = if (fx && q.heavyFx) cfg.launchLeadMs.coerceIn(0, 150) else 0
        val opts = if (reduce) null else optionsFor(cfg.launchAnim, local)

        vm.recordLaunch(app)
        if (cfg.hapticSignatures) HapticSignature.play(this, HapticSignature.Kind.LAUNCH, cfg.haptics)
        val go = {
            if (!this.app.apps.launch(app, screen, opts)) SystemControls.toast(this, "${app.label} is unavailable")
        }
        if (lead == 0) go() else lifecycleScope.launch { delay(lead.toLong()); go() }
    }

    private fun optionsFor(preset: LaunchAnim, r: android.graphics.Rect): Bundle? {
        val v = window.decorView
        val o = when (preset) {
            LaunchAnim.QUANTUM_PORTAL, LaunchAnim.NEURAL_PULSE, LaunchAnim.ENERGY_WAVE, LaunchAnim.SINGULARITY ->
                ActivityOptions.makeClipRevealAnimation(v, r.left, r.top, r.width(), r.height())
            LaunchAnim.HOLOGRAPHIC_EXPANSION, LaunchAnim.HYPERSPACE_JUMP ->
                ActivityOptions.makeScaleUpAnimation(v, r.left, r.top, r.width(), r.height())
            LaunchAnim.CYBERNETIC_ZOOM, LaunchAnim.DATA_STREAM ->
                ActivityOptions.makeCustomAnimation(this, R.anim.nexus_zoom_enter, R.anim.nexus_zoom_exit)
            LaunchAnim.DIGITAL_MATERIALIZATION, LaunchAnim.HEX_DISSOLVE ->
                ActivityOptions.makeCustomAnimation(this, R.anim.nexus_materialize_enter, R.anim.nexus_hold_exit)
            LaunchAnim.SYSTEM_DEFAULT -> null
        }
        return o?.toBundle()
    }

    override fun shortcutsFor(app: AppInfo): List<ShortcutInfo> = this.app.apps.shortcuts(app)

    override fun launchShortcut(s: ShortcutInfo, bounds: Rect) {
        val r = if (bounds == Rect.Zero) null else android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        app.apps.startShortcut(s, r)
    }

    override fun appInfo(app: AppInfo) = this.app.apps.openAppDetails(app)

    override fun uninstall(app: AppInfo) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user)
        if (!SystemControls.start(this, i)) SystemControls.toast(this, "Can't uninstall ${app.label}")
    }

    override fun systemGesture(action: GestureAction) {
        when (action) {
            GestureAction.SYSTEM_SHADE -> if (!SystemControls.expandNotifications(this)) SystemControls.toast(this, "Couldn't open the notification shade")
            GestureAction.SYSTEM_QUICK_SETTINGS -> if (!SystemControls.expandQuickSettings(this)) SystemControls.toast(this, "Couldn't open Quick Settings")
            GestureAction.LOCK_SCREEN -> SystemControls.lockScreen(this)
            GestureAction.FLASHLIGHT -> if (!torch.toggle()) SystemControls.toast(this, "No flashlight available")
            GestureAction.STUDIO -> openStudio()
            GestureAction.ASSISTANT -> SystemControls.assistant(this)
            GestureAction.RECENTS -> if (!SystemControls.recents()) SystemControls.toast(this, "Enable the NEXUS gesture helper for Recents")
            else -> Unit
        }
    }

    override fun widgetProviders(): List<AppWidgetProviderInfo> = widgets.providers()

    override fun pickWidget(info: AppWidgetProviderInfo) {
        vm.close()
        val id = widgets.allocate()
        pendingWidgetId = id
        pendingWidgetInfo = info
        if (widgets.bindIfAllowed(id, info)) {
            configureOrAdd(id, info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            runCatching { bindWidget.launch(intent) }.onFailure { cancelPendingWidget() }
        }
    }

    private fun configureOrAdd(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            val ok = runCatching {
                widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE_WIDGET, null)
            }.isSuccess
            if (!ok) finishAddWidget(id, info)
        } else {
            finishAddWidget(id, info)
        }
    }

    private fun finishAddWidget(id: Int, info: AppWidgetProviderInfo) {
        val cfg = vm.config.value ?: NexusConfig()
        val cell = vm.cellSizePx
        val (w, h) = spanFor(info, cell.width, cell.height)
        vm.addWidget(id, info.provider.flattenToString(), w.coerceAtMost(cfg.gridCols), h.coerceAtMost(cfg.gridRows))
        pendingWidgetId = -1
        pendingWidgetInfo = null
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != -1) widgets.delete(pendingWidgetId)
        pendingWidgetId = -1
        pendingWidgetInfo = null
    }

    private fun openWidgetMenu(widgetId: Int) {
        val l = vm.layout.value ?: return
        l.pages.forEachIndexed { pageIndex, page ->
            page.items.firstOrNull { it.widgetId == widgetId }?.let { item ->
                vm.open(Overlay.Menu(MenuTarget.Item(item, pageIndex), Rect.Zero))
                return
            }
        }
    }

    override fun widgetView(id: Int): View = widgets.view(id) ?: TextView(this).apply {
        text = "WIDGET OFFLINE\nlong-press › remove"
        setTextColor(Color.argb(150, 255, 255, 255))
        gravity = Gravity.CENTER
        textSize = 11f
    }

    override fun widgetSize(id: Int, widthDp: Int, heightDp: Int) = widgets.updateSize(id, widthDp, heightDp)

    override fun deleteWidget(id: Int) = widgets.delete(id)

    override fun pickIcon(app: AppInfo) {
        pendingIconTarget = app.key.id
        runCatching { pickIconLauncher.launch(arrayOf("image/*")) }
    }

    override fun openStudio() {
        startActivity(Intent(this, StudioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openWallpaperPicker() {
        if (!SystemControls.start(this, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))) {
            SystemControls.toast(this, "No wallpaper picker found")
        }
    }

    override fun webSearch(query: String) {
        val ok = SystemControls.start(this, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
        if (!ok) SystemControls.start(this, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
    }

    override fun requestWeather() {
        if (app.weather.hasPermission()) vm.refreshWeather()
        else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    override fun openNotificationAccess() {
        SystemControls.start(this, SystemControls.notificationAccessIntent(this))
    }

    override fun openNotification(n: NexusNotification) {
        vm.close()
        if (!NotificationHub.open(this, n)) openApp(n.pkg)
    }

    override fun openCalendar() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            calendarPermission.launch(Manifest.permission.READ_CALENDAR); return
        }
        val i = Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
        if (!SystemControls.start(this, i)) SystemControls.toast(this, "No calendar app found")
    }

    override fun pickWidgetPhoto(itemId: String) {
        pendingPhotoTarget = itemId
        runCatching { pickPhotoLauncher.launch(arrayOf("image/*")) }
    }

    override fun openClockApp() {
        if (!SystemControls.start(this, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))) SystemControls.toast(this, "No clock app found")
    }

    override fun openApp(pkg: String) {
        // private apps too: this is only reached from that app's own notification or media card
        val a = (vm.apps.value + vm.privateApps.value).firstOrNull { it.component.packageName == pkg }
        if (a != null) launchApp(a, Rect.Zero)
    }

    companion object {
        const val EXTRA_OPEN = "nexus.open"
        private const val KEY_PENDING_WIDGET = "pending_widget"
        private const val KEY_PENDING_ICON = "pending_icon"
        private const val REQ_CONFIGURE_WIDGET = 0x5A1
    }
}
