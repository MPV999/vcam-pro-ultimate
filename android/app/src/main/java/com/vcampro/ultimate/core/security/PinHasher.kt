package com.vcampro.ultimate.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Băm PIN bằng PBKDF2-HMAC-SHA256 với salt ngẫu nhiên.
 * PIN không bao giờ được lưu dạng plaintext.
 */
@Singleton
class PinHasher @Inject constructor() {

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return toHex(salt)
    }

    fun hash(pin: String, saltHex: String): String {
        val salt = fromHex(saltHex)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hashed = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return toHex(hashed)
    }

    fun verify(pin: String, saltHex: String, expectedHash: String): Boolean {
        if (pin.isEmpty() || saltHex.isEmpty() || expectedHash.isEmpty()) return false
        val actual = hash(pin, saltHex)
        return constantTimeEquals(actual, expectedHash)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString()
    }

    private fun fromHex(hex: String): ByteArray {
        val clean = if (hex.length % 2 == 0) hex else "0$hex"
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_BYTES = 16
        const val MIN_PIN_LENGTH = 4
    }
}
