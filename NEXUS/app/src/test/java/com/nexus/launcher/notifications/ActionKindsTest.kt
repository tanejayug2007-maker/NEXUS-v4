package com.nexus.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionKindsTest {

    @Test fun `semantic actions win`() {
        assertEquals(ActionKind.REPLY, ActionKinds.classify(ActionKinds.SEMANTIC_REPLY, "Answer", false))
        assertEquals(ActionKind.MARK_READ, ActionKinds.classify(ActionKinds.SEMANTIC_MARK_AS_READ, "Done", false))
        assertEquals(ActionKind.MUTE, ActionKinds.classify(ActionKinds.SEMANTIC_MUTE, "Shh", false))
    }

    @Test fun `free form input is a reply box`() {
        assertEquals(ActionKind.REPLY, ActionKinds.classify(ActionKinds.SEMANTIC_NONE, "Respond", true))
    }

    @Test fun `titles from common messengers`() {
        assertEquals(ActionKind.MARK_READ, ActionKinds.classify(0, "Mark as read", false))      // WhatsApp, Telegram
        assertEquals(ActionKind.MARK_READ, ActionKinds.classify(0, "MARK AS READ", false))
        assertEquals(ActionKind.MUTE, ActionKinds.classify(0, "Mute", false))
        assertEquals(ActionKind.MUTE, ActionKinds.classify(0, "Mute for 1 hour", false))
        assertEquals(ActionKind.OTHER, ActionKinds.classify(0, "Reply", false)) // no input box: opens the app
        assertEquals(ActionKind.OTHER, ActionKinds.classify(0, "Like", false))
        assertEquals(ActionKind.OTHER, ActionKinds.classify(0, "Already read the docs?", false))
    }

    @Test fun `card order is reply, read, mute, then two others`() {
        val actions = listOf("o1" to ActionKind.OTHER, "m" to ActionKind.MUTE, "o2" to ActionKind.OTHER, "r" to ActionKind.REPLY, "o3" to ActionKind.OTHER, "read" to ActionKind.MARK_READ)
        assertEquals(listOf("r", "read", "m", "o1", "o2"), ActionKinds.ordered(actions, { it.second }).map { it.first })
    }

    @Test fun `messaging detection`() {
        assertTrue(ActionKinds.isMessaging("msg", null, false))
        assertTrue(ActionKinds.isMessaging(null, "android.app.Notification\$MessagingStyle", false))
        assertTrue(ActionKinds.isMessaging(null, null, true))
        assertFalse(ActionKinds.isMessaging("email", "android.app.Notification\$BigTextStyle", false))
    }
}
