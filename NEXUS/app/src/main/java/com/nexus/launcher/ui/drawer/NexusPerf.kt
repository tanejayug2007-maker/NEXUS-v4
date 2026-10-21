package com.nexus.launcher.ui.drawer

import android.util.Log

/**
 * Opt-in gesture timing for on-device measurement (Studio › Performance › Frame stats turns it
 * on together with the frame overlay). Lines go to logcat under the tag "NexusPerf", which
 * tools/phone-test/nexus-perf.ps1 collects. Off: one boolean check, nothing allocated.
 */
object NexusPerf {
    @Volatile var enabled: Boolean = false

    fun log(message: () -> String) {
        if (enabled) Log.i("NexusPerf", message())
    }
}
