package com.shashank.applocker.security

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** Plain JVM tests: no Android classes, software AES only. */
class BiometricPrimitivesTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun aesGcmRoundTrip() {
        val enc = AesGcmCiphers.newEncryptCipher(key)
        val ct = enc.doFinal("secret".toByteArray())
        assertEquals(12, enc.iv.size)
        val dec = AesGcmCiphers.newDecryptCipher(key, enc.iv)
        assertArrayEquals("secret".toByteArray(), dec.doFinal(ct))
    }

    @Test fun eachEncryptionUsesAFreshIv() {
        val a = AesGcmCiphers.newEncryptCipher(key).iv
        val b = AesGcmCiphers.newEncryptCipher(key).iv
        assertFalse(a.contentEquals(b))
    }

    @Test fun tamperedCiphertextIsRejected() {
        val enc = AesGcmCiphers.newEncryptCipher(key)
        val ct = enc.doFinal(ByteArray(32))
        ct[0] = (ct[0].toInt() xor 1).toByte()
        try {
            AesGcmCiphers.newDecryptCipher(key, enc.iv).doFinal(ct)
            fail("expected AEADBadTagException")
        } catch (e: AEADBadTagException) {
            Unit
        }
    }

    @Test fun tokensAreRandom32Bytes() {
        val gen = SecureRandomTokenGenerator(SecureRandom())
        val a = gen.newToken()
        val b = gen.newToken()
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
        assertFalse(a.all { it == 0.toByte() })
    }

    @Test fun recordRoundTrips() {
        val r = BiometricTokenRecord(ByteArray(12) { 1 }, ByteArray(48) { 2 }, ByteArray(32) { 3 })
        val d = BiometricTokenRecord.decode(r.encode())!!
        assertArrayEquals(r.iv, d.iv)
        assertArrayEquals(r.ciphertext, d.ciphertext)
        assertArrayEquals(r.tokenDigest, d.tokenDigest)
    }

    @Test fun malformedRecordsAreRejected() {
        val good = BiometricTokenRecord(ByteArray(12), ByteArray(48), ByteArray(32)).encode()
        assertNull(BiometricTokenRecord.decode(ByteArray(0)))
        assertNull(BiometricTokenRecord.decode(good.copyOf(good.size - 1)))
        assertNull(BiometricTokenRecord.decode(good + 0))
        assertNull(BiometricTokenRecord.decode(good.copyOf().also { it[0] = 9 })) // version
        assertNull(BiometricTokenRecord.decode(good.copyOf().also { it[1] = 8 })) // iv length
        assertNotNull(BiometricTokenRecord.decode(good))
    }
}
