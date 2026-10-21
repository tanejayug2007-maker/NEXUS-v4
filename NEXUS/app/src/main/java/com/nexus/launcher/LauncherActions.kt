package com.nexus.launcher

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ShortcutInfo
import android.view.View
import androidx.compose.ui.geometry.Rect
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.GestureAction
import com.nexus.launcher.notifications.NexusNotification
import com.nexus.launcher.system.TorchController

/** Everything the Compose UI asks the Activity to do (system integration lives there). */
interface LauncherActions {
    val torch: TorchController

    /** [bounds] are the tapped icon's window bounds; Rect.Zero when unknown (keyboard launch). */
    fun launchApp(app: AppInfo, bounds: Rect)
    fun shortcutsFor(app: AppInfo): List<ShortcutInfo>
    fun launchShortcut(s: ShortcutInfo, bounds: Rect)
    fun appInfo(app: AppInfo)
    fun uninstall(app: AppInfo)

    /** System-level gesture actions (shade, QS, lock, flashlight, studio, assistant, recents). */
    fun systemGesture(action: GestureAction)

    fun widgetProviders(): List<AppWidgetProviderInfo>
    fun pickWidget(info: AppWidgetProviderInfo)
    fun widgetView(id: Int): View
    fun widgetSize(id: Int, widthDp: Int, heightDp: Int)
    fun deleteWidget(id: Int)

    fun pickIcon(app: AppInfo)
    fun openStudio()
    fun openWallpaperPicker()
    fun webSearch(query: String)
    fun requestWeather()
    fun openNotificationAccess()
    fun openNotification(n: NexusNotification)
    fun openApp(pkg: String)
    fun openClockApp()
    fun openCalendar()
    fun pickWidgetPhoto(itemId: String)
}

/** Lets home widgets (toggles, favourites, search) reach launcher actions without plumbing. */
val LocalLauncherActions = androidx.compose.runtime.staticCompositionLocalOf<LauncherActions?> { null }
