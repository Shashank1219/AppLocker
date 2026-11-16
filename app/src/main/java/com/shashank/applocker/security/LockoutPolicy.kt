package com.shashank.applocker.security

sealed interface LockoutAction {
    data object None : LockoutAction
    data class Wait(val durationMs: Long) : LockoutAction
    data object RequireDeviceCredential : LockoutAction
}

/**
 * Escalation table. A tier applies when the failure count reaches its threshold and stays
 * in force for each further failure until the next threshold:
 * 1-2 failures: none; 3-4: 30 s; 5-7: 1 min; 8-9: 5 min; 10 or more: device credential.
 */
object LockoutPolicy {
    const val THIRTY_SECONDS_MS = 30_000L
    const val ONE_MINUTE_MS = 60_000L
    const val FIVE_MINUTES_MS = 300_000L

    fun actionFor(failedAttempts: Int): LockoutAction = when {
        failedAttempts >= 10 -> LockoutAction.RequireDeviceCredential
        failedAttempts >= 8 -> LockoutAction.Wait(FIVE_MINUTES_MS)
        failedAttempts >= 5 -> LockoutAction.Wait(ONE_MINUTE_MS)
        failedAttempts >= 3 -> LockoutAction.Wait(THIRTY_SECONDS_MS)
        else -> LockoutAction.None
    }
}
