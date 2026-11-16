package com.shashank.applocker.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import java.security.InvalidAlgorithmParameterException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException

/**
 * Maps platform exceptions to [CryptoFailure]. The exception itself is dropped: its message
 * and cause are never logged, stored or shown.
 */
object CryptoErrors {
    /** @param duringKeyGeneration true when the failing call was key creation. */
    fun map(e: Throwable, duringKeyGeneration: Boolean = false): CryptoFailure = when (e) {
        is KeyPermanentlyInvalidatedException -> CryptoFailure.KEY_INVALIDATED
        is UserNotAuthenticatedException -> CryptoFailure.AUTHENTICATION_REQUIRED
        // Key creation refuses to proceed when no fingerprint is enrolled.
        is InvalidAlgorithmParameterException ->
            if (duringKeyGeneration) CryptoFailure.NO_BIOMETRIC_ENROLLED else CryptoFailure.KEYSTORE_FAILURE
        // AEADBadTagException is a BadPaddingException: the ciphertext was altered or the key is wrong.
        is BadPaddingException, is IllegalBlockSizeException -> CryptoFailure.DECRYPTION_FAILED
        // KeyStoreException, ProviderException, UnrecoverableKeyException, SecretStoreException, anything else.
        else -> CryptoFailure.KEYSTORE_FAILURE
    }
}
