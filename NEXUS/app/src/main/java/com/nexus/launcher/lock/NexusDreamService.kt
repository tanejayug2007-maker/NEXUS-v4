package com.nexus.launcher.lock

import android.service.dreams.DreamService
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nexus.launcher.ui.fx.ChargingVisual
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nexus.launcher.nexus
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.ui.ambient.AmbientFace
import com.nexus.launcher.ui.theme.NexusHost
import com.nexus.launcher.ui.theme.SurfaceAccent

/**
 * NEXUS always-on face as an Android screen saver ("Daydream"). This is the official way for
 * an app to draw while the phone is charging or docked: Settings › Display › Screen saver ›
 * NEXUS Ambient, "When to start: While charging". Android's own AOD is not replaceable.
 */
class NexusDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NexusDreamService)
            setViewTreeSavedStateRegistryOwner(this@NexusDreamService)
            setContent {
                NexusHost { cfg ->
                    val battery by applicationContext.nexus.system.battery.collectAsState()
                    val notes by NotificationHub.items.collectAsState()
                    val quality = LocalNexus.current.quality
                    // Charging intro: the NEXUS charging animation for a few seconds, then the
                    // low-power ambient face. Skipped in Battery Saver / reduced motion.
                    var intro by remember {
                        mutableStateOf(cfg.dreamChargeIntroSec > 0 && battery.charging && quality.tier > 0)
                    }
                    LaunchedEffect(intro) {
                        if (intro) { delay(cfg.dreamChargeIntroSec.coerceIn(3, 30) * 1000L); intro = false }
                    }
                    Crossfade(intro, animationSpec = tween(900), label = "dreamIntro") { showIntro ->
                        if (showIntro) {
                            SurfaceAccent(cfg.chargeAccent) {
                                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                                    ChargingVisual(battery, cfg.chargingAnim, cfg.chargingIntensity, Modifier.fillMaxSize())
                                }
                            }
                        } else {
                            SurfaceAccent(cfg.aodAccent) {
                                AmbientFace(cfg.aodStyle, cfg.aodClockStyle, battery, notes, cfg.aodNotifications, cfg.aodDim)
                            }
                        }
                    }
                }
            }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        applicationContext.nexus.system.startForeground()
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        registry.currentState = Lifecycle.State.CREATED
        applicationContext.nexus.system.stopForeground()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
