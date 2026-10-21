package com.nexus.launcher.ui.settings

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.NexusApp
import com.nexus.launcher.core.FxQuality
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.ui.theme.NexusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Studio UI can ask of its host. */
interface StudioHost {
    fun setDefaultLauncher()
    fun openNotificationAccess()
    fun openAccessibility()
    fun openWriteSettings()
    fun openDndAccess()
    fun requestLocation()
    fun exportBackup()
    fun importBackup()
    fun resetLayout()
    fun goHome()
    /** Opens a launcher overlay: "console", "glyph" or "train". */
    fun openInLauncher(what: String)
    fun clearUsage()
    fun requestDialerRole()
    fun openOverlayPermission()
    fun calibrateFingerprint()
    fun openScreensaverSettings()
    val refreshTick: Int
}

/**
 * NEXUS Studio — the customization app. Edits a local draft (instant live preview) and
 * persists it to DataStore 250 ms after the last change, so sliders stay smooth.
 */
class StudioActivity : ComponentActivity(), StudioHost {

    private val app get() = application as NexusApp
    private val draft = MutableStateFlow<NexusConfig?>(null)
    private var saveJob: Job? = null
    private var tick by mutableIntStateOf(0)
    override val refreshTick: Int get() = tick

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            flush()
            val json = app.store.exportJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null }.getOrDefault(false)
            }
            SystemControls.toast(this@StudioActivity, if (ok) "Backup exported" else "Export failed")
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            val result = text?.let { app.store.importJson(it) }
            if (result?.isSuccess == true) {
                draft.value = app.store.config.first()
                SystemControls.toast(this@StudioActivity, "Backup imported — widgets must be re-added")
            } else {
                SystemControls.toast(this@StudioActivity, "Not a valid NEXUS backup")
            }
        }
    }

    private val dialerRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { draft.value = app.store.config.first() }
        setContent {
            val d by draft.collectAsStateWithLifecycle()
            val ps by app.system.powerSave.collectAsStateWithLifecycle()
            val th by app.system.thermal.collectAsStateWithLifecycle()
            val rm by app.system.reduceMotion.collectAsStateWithLifecycle()
            val cfg = d
            if (cfg != null) {
                val q = FxQuality.compute(cfg.perfMode, ps, th, rm)
                NexusTheme(cfg, q) {
                    StudioScreen(cfg, ::update, this@StudioActivity)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++
        app.system.refreshReduceMotion()
        if (saveJob?.isActive != true) lifecycleScope.launch { draft.value = app.store.config.first() }
    }

    override fun onPause() {
        lifecycleScope.launch { flush() }
        super.onPause()
    }

    private fun update(t: (NexusConfig) -> NexusConfig) {
        val cur = draft.value ?: return
        val next = t(cur)
        if (next == cur) return
        draft.value = next
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(250)
            app.store.updateConfig { next }
        }
    }

    private suspend fun flush() {
        val d = draft.value ?: return
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            app.store.updateConfig { d }
        }
    }

    override fun setDefaultLauncher() {
        if (!SystemControls.start(this, SystemControls.defaultHomeIntent(this))) {
            SystemControls.start(this, Intent(Settings.ACTION_SETTINGS))
        }
    }

    override fun openNotificationAccess() { SystemControls.start(this, SystemControls.notificationAccessIntent(this)) }
    override fun openAccessibility() { SystemControls.start(this, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    override fun openWriteSettings() { SystemControls.start(this, SystemControls.writeSettingsIntent(this)) }
    override fun openDndAccess() { SystemControls.start(this, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
    override fun requestLocation() { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    override fun exportBackup() { runCatching { exportLauncher.launch("nexus-backup.json") } }
    override fun importBackup() { runCatching { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) } }

    override fun resetLayout() {
        lifecycleScope.launch {
            app.store.clearLayout()
            SystemControls.toast(this@StudioActivity, "Home layout reset to defaults")
        }
    }

    override fun openInLauncher(what: String) {
        lifecycleScope.launch {
            flush()
            startActivity(
                Intent(this@StudioActivity, com.nexus.launcher.MainActivity::class.java)
                    .putExtra(com.nexus.launcher.MainActivity.EXTRA_OPEN, what)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun clearUsage() {
        lifecycleScope.launch { app.store.clearUsage(); SystemControls.toast(this@StudioActivity, "Prediction memory wiped") }
    }

    override fun requestDialerRole() {
        val i = com.nexus.launcher.call.DialerActivity.requestRoleIntent(this)
        if (i == null) { SystemControls.start(this, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); return }
        dialerRole.launch(i)
        if (android.os.Build.VERSION.SDK_INT >= 33) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun openOverlayPermission() {
        SystemControls.start(this, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
    }

    override fun calibrateFingerprint() {
        startActivity(Intent(this, com.nexus.launcher.lock.LockLayerActivity::class.java).putExtra(com.nexus.launcher.lock.LockLayerActivity.EXTRA_CALIBRATE, true))
    }

    override fun openScreensaverSettings() {
        if (!SystemControls.start(this, Intent(Settings.ACTION_DREAM_SETTINGS))) SystemControls.start(this, Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    override fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
