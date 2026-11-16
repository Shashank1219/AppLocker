package com.shashank.applocker.security

import java.nio.ByteBuffer

/**
 * What is stored for fingerprint unlock, inside [SecretStore] (so encrypted a second time):
 * the AES-GCM IV and ciphertext of the token, and SHA-256 of the token as the expected value.
 * The token itself is never stored. The digest of a 256-bit random value reveals nothing useful.
 */
class BiometricTokenRecord(val iv: ByteArray, val ciphertext: ByteArray, val tokenDigest: ByteArray) {
    fun encode(): ByteArray = ByteBuffer.allocate(1 + 1 + iv.size + 4 + ciphertext.size + DIGEST_BYTES)
        .put(VERSION)
        .put(iv.size.toByte())
        .put(iv)
        .putInt(ciphertext.size)
        .put(ciphertext)
        .put(tokenDigest)
        .array()

    fun wipe() {
        iv.fill(0)
        ciphertext.fill(0)
        tokenDigest.fill(0)
    }

    companion object {
        const val VERSION: Byte = 1
        const val DIGEST_BYTES = 32
        private const val MAX_CIPHERTEXT = 1024

        /** @return null if [bytes] is not a valid record. */
        fun decode(bytes: ByteArray): BiometricTokenRecord? {
            val buf = ByteBuffer.wrap(bytes)
            if (buf.remaining() < 2 || buf.get() != VERSION) return null
            val ivLen = buf.get().toInt()
            if (ivLen != AesGcmCiphers.IV_BYTES || buf.remaining() < ivLen + 4) return null
            val iv = ByteArray(ivLen).also { buf.get(it) }
            val ctLen = buf.getInt()
            if (ctLen < AesGcmCiphers.TAG_BYTES || ctLen > MAX_CIPHERTEXT) return null
            if (buf.remaining() != ctLen + DIGEST_BYTES) return null
            val ct = ByteArray(ctLen).also { buf.get(it) }
            val digest = ByteArray(DIGEST_BYTES).also { buf.get(it) }
            return BiometricTokenRecord(iv, ct, digest)
        }
    }
}
