package com.nexus.launcher.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Private Space PIN storage: never the PIN itself, only a salted PBKDF2-SHA256 hash
 * ("pbkdf2$iterations$saltHex$hashHex"), compared in constant time. Plus the wrong-attempt
 * back-off. Pure JVM code, unit-tested.
 */
object PrivatePin {
    private const val ITERATIONS = 20_000
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    fun isValidPin(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }, iterations: Int = ITERATIONS): String {
        require(isValidPin(pin)) { "PIN must be $MIN_LENGTH–$MAX_LENGTH digits" }
        return "pbkdf2$$iterations$${salt.hex()}$${derive(pin, salt, iterations).hex()}"
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = parts[2].unhex() ?: return false
        val expected = parts[3].unhex() ?: return false
        if (pin.isEmpty() || pin.length > MAX_LENGTH) return false
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    /** Lock-out after repeated wrong PINs: none for the first 4, then 30 s, doubling, max 15 min. */
    fun lockoutMillis(failedAttempts: Int): Long {
        if (failedAttempts < 5) return 0L
        val steps = (failedAttempts - 5).coerceAtMost(5)
        return (30_000L shl steps).coerceAtMost(15 * 60_000L)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun String.unhex(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching { ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }
}
