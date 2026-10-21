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
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.app.RemoteInput
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
    /** The sender's own actions (reply, mark as read, mute…), in display order. Never invented. */
    val actions: List<NexusAction> = emptyList(),
    /** Conversation layout: MessagingStyle, category "msg" or a reply box. */
    val messaging: Boolean = false,
    /** Last few messages of a conversation (MessagingStyle), oldest first. */
    val messages: List<NexusMessage> = emptyList(),
    /** A player's media notification (has a media session or MediaStyle). Shown as the media card. */
    val media: Boolean = false,
    internal val mediaToken: MediaSession.Token? = null,
)

data class NexusMessage(val sender: String, val text: String, val time: Long)

/** One of the sending app's notification actions. */
class NexusAction(
    val title: String,
    val kind: ActionKind,
    internal val intent: PendingIntent,
    internal val remoteInputs: Array<RemoteInput>,
    /** The reply box accepts free text (RemoteInput.allowFreeFormInput). */
    val freeForm: Boolean,
    /** The app says this action opens its UI (so the panel should close). */
    val opensUi: Boolean,
)

data class MediaState(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val pkg: String,
    internal val controller: MediaController,
    /** Player app name, e.g. "YouTube Music". */
    val appLabel: String = "",
    val album: String = "",
    val canPrevious: Boolean = true,
    val canNext: Boolean = true,
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

    /**
     * Fires one of the sender's actions. [reply] goes into its RemoteInput the same way the system
     * shade sends an inline reply. Returns false if the app has cancelled the action meanwhile.
     */
    fun runAction(context: Context, action: NexusAction, reply: CharSequence? = null): Boolean = try {
        if (reply != null && action.remoteInputs.isNotEmpty()) {
            val results = android.os.Bundle()
            action.remoteInputs.filter { it.allowFreeFormInput }.forEach { results.putCharSequence(it.resultKey, reply) }
            val fill = android.content.Intent().addFlags(android.content.Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(action.remoteInputs, fill, results)
            RemoteInput.setResultsSource(fill, RemoteInput.SOURCE_FREE_FORM_INPUT)
            action.intent.send(context, 0, fill)
        } else {
            action.intent.send()
        }
        true
    } catch (e: PendingIntent.CanceledException) {
        false
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
        if (!n.ongoing && !n.media && !summary && (n.title.isNotBlank() || n.text.isNotBlank())) NotificationHub.emitPosted(n)
        // A player posted its media notification: use that session even if the session list hasn't caught up.
        if (n.media && n.mediaToken != null && activeController?.sessionToken != n.mediaToken) {
            runCatching { pickController(listOf(MediaController(this, n.mediaToken)) + currentSessions()) }
        }
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
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION") extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
        val actions = n.actions.orEmpty().mapNotNull { a -> toAction(a) }
        val ordered = ActionKinds.ordered(actions, { it.kind })
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
            actions = ordered,
            messaging = ActionKinds.isMessaging(n.category, template, actions.any { it.kind == ActionKind.REPLY }),
            messages = messagesOf(extras),
            media = token != null || template?.endsWith("MediaStyle") == true,
            mediaToken = token,
        )
    }

    private fun toAction(a: Notification.Action): NexusAction? {
        val pi = a.actionIntent ?: return null
        val title = a.title?.toString().orEmpty()
        val inputs: Array<RemoteInput> = a.remoteInputs?.filterNotNull()?.toTypedArray() ?: emptyArray()
        val freeForm = inputs.any { it.allowFreeFormInput }
        if (title.isBlank() && !freeForm) return null
        return NexusAction(
            title = title,
            kind = ActionKinds.classify(a.semanticAction, title, freeForm),
            intent = pi,
            remoteInputs = inputs,
            freeForm = freeForm,
            // set by NotificationCompat (WhatsApp, Telegram…): "mark as read"/"mute" say false
            opensUi = a.extras?.getBoolean("android.support.action.showsUserInterface", kindOpensUi(title)) ?: false,
        )
    }

    /** Without the compat flag: anything that isn't a reply / read / mute probably opens the app. */
    private fun kindOpensUi(title: String) = ActionKinds.classify(ActionKinds.SEMANTIC_NONE, title, false) == ActionKind.OTHER

    private fun messagesOf(extras: android.os.Bundle): List<NexusMessage> {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, android.os.Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION") extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        } ?: return emptyList()
        return runCatching {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw).takeLast(6).map { m ->
                NexusMessage(m.senderPerson?.name?.toString() ?: "", m.text?.toString().orEmpty(), m.timestamp)
            }.filter { it.text.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun currentSessions(): List<MediaController> = runCatching {
        sessionManager?.getActiveSessions(ComponentName(this, NexusNotificationService::class.java)).orEmpty()
    }.getOrDefault(emptyList())

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
        val state = c.playbackState
        val supported = state?.actions ?: 0L
        // A player that reports no actions at all still gets both buttons (many do this while buffering).
        fun can(flag: Long) = supported == 0L || (supported and flag) != 0L
        NotificationHub.setMedia(
            MediaState(
                title = title,
                artist = artist,
                art = scaled,
                playing = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
                pkg = c.packageName,
                controller = c,
                appLabel = labelFor(c.packageName),
                album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                canPrevious = can(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                canNext = can(PlaybackState.ACTION_SKIP_TO_NEXT),
            )
        )
    }
}
