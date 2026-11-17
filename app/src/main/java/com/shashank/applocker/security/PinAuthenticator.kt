package com.shashank.applocker.security

import com.shashank.applocker.domain.model.AttemptResult
import com.shashank.applocker.domain.model.LockoutStatus
import com.shashank.applocker.domain.model.PinVerifyResult
import com.shashank.applocker.domain.repository.PinRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only path from "user typed a PIN" to "unlocked". Order matters:
 * 1. If a lockout is active, refuse without checking the PIN (a correct PIN during a wait
 *    must not work, or the wait would be pointless).
 * 2. Verify against the stored verifier.
 * 3. Count the result. A failed count write is reported as an error, never ignored.
 *
 * Attempts are serialized so rapid concurrent guesses cannot all pass step 1 together.
 * Anything unreadable fails closed.
 */
@Singleton
class PinAuthenticator @Inject constructor(
    private val pinRepository: PinRepository,
    private val lockout: LockoutManager,
) {
    private val mutex = Mutex()

    /** Current lockout state for the UI. */
    suspend fun status(): LockoutStatus = try {
        lockout.status()
    } catch (e: SecretStoreException) {
        LockoutStatus.Unavailable
    }

    /** Takes ownership of [pin] and wipes it. */
    /**
     * Consecutive wrong PINs so far (persisted, so it survives the lock screen closing).
     * If the count cannot be read this returns [Int.MAX_VALUE], so a caller that acts after N
     * failures errs on the side of acting.
     */
    suspend fun failedAttempts(): Int = try {
        lockout.failedAttempts()
    } catch (_: SecretStoreException) {
        Int.MAX_VALUE
    }

    suspend fun attempt(pin: CharArray): AttemptResult = mutex.withLock {
        try {
            when (val before = status()) {
                LockoutStatus.Open -> Unit
                LockoutStatus.Unavailable -> return@withLock AttemptResult.StorageError
                else -> return@withLock AttemptResult.Locked(before)
            }
            when (pinRepository.verifyPin(pin)) {
                PinVerifyResult.Success -> {
                    // If the reset fails the user is still in; the old count is the safe side.
                    try {
                        lockout.recordSuccess()
                    } catch (_: SecretStoreException) {
                        // keep the old count
                    }
                    AttemptResult.Success
                }
                PinVerifyResult.Incorrect -> AttemptResult.Incorrect(lockout.recordFailure())
                PinVerifyResult.NotSet -> AttemptResult.Incorrect(LockoutStatus.Open)
                PinVerifyResult.StorageError -> AttemptResult.StorageError
            }
        } catch (e: SecretStoreException) {
            AttemptResult.StorageError
        } finally {
            pin.fill('0')
        }
    }

    /** Call only after the system has confirmed the device screen lock. */
    suspend fun resetAfterDeviceCredential(): Boolean = try {
        lockout.resetAfterDeviceCredential()
        true
    } catch (e: SecretStoreException) {
        false
    }
}
