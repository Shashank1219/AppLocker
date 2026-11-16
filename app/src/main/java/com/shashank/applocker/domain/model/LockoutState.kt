package com.shashank.applocker.domain.model

/**
 * Persisted failed-PIN bookkeeping. Times are [android.os.SystemClock.elapsedRealtime] values,
 * never wall-clock, so changing the system time cannot shorten a wait.
 */
data class LockoutState(
    val failedAttempts: Int = 0,
    /** elapsedRealtime at which the current wait ends; 0 when no wait is active. */
    val lockedUntilElapsedMs: Long = 0L,
    /** Length of the current wait. Used to detect reboots and to restart the wait after one. */
    val lockDurationMs: Long = 0L,
    /** Boot count when the wait was recorded, to detect reboots. */
    val bootCount: Int = 0,
    /** Set at 10 failures when the device has a screen lock. Cleared only by that credential. */
    val requiresDeviceCredential: Boolean = false,
) {
    companion object {
        val NONE = LockoutState()
    }
}

/** What the unlock screen must currently do. */
sealed interface LockoutStatus {
    data object Open : LockoutStatus
    data class Waiting(val remainingMs: Long) : LockoutStatus
    data object CredentialRequired : LockoutStatus

    /** Lockout state could not be read. Treated as locked: never fail open. */
    data object Unavailable : LockoutStatus
}

sealed interface AttemptResult {
    data object Success : AttemptResult

    /** Wrong PIN. [status] is the lockout state after counting this failure. */
    data class Incorrect(val status: LockoutStatus) : AttemptResult

    /** Attempt refused without checking the PIN, because a lockout is active. */
    data class Locked(val status: LockoutStatus) : AttemptResult

    data object StorageError : AttemptResult
}
