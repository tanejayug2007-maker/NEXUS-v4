package com.nexus.launcher.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class BatteryState(
    val level: Int = 0,
    val charging: Boolean = false,
    val full: Boolean = false,
    val source: String = "",
    val temperatureC: Float = 0f,
    val voltageMv: Int = 0,
    val health: String = "",
)

enum class NetType { NONE, WIFI, CELLULAR, ETHERNET, VPN, OTHER }

data class NetState(
    val type: NetType = NetType.NONE,
    val validated: Boolean = false,
    val signal: Int? = null,
    val metered: Boolean = false,
)

/** Rendering budget derived from performance mode, power-saver, thermal state and system animation scale. */
data class FxQuality(
    val tier: Int,                 // 0 = battery saver, 1 = balanced, 2 = ultra
    val particles: Float,          // multiplier for particle counts
    val ambientContinuous: Boolean,// background keeps animating while idle
    val ambientEnabled: Boolean,   // background animates at all
    val glow: Boolean,
    val blur: Boolean,
    val heavyFx: Boolean,          // full launch/unlock/charging effects
    val reason: String,
) {
    companion object {
        fun compute(mode: PerfMode, powerSave: Boolean, thermal: Int, reduceMotion: Boolean): FxQuality {
            val hot = thermal >= PowerManager.THERMAL_STATUS_MODERATE
            val tier = when {
                reduceMotion -> 0
                mode == PerfMode.BATTERY_SAVER -> 0
                mode == PerfMode.AUTO && (powerSave || hot) -> 0
                mode == PerfMode.ULTRA && thermal >= PowerManager.THERMAL_STATUS_SEVERE -> 1
                mode == PerfMode.ULTRA -> 2
                else -> 1
            }
            val reason = when {
                reduceMotion -> "System animations off"
                mode == PerfMode.AUTO && powerSave -> "Battery saver active"
                mode == PerfMode.AUTO && hot -> "Device warm — effects reduced"
                mode == PerfMode.ULTRA && tier == 1 -> "Device hot — Ultra throttled"
                else -> mode.pretty()
            }
            return when (tier) {
                2 -> FxQuality(2, 1f, ambientContinuous = true, ambientEnabled = true, glow = true, blur = true, heavyFx = true, reason = reason)
                1 -> FxQuality(1, 0.6f, ambientContinuous = false, ambientEnabled = true, glow = true, blur = true, heavyFx = true, reason = reason)
                else -> FxQuality(0, 0f, ambientContinuous = false, ambientEnabled = false, glow = true, blur = false, heavyFx = false, reason = reason)
            }
        }
    }
}

/**
 * Event-driven device state. Nothing here polls or holds wake locks:
 *  - lightweight global receivers (unlock, screen off, power connect, power-saver) live for the process,
 *  - the battery-level receiver is only registered while the launcher is in the foreground.
 */
class SystemMonitor(private val context: Context) {

    private val pm = context.getSystemService(PowerManager::class.java)
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _battery = MutableStateFlow(BatteryState())
    val battery: StateFlow<BatteryState> = _battery

    private val _net = MutableStateFlow(NetState())
    val net: StateFlow<NetState> = _net

    private val _powerSave = MutableStateFlow(false)
    val powerSave: StateFlow<Boolean> = _powerSave

    private val _thermal = MutableStateFlow(PowerManager.THERMAL_STATUS_NONE)
    val thermal: StateFlow<Int> = _thermal

    private val _reduceMotion = MutableStateFlow(false)
    val reduceMotion: StateFlow<Boolean> = _reduceMotion

    private val _userPresent = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val userPresent: SharedFlow<Long> = _userPresent

    private val _screenOn = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    /** Screen turned on (used to show the optional NEXUS lock layer). */
    val screenOn: SharedFlow<Long> = _screenOn

    private val _powerConnected = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val powerConnected: SharedFlow<Long> = _powerConnected

    @Volatile var lastScreenOff: Long = 0L
        private set
    @Volatile var lastPowerConnected: Long = 0L
        private set

    private val globalReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val now = SystemClock.elapsedRealtime()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> lastScreenOff = now
                Intent.ACTION_SCREEN_ON -> _screenOn.tryEmit(now)
                Intent.ACTION_USER_PRESENT -> _userPresent.tryEmit(now)
                Intent.ACTION_POWER_CONNECTED -> {
                    lastPowerConnected = now
                    _battery.value = _battery.value.copy(charging = true)
                    _powerConnected.tryEmit(now)
                }
                Intent.ACTION_POWER_DISCONNECTED -> _battery.value = _battery.value.copy(charging = false, source = "")
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> _powerSave.value = pm.isPowerSaveMode
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = parseBattery(intent)
    }
    private var batteryRegistered = false
    private var foregroundRefs = 0

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetType.VPN
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetType.ETHERNET
                else -> NetType.OTHER
            }
            val sig = caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
            _net.value = NetState(
                type = type,
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                signal = sig,
                metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            )
        }

        override fun onLost(network: Network) {
            _net.value = NetState()
        }
    }

    fun startGlobal() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, globalReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        _powerSave.value = pm.isPowerSaveMode
        _thermal.value = pm.currentThermalStatus
        pm.addThermalStatusListener(ContextCompat.getMainExecutor(context)) { status -> _thermal.value = status }
        runCatching { cm.registerDefaultNetworkCallback(netCallback) }
        readBatteryNow()
        refreshReduceMotion()
    }

    /** Called from onStart of the launcher (and other NEXUS screens). Ref-counted. */
    @Synchronized
    fun startForeground() {
        refreshReduceMotion()
        foregroundRefs++
        if (batteryRegistered) return
        val sticky = ContextCompat.registerReceiver(
            context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let { parseBattery(it) }
        batteryRegistered = true
    }

    /** Called from onStop. The receiver is removed when the last NEXUS screen stops. */
    @Synchronized
    fun stopForeground() {
        foregroundRefs = (foregroundRefs - 1).coerceAtLeast(0)
        if (foregroundRefs > 0 || !batteryRegistered) return
        runCatching { context.unregisterReceiver(batteryReceiver) }
        batteryRegistered = false
    }

    fun refreshReduceMotion() {
        _reduceMotion.value = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    fun readBatteryNow() {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { parseBattery(it) }
    }

    private fun parseBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else _battery.value.level
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "WIRELESS"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && plugged and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> "DOCK"
            else -> ""
        }
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE"
            else -> "UNKNOWN"
        }
        _battery.value = BatteryState(
            level = pct.coerceIn(0, 100),
            charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING,
            full = status == BatteryManager.BATTERY_STATUS_FULL,
            source = source,
            temperatureC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            health = health,
        )
    }
}
