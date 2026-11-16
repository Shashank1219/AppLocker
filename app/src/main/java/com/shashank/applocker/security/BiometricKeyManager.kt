package com.shashank.applocker.security

/**
 * Keystore-backed unlock secret for fingerprint unlock.
 *
 * The flow is split in two so BiometricPrompt can sit in the middle (Stage 6):
 *
 * Enable (after the PIN has been confirmed):
 * 1. [prepareEnrollment] -> `Ready(cipher)`
 * 2. show `BiometricPrompt` with `CryptoObject(cipher)`
 * 3. on success call [completeEnrollment] with that same cipher
 *
 * Unlock:
 * 1. [prepareUnlock] -> `Ready(cipher)`
 * 2. show `BiometricPrompt` with `CryptoObject(cipher)`
 * 3. on success call [completeUnlock] with that same cipher; only `Verified` may unlock
 *
 * A biometric success that does not end in `Verified` must never unlock anything. Every
 * failure is a [CryptoFailure]; none of them falls back to an unauthenticated path.
 */
interface BiometricKeyManager {
    /** True if a key and an enrolled token both exist. Never throws. */
    suspend fun isEnrolled(): Boolean

    /** Replaces any previous key and token with a fresh key that requires authentication per use. */
    suspend fun prepareEnrollment(): CipherPreparation

    /** Generates the random token, encrypts it with the authorised [cipher] and stores it. */
    suspend fun completeEnrollment(cipher: javax.crypto.Cipher): EnrollmentResult

    suspend fun prepareUnlock(): CipherPreparation

    /** Decrypts the stored token with the authorised [cipher] and checks it. */
    suspend fun completeUnlock(cipher: javax.crypto.Cipher): UnlockTokenResult

    /** Deletes the key and the token. Safe to call at any time. */
    suspend fun disable()
}
