package com.nexus.launcher.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Optional, user-enabled helper. It reads NO screen content (canRetrieveWindowContent=false)
 * and only performs global actions the user mapped to gestures: lock, shade, Quick Settings, recents.
 */
class NexusAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ref = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ref = null
        super.onDestroy()
    }

    companion object {
        private var ref: WeakReference<NexusAccessibilityService>? = null
        val isRunning: Boolean get() = ref?.get() != null

        fun perform(action: Int): Boolean = ref?.get()?.performGlobalAction(action) ?: false
    }
}
