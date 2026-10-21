package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivatePinTest {

    @Test fun `correct PIN verifies, wrong ones do not`() {
        val h = PrivatePin.hash("4821", iterations = 1000)
        assertTrue(PrivatePin.verify("4821", h))
        assertFalse(PrivatePin.verify("4822", h))
        assertFalse(PrivatePin.verify("", h))
        assertFalse(PrivatePin.verify("48210", h))
    }

    @Test fun `stored value never contains the PIN and is salted`() {
        val a = PrivatePin.hash("123456", iterations = 1000)
        val b = PrivatePin.hash("123456", iterations = 1000)
        assertFalse(a.contains("123456"))
        assertNotEquals(a, b) // different random salt each time
        assertTrue(a.startsWith("pbkdf2$1000$"))
    }

    @Test fun `malformed stored values are rejected`() {
        assertFalse(PrivatePin.verify("1234", ""))
        assertFalse(PrivatePin.verify("1234", "pbkdf2\$x\$00\$00"))
        assertFalse(PrivatePin.verify("1234", "sha1\$1000\$00\$00"))
    }

    @Test fun `PIN rules`() {
        assertTrue(PrivatePin.isValidPin("0000"))
        assertFalse(PrivatePin.isValidPin("123"))
        assertFalse(PrivatePin.isValidPin("12a4"))
        assertFalse(PrivatePin.isValidPin("1".repeat(13)))
    }

    @Test fun `lockout grows after repeated wrong PINs and is capped`() {
        assertEquals(0L, PrivatePin.lockoutMillis(4))
        assertEquals(30_000L, PrivatePin.lockoutMillis(5))
        assertEquals(60_000L, PrivatePin.lockoutMillis(6))
        assertEquals(15 * 60_000L, PrivatePin.lockoutMillis(50))
    }
}
