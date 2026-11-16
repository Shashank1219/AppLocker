package com.shashank.applocker.security

import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

interface PinHasher {
    /** Derives a 32-byte verifier from [pin] and [salt]. Does not modify or wipe [pin]. */
    fun hash(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray
}

/** PBKDF2WithHmacSHA256, available on every Android 8.0+ device with no native code. */
@Singleton
class Pbkdf2PinHasher @Inject constructor() : PinHasher {
    override fun hash(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } catch (e: InvalidKeySpecException) {
            throw IllegalStateException("PBKDF2 failed")
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val HASH_BITS = 256
        const val HASH_BYTES = HASH_BITS / 8
    }
}

/** Chooses the PBKDF2 work factor for a newly set PIN. */
interface IterationPolicy {
    fun iterationsForNewPin(): Int
}

/**
 * Times a short PBKDF2 run on this device and scales it so one verification takes about
 * [TARGET_MILLIS] ms. The result is stored with the PIN, so slower or faster phones each get
 * their own value. Clamped so a noisy measurement cannot produce a weak or unusable setting.
 */
@Singleton
class CalibratedIterationPolicy @Inject constructor(
    private val hasher: PinHasher,
) : IterationPolicy {
    override fun iterationsForNewPin(): Int {
        val salt = ByteArray(16)
        val probe = charArrayOf('1', '3', '5', '7', '9', '0')
        hasher.hash(probe, salt, PROBE_ITERATIONS) // warm-up (class loading, JIT)
        val start = System.nanoTime()
        hasher.hash(probe, salt, PROBE_ITERATIONS)
        val elapsedMs = ((System.nanoTime() - start) / 1_000_000.0).coerceAtLeast(1.0)
        probe.fill('0')
        return (PROBE_ITERATIONS * TARGET_MILLIS / elapsedMs).toLong()
            .coerceIn(MIN_ITERATIONS.toLong(), MAX_ITERATIONS.toLong()).toInt()
    }

    companion object {
        const val TARGET_MILLIS = 200
        const val PROBE_ITERATIONS = 20_000
        const val MIN_ITERATIONS = 100_000
        const val MAX_ITERATIONS = 2_000_000
    }
}
