package com.shashank.applocker.security

import com.shashank.applocker.di.DefaultDispatcher
import com.shashank.applocker.domain.model.LockoutState
import com.shashank.applocker.domain.repository.LockoutRepository
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Keeps the lockout counters in [SecretStore] (AES-GCM, key in AndroidKeyStore), so they
 * survive process death and cannot be edited or read from the app's files.
 * A missing entry means "no failures". An unreadable or malformed one throws.
 */
@Singleton
class SecureLockoutRepository @Inject constructor(
    private val secretStore: SecretStore,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : LockoutRepository {

    override suspend fun get(): LockoutState = withContext(dispatcher) {
        val bytes = secretStore.get(KEY) ?: return@withContext LockoutState.NONE
        decode(bytes).also { bytes.fill(0) }
    }

    override suspend fun save(state: LockoutState) = withContext(dispatcher) {
        secretStore.put(KEY, encode(state))
    }

    override suspend fun reset() = withContext(dispatcher) {
        secretStore.remove(KEY)
    }

    internal companion object {
        const val KEY = "lockout_state"
        private const val VERSION: Byte = 1
        private const val SIZE = 1 + 4 + 8 + 8 + 4 + 1

        fun encode(s: LockoutState): ByteArray = ByteBuffer.allocate(SIZE)
            .put(VERSION)
            .putInt(s.failedAttempts)
            .putLong(s.lockedUntilElapsedMs)
            .putLong(s.lockDurationMs)
            .putInt(s.bootCount)
            .put(if (s.requiresDeviceCredential) 1 else 0)
            .array()

        fun decode(bytes: ByteArray): LockoutState {
            if (bytes.size != SIZE) throw SecretStoreException()
            return try {
                val buf = ByteBuffer.wrap(bytes)
                if (buf.get() != VERSION) throw SecretStoreException()
                LockoutState(
                    failedAttempts = buf.getInt(),
                    lockedUntilElapsedMs = buf.getLong(),
                    lockDurationMs = buf.getLong(),
                    bootCount = buf.getInt(),
                    requiresDeviceCredential = buf.get() != 0.toByte(),
                )
            } catch (e: BufferUnderflowException) {
                throw SecretStoreException()
            }
        }
    }
}
