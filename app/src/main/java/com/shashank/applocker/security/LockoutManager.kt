package com.shashank.applocker.security

import com.shashank.applocker.domain.model.LockoutState
import com.shashank.applocker.domain.model.LockoutStatus
import com.shashank.applocker.domain.repository.LockoutRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies [LockoutPolicy] to the persisted [LockoutState]. All functions throw
 * [SecretStoreException] if the state cannot be read or written; callers must fail closed.
 *
 * Reboot handling: elapsedRealtime restarts at boot, so a stored deadline cannot be trusted
 * afterwards. If the boot count changed, or the clock went backwards past the start of the
 * wait, the full wait starts again from now. A reboot can therefore never shorten a lockout.
 */
@Singleton
class LockoutManager @Inject constructor(
    private val repository: LockoutRepository,
    private val clock: ElapsedClock,
    private val credentials: DeviceCredentialAvailability,
) {
    private val mutex = Mutex()

    suspend fun status(): LockoutStatus = mutex.withLock {
        val state = repository.get()
        val now = clock.elapsedRealtimeMs()

        if (state.requiresDeviceCredential) {
            if (credentials.isAvailable()) return LockoutStatus.CredentialRequired
            // The screen lock was removed after the lockout began. Fall back to a long wait.
            return startWait(state.copy(requiresDeviceCredential = false), LockoutPolicy.FIVE_MINUTES_MS, now)
        }
        if (state.lockedUntilElapsedMs == 0L || state.lockDurationMs <= 0L) return LockoutStatus.Open

        val start = state.lockedUntilElapsedMs - state.lockDurationMs
        val rebooted = state.bootCount != clock.bootCount() || now < start
        if (rebooted) return startWait(state, state.lockDurationMs, now)

        val remaining = (state.lockedUntilElapsedMs - now).coerceAtMost(state.lockDurationMs)
        if (remaining > 0) return LockoutStatus.Waiting(remaining)

        // Wait is over. Clear the deadline so a later reboot is not mistaken for this wait.
        repository.save(state.copy(lockedUntilElapsedMs = 0L, lockDurationMs = 0L))
        LockoutStatus.Open
    }

    suspend fun recordFailure(): LockoutStatus = mutex.withLock {
        val state = repository.get()
        val attempts = (state.failedAttempts + 1).coerceAtMost(MAX_COUNT)
        val now = clock.elapsedRealtimeMs()
        val base = state.copy(failedAttempts = attempts)
        when (val action = LockoutPolicy.actionFor(attempts)) {
            LockoutAction.None -> {
                repository.save(base)
                LockoutStatus.Open
            }
            is LockoutAction.Wait -> startWait(base, action.durationMs, now)
            LockoutAction.RequireDeviceCredential ->
                if (credentials.isAvailable()) {
                    repository.save(base.copy(requiresDeviceCredential = true, lockedUntilElapsedMs = 0L, lockDurationMs = 0L))
                    LockoutStatus.CredentialRequired
                } else {
                    // No screen lock to fall back on: stay at the 5-minute wait.
                    startWait(base, LockoutPolicy.FIVE_MINUTES_MS, now)
                }
        }
    }

    /** Consecutive wrong PINs since the last success or credential reset. */
    suspend fun failedAttempts(): Int = mutex.withLock { repository.get().failedAttempts }

    /** Correct PIN: forget all failures. */
    suspend fun recordSuccess() = mutex.withLock { repository.reset() }

    /** The user proved they own the device screen lock: forget all failures. */
    suspend fun resetAfterDeviceCredential() = mutex.withLock { repository.reset() }

    private suspend fun startWait(state: LockoutState, durationMs: Long, now: Long): LockoutStatus {
        repository.save(
            state.copy(
                lockedUntilElapsedMs = now + durationMs,
                lockDurationMs = durationMs,
                bootCount = clock.bootCount(),
            ),
        )
        return LockoutStatus.Waiting(durationMs)
    }

    private companion object {
        const val MAX_COUNT = 1_000
    }
}
