package com.nexus.launcher.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/** A notification as NEXUS displays it. Holds no Notification object to avoid leaking big extras. */
data class NexusNotification(
    val key: String,
    val pkg: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val ongoing: Boolean,
    val clearable: Boolean,
    val color: Int,
    val user: UserHandle,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
)

data class MediaState(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val pkg: String,
    internal val controller: MediaController,
)

/** Bridge between the system-bound listener service and the launcher UI. */
object NotificationHub {
    private val _items = MutableStateFlow<List<NexusNotification>>(emptyList())
    val items: StateFlow<List<NexusNotification>> = _items

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _posted = MutableSharedFlow<NexusNotification>(extraBufferCapacity = 8)
    /** New, user-visible notifications (not the initial snapshot) — drives popup previews. */
    val posted: SharedFlow<NexusNotification> = _posted

    private val _media = MutableStateFlow<MediaState?>(null)
    val media: StateFlow<MediaState?> = _media

    internal var service: WeakReference<NexusNotificationService>? = null

    internal fun setConnected(v: Boolean) { _connected.value = v; if (!v) { _items.value = emptyList(); _media.value = null } }
    internal fun setItems(list: List<NexusNotification>) { _items.value = list }
    internal fun emitPosted(n: NexusNotification) { _posted.tryEmit(n) }
    internal fun setMedia(m: MediaState?) { _media.value = m }

    fun dismiss(key: String) { service?.get()?.let { runCatching { it.cancelNotification(key) } } }

    fun dismissAll() { service?.get()?.let { runCatching { it.cancelAllNotifications() } } }

    /** Opens the notification's target with the sender's intent, then auto-cancels if it asked to. */
    fun open(context: Context, n: NexusNotification): Boolean {
        val pi = n.contentIntent ?: return false
        return try {
            val opts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null
            pi.send(context, 0, null, null, null, null, opts)
            if (n.autoCancel) dismiss(n.key)
            true
        } catch (e: PendingIntent.CanceledException) {
            false
        }
    }

    fun play() = _media.value?.controller?.transportControls?.play()
    fun pause() = _media.value?.controller?.transportControls?.pause()
    fun next() = _media.value?.controller?.transportControls?.skipToNext()
    fun previous() = _media.value?.controller?.transportControls?.skipToPrevious()
}

/**
 * Official notification-access API. Only active after the user enables "NEXUS notification feed"
 * in Settings › Notifications › Device & app notifications (Notification access).
 */
class NexusNotificationService : NotificationListenerService() {

    private val labels = HashMap<String, String>()
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private val main = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pickController(controllers.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onSessionDestroyed() {
            activeController = null; publishMedia()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationHub.service = WeakReference(this)
        NotificationHub.setConnected(true)
        refreshAll()
        runCatching {
            val sm = getSystemService(MediaSessionManager::class.java)
            sessionManager = sm
            val me = ComponentName(this, NexusNotificationService::class.java)
            sm.addOnActiveSessionsChangedListener(sessionsListener, me, main)
            pickController(sm.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        NotificationHub.setConnected(false)
        NotificationHub.service = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshAll()
        val n = sbn?.let { toNexus(it) } ?: return
        val summary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (!n.ongoing && !summary && (n.title.isNotBlank() || n.text.isNotBlank())) NotificationHub.emitPosted(n)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshAll()
    }

    private fun refreshAll() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val groupsWithChildren = active
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .mapNotNull { it.groupKey }
            .toSet()
        val list = active.asSequence()
            .filter { sbn ->
                val summary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                !(summary && sbn.groupKey in groupsWithChildren)
            }
            .mapNotNull { toNexus(it) }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .sortedByDescending { it.postTime }
            .toList()
        NotificationHub.setItems(list)
    }

    private fun toNexus(sbn: StatusBarNotification): NexusNotification? {
        val n = sbn.notification ?: return null
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        return NexusNotification(
            key = sbn.key,
            pkg = sbn.packageName,
            appLabel = labelFor(sbn.packageName),
            title = title,
            text = text,
            postTime = sbn.postTime,
            ongoing = sbn.isOngoing,
            clearable = sbn.isClearable,
            color = n.color,
            user = sbn.user,
            contentIntent = n.contentIntent,
            autoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
        )
    }

    private fun labelFor(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
        }.getOrDefault(pkg)
    }

    private fun pickController(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
        if (chosen?.sessionToken != activeController?.sessionToken) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = chosen
            chosen?.registerCallback(controllerCallback, main)
        }
        publishMedia()
    }

    private fun publishMedia() {
        val c = activeController
        if (c == null) { NotificationHub.setMedia(null); return }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (title.isBlank()) { NotificationHub.setMedia(null); return }
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val scaled = art?.let { if (it.width > 256) Bitmap.createScaledBitmap(it, 256, 256 * it.height / it.width.coerceAtLeast(1), true) else it }
        NotificationHub.setMedia(
            MediaState(
                title = title,
                artist = artist,
                art = scaled,
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                pkg = c.packageName,
                controller = c,
            )
        )
    }
}
