package com.nexus.launcher.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.core.NexusConfig
import com.nexus.launcher.nexus
import com.nexus.launcher.notifications.NotificationHub
import com.nexus.launcher.ui.ambient.AmbientFace
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.components.SliderRow
import com.nexus.launcher.ui.fx.FingerprintFx
import com.nexus.launcher.ui.fx.NexusBackground
import com.nexus.launcher.ui.fx.NexusClock
import com.nexus.launcher.ui.theme.LocalNexus
import com.nexus.launcher.ui.theme.NexusHost
import com.nexus.launcher.ui.theme.SurfaceAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Optional NEXUS lock layer. It is shown ON TOP of Android's secure lock screen when the
 * screen turns on; swiping up (or touching the fingerprint target) asks Android to dismiss
 * the keyguard, which still runs Android's own PIN / pattern / fingerprint check.
 * It never replaces or weakens the system lock — it is a skin in front of it.
 */
class LockLayerActivity : ComponentActivity() {

    private val app get() = applicationContext.nexus
    private var scanning by mutableStateOf(false)
    private var ambient by mutableStateOf(false)
    private var lastTouch = System.currentTimeMillis()
    private val calibrate get() = intent?.getBooleanExtra(EXTRA_CALIBRATE, false) == true

    private val presentReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { finishAndRemoveTask() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        ContextCompat.registerReceiver(this, presentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (calibrate) finish() else if (ambient) wake() }
        })

        setContent {
            NexusHost { cfg ->
                SurfaceAccent(cfg.lockAccent) {
                    if (calibrate) Calibration(cfg) else LockScreen(cfg)
                }
            }
        }

        if (!calibrate) lifecycleScope.launch {
            while (true) {
                delay(1000)
                val cfg = app.config.value ?: continue
                if (cfg.aodOnLockLayer && !ambient && !scanning && System.currentTimeMillis() - lastTouch > 10_000) enterAmbient(cfg)
            }
        }
    }

    override fun onStart() { super.onStart(); app.system.startForeground() }
    override fun onStop() { app.system.stopForeground(); super.onStop() }

    override fun onDestroy() {
        runCatching { unregisterReceiver(presentReceiver) }
        super.onDestroy()
    }

    /** Hands over to Android's secure unlock (PIN / pattern / fingerprint / face). */
    private fun unlock() {
        scanning = true
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) { finishAndRemoveTask(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { finishAndRemoveTask() }
            override fun onDismissCancelled() { scanning = false }
            override fun onDismissError() { scanning = false }
        })
    }

    private fun enterAmbient(cfg: NexusConfig) {
        ambient = true
        window.attributes = window.attributes.apply { screenBrightness = 0.02f }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            delay(cfg.aodMinutes.coerceIn(1, 60) * 60_000L)
            // let the phone sleep normally after the chosen time
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun wake() {
        lastTouch = System.currentTimeMillis()
        if (!ambient) return
        ambient = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }

    @Composable
    private fun LockScreen(cfg: NexusConfig) {
        val nx = LocalNexus.current
        val p = nx.palette
        val battery by app.system.battery.collectAsState()
        val notes by NotificationHub.items.collectAsState()
        AnimatedContent(ambient, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "aod") { isAmbient ->
            if (isAmbient) {
                AmbientFace(
                    cfg.aodStyle, cfg.aodClockStyle, battery, notes, cfg.aodNotifications, cfg.aodDim,
                    Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { wake() } },
                )
            } else {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black)
                        .pointerInput(Unit) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f; lastTouch = System.currentTimeMillis() },
                                onVerticalDrag = { c, d -> c.consume(); total += d },
                                onDragEnd = { if (total < -120f) unlock() },
                            )
                        }
                        .pointerInput(Unit) { detectTapGestures { lastTouch = System.currentTimeMillis() } }
                ) {
                    NexusBackground(animate = true, modifier = Modifier.fillMaxSize())
                    Column(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(48.dp))
                        Text("// NEXUS SECURE LAYER", style = nx.label(9.5f, p.accent))
                        Spacer(Modifier.height(18.dp))
                        NexusClock(cfg.lockClockStyle, Modifier.fillMaxWidth(0.85f), seconds = true)
                        Spacer(Modifier.height(18.dp))
                        val count = notes.count { !it.ongoing }
                        if (count > 0) Text(
                            "$count SIGNALS · " + notes.filter { !it.ongoing }.map { it.appLabel.uppercase() }.distinct().take(3).joinToString(" · "),
                            style = nx.label(10f, p.text.copy(alpha = 0.75f)),
                        )
                        Text("POWER ${battery.level}%${if (battery.charging) " · CHARGING" else ""}", style = nx.label(9.5f, p.textDim))
                    }
                    // fingerprint target at the calibrated position
                    val density = LocalDensity.current
                    val sizePx = with(density) { cfg.fpSize.dp.toPx() } * 2.2f
                    val wPx = constraints.maxWidth.toFloat()
                    val hPx = constraints.maxHeight.toFloat()
                    FingerprintFx(
                        cfg.fingerprintStyle, scanning,
                        Modifier
                            .offset { IntOffset((cfg.fpX * wPx - sizePx / 2).roundToInt(), (cfg.fpY * hPx - sizePx / 2).roundToInt()) }
                            .size(with(density) { sizePx.toDp() })
                            .pointerInput(Unit) { detectTapGestures { unlock() } },
                    )
                    Text(
                        if (scanning) "AUTHENTICATING…" else "SWIPE UP · OR TOUCH THE SENSOR",
                        style = nx.label(9f, p.textDim),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                    )
                }
            }
        }
    }

    /** Drag the ring over your phone's in-display sensor; set its size; save. */
    @Composable
    private fun Calibration(cfg: NexusConfig) {
        val nx = LocalNexus.current
        val p = nx.palette
        var fx by remember { mutableFloatStateOf(cfg.fpX) }
        var fy by remember { mutableFloatStateOf(cfg.fpY) }
        var size by remember { mutableFloatStateOf(cfg.fpSize) }
        BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            val density = LocalDensity.current
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val sPx = with(density) { size.dp.toPx() } * 2.2f
            FingerprintFx(
                cfg.fingerprintStyle, true,
                Modifier
                    .offset { IntOffset((fx * wPx - sPx / 2).roundToInt(), (fy * hPx - sPx / 2).roundToInt()) }
                    .size(with(density) { sPx.toDp() })
                    .pointerInput(wPx, hPx) {
                        detectDragGestures { c, d ->
                            c.consume()
                            fx = (fx + d.x / wPx).coerceIn(0.05f, 0.95f)
                            fy = (fy + d.y / hPx).coerceIn(0.05f, 0.98f)
                        }
                    },
            )
            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("// SENSOR CALIBRATION", style = nx.label(10f, p.accent))
                Text("Drag the ring so it sits exactly over your phone's fingerprint sensor (touch the sensor once with the screen off to see where it is).", style = nx.body(13f, color = p.textDim))
                SliderRow("Ring size", size, 40f..110f, { size = it }, { "${it.roundToInt()}dp" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NxButton("Save", {
                        lifecycleScope.launch {
                            app.store.updateConfig { it.copy(fpX = fx, fpY = fy, fpSize = size) }
                            finish()
                        }
                    }, primary = true)
                    NxButton("Cancel", { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_CALIBRATE = "calibrate"

        /** Shows the layer if enabled, the phone is locked and "Display over other apps" is granted. */
        fun maybeShow(context: Context) {
            val cfg = context.nexus.config.value ?: return
            if (!cfg.lockLayer) return
            val km = context.getSystemService(KeyguardManager::class.java)
            if (!km.isKeyguardLocked) return
            if (!Settings.canDrawOverlays(context)) return
            runCatching {
                context.startActivity(Intent(context, LockLayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
        }
    }
}
