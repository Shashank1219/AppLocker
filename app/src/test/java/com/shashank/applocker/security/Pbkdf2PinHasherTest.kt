package com.shashank.applocker.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Pbkdf2PinHasherTest {
    private val hasher = Pbkdf2PinHasher()
    private val salt = ByteArray(16) { it.toByte() }
    private fun pin(s: String) = s.toCharArray()

    @Test fun isDeterministicForSameInputs() {
        assertArrayEquals(hasher.hash(pin("135790"), salt, 1000), hasher.hash(pin("135790"), salt, 1000))
    }

    @Test fun outputIs32Bytes() = assertEquals(32, hasher.hash(pin("135790"), salt, 1000).size)

    @Test fun differentPinSaltOrIterationsChangeOutput() {
        val base = hasher.hash(pin("135790"), salt, 1000)
        assertFalse(base.contentEquals(hasher.hash(pin("135791"), salt, 1000)))
        assertFalse(base.contentEquals(hasher.hash(pin("135790"), ByteArray(16) { 9 }, 1000)))
        assertFalse(base.contentEquals(hasher.hash(pin("135790"), salt, 1001)))
    }

    @Test fun doesNotModifyInputPin() {
        val p = pin("135790")
        hasher.hash(p, salt, 1000)
        assertEquals("135790", String(p))
    }

    @Test fun matchesPbkdf2HmacSha256KnownVector() {
        // RFC 7914 section 11: PBKDF2-HMAC-SHA-256, P="passwd", S="salt", c=1, first 32 bytes.
        val out = hasher.hash("passwd".toCharArray(), "salt".toByteArray(), 1)
        val hex = out.joinToString("") { "%02x".format(it) }
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", hex)
    }

    @Test fun calibratedPolicyStaysWithinBounds() {
        val n = CalibratedIterationPolicy(hasher).iterationsForNewPin()
        assertEquals(true, n in CalibratedIterationPolicy.MIN_ITERATIONS..CalibratedIterationPolicy.MAX_ITERATIONS)
    }
}
