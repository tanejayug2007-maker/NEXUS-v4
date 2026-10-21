package com.nexus.launcher

import android.app.Application
import android.content.Context
import com.nexus.launcher.core.AppRepository
import com.nexus.launcher.core.ConfigStore
import com.nexus.launcher.core.IconEngine
import com.nexus.launcher.core.InterfaceMode
import com.nexus.launcher.core.LaunchAnim
import com.nexus.launcher.core.ThemePresets
import com.nexus.launcher.core.UnlockAnim
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.core.SystemMonitor
import com.nexus.launcher.core.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Process-wide singletons. Kept deliberately small: no DI framework, no background services. */
class NexusApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var store: ConfigStore
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var icons: IconEngine
        private set
    lateinit var system: SystemMonitor
        private set
    lateinit var weather: WeatherRepository
        private set

    /** null until the first DataStore read completes (avoids flashing default theme). */
    lateinit var config: StateFlow<NexusConfig?>
        private set

    /**
     * Every app NOT in the Private Space: what drawer, search, orbit, radial, home pages and widgets
     * see. [privateApps] is only read by the (authenticated) Private Space screen.
     */
    lateinit var publicApps: StateFlow<List<com.nexus.launcher.core.AppInfo>>
        private set
    lateinit var privateApps: StateFlow<List<com.nexus.launcher.core.AppInfo>>
        private set

    override fun onCreate() {
        super.onCreate()
        store = ConfigStore(this)
        icons = IconEngine(this)
        apps = AppRepository(this)
        system = SystemMonitor(this).also { it.startGlobal() }
        weather = WeatherRepository(this)
        config = store.config.stateIn(appScope, SharingStarted.Eagerly, null)
        // Waits for the real config: never shows a private app publicly while settings are still loading.
        val privateIds = config.filterNotNull().map { it.privateApps }.distinctUntilChanged()
        publicApps = combine(apps.apps, privateIds) { list, priv -> if (priv.isEmpty()) list else list.filter { it.key.id !in priv } }
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())
        privateApps = combine(apps.apps, privateIds) { list, priv -> list.filter { it.key.id in priv } }
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())

        appScope.launch { apps.changed.collect { icons.invalidatePackage(it) } }
        appScope.launch { system.screenOn.collect { com.nexus.launcher.lock.LockLayerActivity.maybeShow(this@NexusApp) } }

        // One-time move to the v2 "Helix" design for configs created by older builds (and fresh installs).
        appScope.launch {
            store.updateConfig { c -> migrateDesign(c) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        icons.trim(level)
    }
}

const val DESIGN_VERSION = 3

/** Control Deck modules every build before milestone 6 saved by default. */
internal val LEGACY_DECK_DEFAULT = listOf(
    "DIALS", "BATTERY", "WIFI", "BLUETOOTH", "MOBILE", "FLASHLIGHT", "DND", "ROTATION", "LOCATION", "NFC", "SHADE", "SETTINGS",
)

/**
 * One-time design upgrades for configs saved by older builds (and fresh installs).
 *  v2: the "Helix" design.
 *  v3: the Control Deck reference layout (eight tiles) — only if the deck was never customised.
 */
internal fun migrateDesign(c0: com.nexus.launcher.core.NexusConfig): com.nexus.launcher.core.NexusConfig {
    var c = c0
    if (c.designVersion < 2) c = c.copy(
        theme = ThemePresets.HelixPrime, presetName = "Helix Prime",
        interfaceMode = InterfaceMode.OPERATOR, designVersion = 2,
        launchAnim = LaunchAnim.HYPERSPACE_JUMP, unlockAnim = UnlockAnim.IRIS_SCAN,
    )
    if (c.designVersion < 3) c = c.copy(
        controlModules = if (c.controlModules == LEGACY_DECK_DEFAULT) com.nexus.launcher.core.ControlModules.defaultOrder else c.controlModules,
        designVersion = 3,
    )
    return c
}

val Context.nexus: NexusApp get() = applicationContext as NexusApp
