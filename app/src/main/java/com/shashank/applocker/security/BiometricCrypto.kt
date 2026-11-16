package com.shashank.applocker.security

import javax.crypto.Cipher

/**
 * Why a biometric-key operation failed. Deliberately coarse and free of details: no key
 * names, no exception messages, nothing that could carry secret material.
 */
enum class CryptoFailure {
    /** No key or no enrolled token yet. Fingerprint unlock has not been enabled. */
    NOT_INITIALIZED,

    /**
     * The key can no longer be used, usually because a fingerprint was added. The manager has
     * already deleted the key and the token. The caller must turn fingerprint unlock off and
     * tell the user to re-enable it with their PIN.
     */
    KEY_INVALIDATED,

    /** The cipher was used without a successful biometric authentication. */
    AUTHENTICATION_REQUIRED,

    /** Ciphertext failed authentication or the decrypted token did not match. Never unlocks. */
    DECRYPTION_FAILED,

    /** Key creation needs at least one enrolled fingerprint and there is none. */
    NO_BIOMETRIC_ENROLLED,

    /** Any other Keystore, provider or storage problem. */
    KEYSTORE_FAILURE,
}

/**
 * A [Cipher] that is initialised but not yet authorised. Wrap it in a
 * `BiometricPrompt.CryptoObject`, and pass the same instance to the matching `complete*`
 * function from the prompt's success callback. Single use.
 */
sealed interface CipherPreparation {
    data class Ready(val cipher: Cipher) : CipherPreparation
    data class Failed(val failure: CryptoFailure) : CipherPreparation
}

sealed interface EnrollmentResult {
    data object Success : EnrollmentResult
    data class Failed(val failure: CryptoFailure) : EnrollmentResult
}

sealed interface UnlockTokenResult {
    /** The decrypted token matched the expected value. The only result that may unlock. */
    data object Verified : UnlockTokenResult
    data class Failed(val failure: CryptoFailure) : UnlockTokenResult
}
