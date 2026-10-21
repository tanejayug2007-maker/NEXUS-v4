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

    override fun onCreate() {
        super.onCreate()
        store = ConfigStore(this)
        icons = IconEngine(this)
        apps = AppRepository(this)
        system = SystemMonitor(this).also { it.startGlobal() }
        weather = WeatherRepository(this)
        config = store.config.stateIn(appScope, SharingStarted.Eagerly, null)

        appScope.launch { apps.changed.collect { icons.invalidatePackage(it) } }
        appScope.launch { system.screenOn.collect { com.nexus.launcher.lock.LockLayerActivity.maybeShow(this@NexusApp) } }

        // One-time move to the v2 "Helix" design for configs created by older builds (and fresh installs).
        appScope.launch {
            store.updateConfig { c ->
                if (c.designVersion >= DESIGN_VERSION) c
                else c.copy(
                    theme = ThemePresets.HelixPrime, presetName = "Helix Prime",
                    interfaceMode = InterfaceMode.OPERATOR, designVersion = DESIGN_VERSION,
                    launchAnim = LaunchAnim.HYPERSPACE_JUMP, unlockAnim = UnlockAnim.IRIS_SCAN,
                )
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        icons.trim(level)
    }
}

const val DESIGN_VERSION = 2

val Context.nexus: NexusApp get() = applicationContext as NexusApp
