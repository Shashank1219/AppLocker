package com.shashank.applocker.security

import androidx.biometric.BiometricPrompt

/** Short, user-facing reasons for falling back to the PIN. NONE means fall back silently. */
enum class FingerprintMessage { NONE, LOCKED, UNAVAILABLE, NOT_ENROLLED, FAILED, KEY_CHANGED }

/**
 * Decides what happens when the system prompt ends with an error. Every error ends in the PIN
 * screen. None of them touches the PIN failure counter: cancelling or pressing "Use PIN" is not
 * a failed attempt, and fingerprint rejections are handled by the system's own limits.
 */
object BiometricErrorPolicy {
    fun messageFor(errorCode: Int): FingerprintMessage = when (errorCode) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED -> FingerprintMessage.NONE

        BiometricPrompt.ERROR_LOCKOUT,
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> FingerprintMessage.LOCKED

        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_HW_NOT_PRESENT -> FingerprintMessage.UNAVAILABLE

        BiometricPrompt.ERROR_NO_BIOMETRICS -> FingerprintMessage.NOT_ENROLLED

        else -> FingerprintMessage.FAILED
    }

    fun messageFor(availability: BiometricAvailability): FingerprintMessage = when (availability) {
        BiometricAvailability.AVAILABLE -> FingerprintMessage.NONE
        BiometricAvailability.NOT_ENROLLED -> FingerprintMessage.NOT_ENROLLED
        else -> FingerprintMessage.UNAVAILABLE
    }
}
