package com.nexus.launcher.system.tiles

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nexus.launcher.MainActivity
import com.nexus.launcher.apps.calculator.CalculatorActivity
import com.nexus.launcher.apps.settingshub.SettingsHubActivity
import com.nexus.launcher.core.PerfMode
import com.nexus.launcher.nexus
import com.nexus.launcher.system.SystemControls
import com.nexus.launcher.system.TorchController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * NEXUS Quick Settings tiles. These are REAL tiles inside the REAL Nothing OS Quick Settings panel
 * (Android's public TileService API). Android draws them in the system's own tile style — NEXUS
 * controls the icon, label, subtitle and state, not the tile's shape or animation.
 * They only run while the Quick Settings panel is open: no background work.
 */

/** Opens an activity from a tile, collapsing the shade (Android 14+ requires a PendingIntent). */
internal fun TileService.launch(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val pi = PendingIntent.getActivity(this, intent.component.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startActivityAndCollapse(pi)
    } else {
        @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
        startActivityAndCollapse(intent)
    }
}

internal fun Tile.render(state: Int, subtitle: CharSequence? = null) {
    this.state = state
    this.subtitle = subtitle
    updateTile()
}

/** Real flashlight toggle (CameraManager torch), state follows the system torch. */
class NexusTorchTile : TileService() {
    private var torch: TorchController? = null
    private var job: kotlinx.coroutines.Job? = null

    override fun onStartListening() {
        val t = torch ?: TorchController(this).also { torch = it }
        if (!t.available) { qsTile?.render(Tile.STATE_UNAVAILABLE, "No flash"); return }
        t.start()
        job = nexus.appScope.launch {
            t.on.collect { on -> qsTile?.render(if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE, if (on) "On" else "Off") }
        }
    }

    override fun onStopListening() {
        job?.cancel(); job = null
        torch?.stop()
    }

    override fun onClick() {
        torch?.toggle()
    }
}

/** Battery-saving graphics: switches NEXUS to its Battery Saver render mode and back. */
class NexusEcoTile : TileService() {
    private val prefs get() = getSharedPreferences("nexus_tiles", Context.MODE_PRIVATE)

    override fun onStartListening() {
        nexus.appScope.launch {
            val mode = nexus.store.config.first().perfMode
            val on = mode == PerfMode.BATTERY_SAVER
            qsTile?.render(if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE, if (on) "Low-power FX" else "Full FX")
        }
    }

    override fun onClick() {
        nexus.appScope.launch {
            nexus.store.updateConfig { c ->
                if (c.perfMode == PerfMode.BATTERY_SAVER) {
                    val prev = runCatching { PerfMode.valueOf(prefs.getString(KEY_PREV, PerfMode.AUTO.name)!!) }.getOrDefault(PerfMode.AUTO)
                    c.copy(perfMode = if (prev == PerfMode.BATTERY_SAVER) PerfMode.AUTO else prev)
                } else {
                    prefs.edit().putString(KEY_PREV, c.perfMode.name).apply()
                    c.copy(perfMode = PerfMode.BATTERY_SAVER)
                }
            }
            onStartListening()
        }
    }

    private companion object { const val KEY_PREV = "eco_previous_mode" }
}

class NexusCalculatorTile : TileService() {
    override fun onStartListening() { qsTile?.render(Tile.STATE_INACTIVE) }
    override fun onClick() = afterUnlock { launch(Intent(this, CalculatorActivity::class.java)) }
}

class NexusSettingsHubTile : TileService() {
    override fun onStartListening() { qsTile?.render(Tile.STATE_INACTIVE) }
    override fun onClick() = afterUnlock { launch(Intent(this, SettingsHubActivity::class.java)) }
}

/** Opens NEXUS//SHELL on the home screen. Only offered when NEXUS is the Home app. */
class NexusShellTile : TileService() {
    override fun onStartListening() {
        val home = SystemControls.isDefaultHome(this)
        qsTile?.render(if (home) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE, if (home) null else "Set NEXUS as Home")
    }

    override fun onClick() {
        if (!SystemControls.isDefaultHome(this)) return
        afterUnlock { launch(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "console")) }
    }
}

/** Activities must not open over the secure lock screen: Android's own unlock runs first. */
private fun TileService.afterUnlock(block: () -> Unit) {
    if (isLocked) unlockAndRun(Runnable { block() }) else block()
}
