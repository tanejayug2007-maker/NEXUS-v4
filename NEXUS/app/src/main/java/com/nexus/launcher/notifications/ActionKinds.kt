package com.nexus.launcher.notifications

/**
 * What a notification's own action does, as far as NEXUS can tell. NEXUS only ever shows actions the
 * sending app attached to its notification: nothing here is invented. Pure (unit-tested).
 */
enum class ActionKind { REPLY, MARK_READ, MUTE, OTHER }

object ActionKinds {
    // Notification.Action.SEMANTIC_ACTION_* (API 28), copied so this file stays plain JVM code.
    const val SEMANTIC_NONE = 0
    const val SEMANTIC_REPLY = 1
    const val SEMANTIC_MARK_AS_READ = 2
    const val SEMANTIC_MUTE = 6

    private val markRead = listOf(
        "mark as read", "mark read", "mark all as read", "read", "seen",
        "als gelesen", "marquer comme lu", "marcar como leído", "marcar como lida", "segna come letto",
        "पढ़ा गया", "पढ़ा हुआ",
    )
    private val mute = listOf("mute", "silence", "silent", "stummschalten", "silenciar", "désactiver le son", "silenzia", "म्यूट")

    /**
     * Semantic action first (apps that set it are explicit), then a free-form RemoteInput (that is a
     * reply box by definition), then the visible title in a few languages. A "Reply" without an input
     * box is left as OTHER: it just opens the app, so it is shown as the app's own button.
     */
    fun classify(semantic: Int, title: String, hasFreeFormInput: Boolean): ActionKind {
        when (semantic) {
            SEMANTIC_REPLY -> return ActionKind.REPLY
            SEMANTIC_MARK_AS_READ -> return ActionKind.MARK_READ
            SEMANTIC_MUTE -> return ActionKind.MUTE
        }
        if (hasFreeFormInput) return ActionKind.REPLY
        val t = title.trim().lowercase()
        if (t.isEmpty()) return ActionKind.OTHER
        if (markRead.any { t == it || t.startsWith("$it ") }) return ActionKind.MARK_READ
        if (mute.any { t == it || t.startsWith("$it ") }) return ActionKind.MUTE
        return ActionKind.OTHER
    }

    /** Order on the card: Reply, Mark as read, Mute, then the app's others, as many as fit. */
    fun <T> ordered(actions: List<T>, kindOf: (T) -> ActionKind, maxOthers: Int = 2): List<T> {
        val primary = listOf(ActionKind.REPLY, ActionKind.MARK_READ, ActionKind.MUTE)
            .mapNotNull { k -> actions.firstOrNull { kindOf(it) == k } }
        val others = actions.filter { kindOf(it) == ActionKind.OTHER }.take(maxOthers)
        return primary + others
    }

    /** Is this a conversation-type notification (so the card gets the messaging layout)? */
    fun isMessaging(category: String?, template: String?, hasReply: Boolean): Boolean =
        category == "msg" || template?.endsWith("MessagingStyle") == true || hasReply
}
