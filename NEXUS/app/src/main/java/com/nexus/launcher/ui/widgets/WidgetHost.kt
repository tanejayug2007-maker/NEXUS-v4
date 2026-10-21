package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.UserManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs

/**
 * Host view that adds launcher-style long-press detection on top of the widget's own
 * touch handling (widgets consume touches, so Compose can't see the long press).
 */
class NexusWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    private val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val trigger = Runnable {
        fired = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
        // From now on onInterceptTouchEvent returns true, so the framework sends
        // ACTION_CANCEL to the widget's children and the press never becomes a click.
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false; downX = ev.x; downY = ev.y
                postDelayed(trigger, timeout)
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(trigger)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(trigger)
        }
        return fired
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) removeCallbacks(trigger)
        return fired || super.onTouchEvent(event)
    }
}

/**
 * Real AppWidgetHost. Views are cached by widget id so pages can be recomposed (pager,
 * config changes) without re-inflating RemoteViews.
 */
class NexusWidgetHost(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val views = HashMap<Int, AppWidgetHostView>()
    var onWidgetLongPress: ((Int) -> Unit)? = null

    val host: AppWidgetHost = object : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(ctx: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            NexusWidgetHostView(ctx)
    }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun delete(id: Int) {
        views.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun view(id: Int): View? {
        views[id]?.let { v -> (v.parent as? ViewGroup)?.removeView(v); return v }
        val info = info(id) ?: return null
        val v = host.createView(context, id, info)
        (v as? NexusWidgetHostView)?.onLongPress = { onWidgetLongPress?.invoke(id) }
        views[id] = v
        return v
    }

    /** Tells the widget how big it actually is so it can pick the right layout. */
    fun updateSize(id: Int, widthDp: Int, heightDp: Int) {
        val opts = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        runCatching { manager.updateAppWidgetOptions(id, opts) }
    }

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    fun providers(): List<AppWidgetProviderInfo> {
        val um = context.getSystemService(UserManager::class.java)
        return um.userProfiles.flatMap { u -> runCatching { manager.getInstalledProvidersForProfile(u) }.getOrDefault(emptyList()) }
    }

    fun clearViews() = views.clear()

    companion object {
        const val HOST_ID = 2050

        fun flatten(cn: ComponentName): String = cn.flattenToString()
    }
}
