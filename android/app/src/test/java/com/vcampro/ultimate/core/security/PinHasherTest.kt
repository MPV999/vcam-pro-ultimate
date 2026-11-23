package com.vcampro.ultimate.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val hasher = PinHasher()

    @Test
    fun samePinAndSalt_produceSameHash() {
        val salt = hasher.newSalt()
        val first = hasher.hash("123456", salt)
        val second = hasher.hash("123456", salt)
        assertEquals(first, second)
    }

    @Test
    fun differentSalt_producesDifferentHash() {
        val first = hasher.hash("123456", hasher.newSalt())
        val second = hasher.hash("123456", hasher.newSalt())
        assertNotEquals(first, second)
    }

    @Test
    fun verify_acceptsCorrectPinAndRejectsWrongPin() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("4821", salt)
        assertTrue(hasher.verify("4821", salt, hash))
        assertFalse(hasher.verify("4822", salt, hash))
        assertFalse(hasher.verify("", salt, hash))
    }

    @Test
    fun hashIsNotPlaintext() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("9999", salt)
        assertFalse(hash.contains("9999"))
        assertEquals(64, hash.length) // 256-bit key -> 64 ký tự hex
    }
}
