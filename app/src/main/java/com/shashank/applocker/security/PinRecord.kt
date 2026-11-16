package com.shashank.applocker.security

import java.nio.ByteBuffer

/**
 * What is persisted (inside [SecretStore], so encrypted): format version, PBKDF2 iteration
 * count, random salt and the derived verifier. The raw PIN is never part of it.
 */
class PinRecord(val iterations: Int, val salt: ByteArray, val hash: ByteArray) {
    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(VERSION)
        .putInt(iterations)
        .put(salt)
        .put(hash)
        .array()

    fun wipe() {
        salt.fill(0)
        hash.fill(0)
    }

    companion object {
        const val VERSION: Byte = 1
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
        private const val SIZE = 1 + 4 + SALT_BYTES + HASH_BYTES

        /** @return null if [bytes] is not a valid record. */
        fun decode(bytes: ByteArray): PinRecord? {
            if (bytes.size != SIZE) return null
            val buf = ByteBuffer.wrap(bytes)
            if (buf.get() != VERSION) return null
            val iterations = buf.getInt()
            if (iterations < 1) return null
            val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
            val hash = ByteArray(HASH_BYTES).also { buf.get(it) }
            return PinRecord(iterations, salt, hash)
        }
    }
}
