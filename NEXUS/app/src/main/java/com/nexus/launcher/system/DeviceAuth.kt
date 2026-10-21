package com.nexus.launcher.system

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import com.nexus.launcher.core.PrivatePin

/**
 * "Phone lock" for the Private Space: Android's own BiometricPrompt, allowing fingerprint/face or the
 * phone's PIN / pattern / password. NEXUS never sees the credential, only "succeeded" or not.
 * Needs only the normal USE_BIOMETRIC permission (granted at install, no prompt).
 */
object DeviceAuth {

    /** A screen lock exists, so DEVICE unlock can work at all. */
    fun available(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun authenticate(activity: Activity, title: String, onResult: (ok: Boolean, error: String?) -> Unit): CancellationSignal {
        val cancel = CancellationSignal()
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle("NEXUS Private Space")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
        }
        runCatching {
            builder.build().authenticate(cancel, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(true, null)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false, errString?.toString())
            })
        }.onFailure { onResult(false, it.message) }
        return cancel
    }
}

/**
 * Wrong-PIN back-off for this process (see PrivatePin.lockoutMillis). Kept in memory on purpose:
 * it slows down guessing at the phone; the PIN itself is only stored as a PBKDF2 hash.
 */
object PrivateGate {
    @Volatile var failed = 0
        private set
    @Volatile private var lockedUntil = 0L

    fun remainingLockMs(now: Long = System.currentTimeMillis()): Long = (lockedUntil - now).coerceAtLeast(0L)

    fun onWrong(now: Long = System.currentTimeMillis()) {
        failed++
        val wait = PrivatePin.lockoutMillis(failed)
        if (wait > 0) lockedUntil = now + wait
    }

    fun onSuccess() { failed = 0; lockedUntil = 0L }
}
