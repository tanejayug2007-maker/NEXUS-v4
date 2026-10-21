package com.nexus.launcher.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.role.RoleManager
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.nexus.launcher.notifications.NexusNotificationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Every system control NEXUS offers, implemented only with public APIs. When Android does not
 * allow a third-party app to toggle something directly (Wi-Fi, Bluetooth, mobile data, NFC on
 * Android 10+), NEXUS opens the official system panel instead of showing a fake toggle.
 */
object SystemControls {

    // ---- default launcher -------------------------------------------------------------------

    fun isDefaultHome(context: Context): Boolean {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val res = context.packageManager.resolveActivity(intent, 0)
        return res?.activityInfo?.packageName == context.packageName
    }

    /** Intent that asks the user to make NEXUS the Home app (system dialog, or Settings fallback). */
    fun defaultHomeIntent(context: Context): Intent {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    // ---- permissions ------------------------------------------------------------------------

    fun notificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun notificationAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, NexusNotificationService::class.java).flattenToString(),
            )
        } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun accessibilityEnabled(context: Context): Boolean {
        if (NexusAccessibilityService.isRunning) return true
        val flat = ComponentName(context, NexusAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun dndAccessGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    // ---- launching system UI ----------------------------------------------------------------

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun openPanel(context: Context, panelAction: String, fallback: String) {
        if (!start(context, Intent(panelAction))) start(context, Intent(fallback))
    }

    fun openWifi(context: Context) = openPanel(context, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
    fun openInternet(context: Context) = openPanel(context, Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS)
    fun openNfc(context: Context) = openPanel(context, Settings.Panel.ACTION_NFC, Settings.ACTION_NFC_SETTINGS)
    fun openVolumePanel(context: Context) = openPanel(context, Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS)
    fun openBluetooth(context: Context) { start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    fun openLocation(context: Context) { start(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    fun openSettings(context: Context) { start(context, Intent(Settings.ACTION_SETTINGS)) }
    fun openDisplay(context: Context) { start(context, Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
    fun openBattery(context: Context) {
        if (!start(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))) start(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    }

    /** Real notification shade. Prefers the accessibility helper; falls back to StatusBarManager. */
    fun expandNotifications(context: Context): Boolean =
        NexusAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) || statusBar(context, "expandNotificationsPanel")

    fun expandQuickSettings(context: Context): Boolean =
        NexusAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) || statusBar(context, "expandSettingsPanel")

    fun recents(): Boolean = NexusAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_RECENTS)

    fun lockScreen(context: Context): Boolean {
        val ok = NexusAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        if (!ok) toast(context, "Enable “NEXUS gesture helper” in Studio › System to lock with a gesture")
        return ok
    }

    @SuppressLint("WrongConstant")
    private fun statusBar(context: Context, method: String): Boolean = runCatching {
        val sbm = context.getSystemService("statusbar") ?: return false
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(sbm)
        true
    }.getOrDefault(false)

    fun assistant(context: Context) {
        if (!start(context, Intent(Intent.ACTION_ASSIST))) start(context, Intent(Intent.ACTION_VOICE_COMMAND))
    }

    fun toast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }

    // ---- state readers (no special permissions) ---------------------------------------------

    fun wifiEnabled(context: Context): Boolean =
        runCatching { context.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun bluetoothEnabled(context: Context): Boolean =
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)

    fun nfcState(context: Context): Boolean? =
        runCatching { NfcAdapter.getDefaultAdapter(context)?.isEnabled }.getOrNull()

    fun locationEnabled(context: Context): Boolean =
        runCatching { context.getSystemService(LocationManager::class.java).isLocationEnabled }.getOrDefault(false)

    fun airplaneOn(context: Context): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1 }.getOrDefault(false)

    fun powerSaveOn(context: Context): Boolean =
        runCatching { context.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode }.getOrDefault(false)

    /** System dark theme currently in effect (whatever turned it on: manual, schedule or battery saver). */
    fun darkModeOn(context: Context): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    // Third-party apps can't toggle these on Android 10+: open the official screen instead.
    fun openAirplane(context: Context) { start(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
    fun openBatterySaver(context: Context) {
        if (!start(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))) openBattery(context)
    }
    fun openHotspot(context: Context) {
        if (!start(context, Intent("android.settings.TETHER_SETTINGS"))) start(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
    fun openDarkMode(context: Context) {
        if (!start(context, Intent("android.settings.DARK_THEME_SETTINGS"))) openDisplay(context)
    }

    // ---- real toggles -----------------------------------------------------------------------

    fun dndOn(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL

    /** Toggles Do Not Disturb if the user granted DND access, otherwise opens that permission screen. */
    fun toggleDnd(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); return
        }
        nm.setInterruptionFilter(
            if (dndOn(context)) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
    }

    fun brightness(context: Context): Float {
        val v = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
        return (v / 255f).coerceIn(0f, 1f)
    }

    fun autoBrightness(context: Context): Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    /** Requires the user-granted "Modify system settings" special access. */
    fun setBrightness(context: Context, value: Float): Boolean {
        if (!canWriteSettings(context)) return false
        return runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (value.coerceIn(0.01f, 1f) * 255).toInt())
        }.isSuccess
    }

    fun setAutoBrightness(context: Context, on: Boolean): Boolean {
        if (!canWriteSettings(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
        }.isSuccess
    }

    fun autoRotate(context: Context): Boolean =
        runCatching { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1 }.getOrDefault(false)

    fun setAutoRotate(context: Context, on: Boolean): Boolean {
        if (!canWriteSettings(context)) return false
        return runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0) }.isSuccess
    }

    fun mediaVolume(context: Context): Float {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setMediaVolume(context: Context, value: Float) {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (value.coerceIn(0f, 1f) * max).toInt(), 0) }
    }
}

/** Flashlight via CameraManager.setTorchMode — a real toggle, no camera permission required. */
class TorchController(context: Context) {
    private val cm = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = runCatching {
        cm.cameraIdList.firstOrNull { id ->
            val ch = cm.getCameraCharacteristics(id)
            ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on
    val available: Boolean get() = cameraId != null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) _on.value = enabled
        }
    }
    private var registered = false

    fun start() {
        if (!registered && cameraId != null) {
            runCatching { cm.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
            registered = true
        }
    }

    fun stop() {
        if (registered) { runCatching { cm.unregisterTorchCallback(callback) }; registered = false }
    }

    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return runCatching { cm.setTorchMode(id, !_on.value); _on.value = !_on.value }.isSuccess
    }
}
