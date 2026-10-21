package com.nexus.launcher.core

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Installed-app discovery through [LauncherApps] — the official launcher API. Handles work
 * profiles and live install / uninstall / update events.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Fires for real uninstalls only (not for packages that are just temporarily unavailable). */
    private val _removed = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 16)
    val removed: SharedFlow<Pair<String, Long>> = _removed

    /** Any package change — used to invalidate cached icons. */
    private val _changed = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val changed: SharedFlow<String> = _changed

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            _changed.tryEmit(packageName)
            _removed.tryEmit(packageName to serialOf(user))
            refresh()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            _changed.tryEmit(packageName); refresh()
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            _changed.tryEmit(packageName); refresh()
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { _changed.tryEmit(it) }; refresh()
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { _changed.tryEmit(it) }; refresh()
        }
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch {
            for (r in refreshRequests) {
                _apps.value = load()
                _loaded.value = true
                delay(200) // coalesce bursts (e.g. app updates touching several packages)
            }
        }
        refresh()
    }

    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    private fun load(): List<AppInfo> {
        val primary = Process.myUserHandle()
        val result = ArrayList<AppInfo>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val list = try {
                launcherApps.getActivityList(null, user)
            } catch (e: Exception) {
                Log.w(TAG, "getActivityList failed for $user", e); emptyList()
            }
            for (lai in list) {
                val cn = lai.componentName
                result += AppInfo(
                    key = AppKey(cn.packageName, cn.className, serial),
                    label = lai.label?.toString().orEmpty().ifBlank { cn.packageName },
                    component = cn,
                    user = user,
                    isWork = user != primary,
                    info = lai,
                )
            }
        }
        return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** Starts an app. [sourceBounds] are screen coordinates of the tapped icon. */
    fun launch(app: AppInfo, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, options)
        true
    } catch (e: Exception) {
        Log.w(TAG, "launch failed: ${app.key.id}", e); false
    }

    fun openAppDetails(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    /** App shortcuts (long-press actions). Only available while NEXUS is the default launcher. */
    fun shortcuts(app: AppInfo): List<ShortcutInfo> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.component.packageName)
            .setActivity(app.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        return runCatching { launcherApps.getShortcuts(q, app.user).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedBy { it.rank }
            .take(5)
    }

    fun startShortcut(s: ShortcutInfo, bounds: Rect?) {
        runCatching { launcherApps.startShortcut(s, bounds, null) }
            .onFailure { Log.w(TAG, "shortcut failed", it) }
    }

    fun shortcutIcon(s: ShortcutInfo): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(s, context.resources.displayMetrics.densityDpi) }.getOrNull()

    companion object {
        private const val TAG = "NexusApps"
    }
}
