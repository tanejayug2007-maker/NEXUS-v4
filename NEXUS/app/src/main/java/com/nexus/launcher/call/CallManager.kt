package com.nexus.launcher.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.nexus.launcher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

enum class CallPhase { RINGING, DIALING, ACTIVE, HOLDING, ENDED }

data class CallUi(
    val id: Int,
    val name: String,
    val number: String,
    val phase: CallPhase,
    val connectTime: Long,
)

/** Shared state between the system-bound InCallService and the call screen. */
object CallManager {
    private val calls = LinkedHashMap<Call, Int>()
    private var nextId = 1
    internal var service: WeakReference<NexusInCallService>? = null

    private val _current = MutableStateFlow<CallUi?>(null)
    val current: StateFlow<CallUi?> = _current

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted

    private val _speaker = MutableStateFlow(false)
    val speaker: StateFlow<Boolean> = _speaker

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    internal fun add(call: Call) {
        calls[call] = nextId++
        call.registerCallback(callback)
        publish()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        publish()
    }

    internal fun audio(state: CallAudioState?) {
        state ?: return
        _muted.value = state.isMuted
        _speaker.value = state.route == CallAudioState.ROUTE_SPEAKER
    }

    private fun primary(): Call? =
        calls.keys.firstOrNull { it.state == Call.STATE_RINGING }
            ?: calls.keys.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: calls.keys.lastOrNull()

    @Suppress("DEPRECATION")
    private fun publish() {
        val c = primary()
        if (c == null) { _current.value = null; return }
        val d = c.details
        val number = d.handle?.schemeSpecificPart.orEmpty()
        val name = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d.contactDisplayName else null)
            ?: d.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: number.ifBlank { "Unknown" }
        val phase = when (c.state) {
            Call.STATE_RINGING -> CallPhase.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL -> CallPhase.DIALING
            Call.STATE_ACTIVE -> CallPhase.ACTIVE
            Call.STATE_HOLDING -> CallPhase.HOLDING
            else -> CallPhase.ENDED
        }
        _current.value = CallUi(calls[c] ?: 0, name, number, phase, d.connectTimeMillis)
    }

    fun answer() = primary()?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun decline() = primary()?.let { if (it.state == Call.STATE_RINGING) it.reject(false, null) else it.disconnect() }
    fun hangUp() = primary()?.disconnect()
    fun toggleHold() = primary()?.let { if (it.state == Call.STATE_HOLDING) it.unhold() else it.hold() }
    fun dtmf(c: Char) = primary()?.let { it.playDtmfTone(c); it.stopDtmfTone() }

    fun toggleMute() { service?.get()?.setMuted(!_muted.value) }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        service?.get()?.setAudioRoute(if (_speaker.value) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    // ---- incoming-call notification (full-screen intent, as Android requires for calls) -----

    private const val CHANNEL = "nexus_calls"
    const val NOTIF_ID = 2050

    fun showIncoming(context: Context, ui: CallUi) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val screen = PendingIntent.getActivity(context, 1, Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val answer = PendingIntent.getActivity(context, 2, Intent(context, CallActivity::class.java).putExtra(CallActivity.EXTRA_ACTION, "answer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val decline = PendingIntent.getActivity(context, 3, Intent(context, CallActivity::class.java).putExtra(CallActivity.EXTRA_ACTION, "decline").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        val person = Person.Builder().setName(ui.name).setImportant(true).build()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .build()
        // Android 13+: without the notification permission the call UI still opens via CallActivity.
        val canPost = android.os.Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (canPost) runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, n) }
    }

    fun clearIncoming(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}

/**
 * Telecom binds this when NEXUS is the default phone app (Studio › Calls › Make NEXUS the
 * phone app). Telecom itself plays the ringtone; NEXUS only draws the call screens.
 */
class NexusInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallManager.service = WeakReference(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(call)
        val ui = CallManager.current.value
        if (call.state == Call.STATE_RINGING && ui != null) {
            CallManager.showIncoming(this, ui)
        }
        runCatching { startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
        if (CallManager.current.value == null) CallManager.clearIncoming(this)
    }

    @Deprecated("Still the callback telecom uses on API < 34")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        CallManager.audio(audioState)
    }

    override fun onDestroy() {
        CallManager.service = null
        super.onDestroy()
    }
}
