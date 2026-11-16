package com.shashank.applocker.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric only to be able to construct the Android exception classes. */
@RunWith(RobolectricTestRunner::class)
class CryptoErrorsTest {
    private fun map(e: Throwable, keygen: Boolean = false) = CryptoErrors.map(e, keygen)

    @Test fun invalidatedKey() = assertEquals(CryptoFailure.KEY_INVALIDATED, map(KeyPermanentlyInvalidatedException()))

    @Test fun userAuthenticationRequired() =
        assertEquals(CryptoFailure.AUTHENTICATION_REQUIRED, map(UserNotAuthenticatedException()))

    @Test fun decryptionProblems() {
        listOf(AEADBadTagException(), BadPaddingException(), IllegalBlockSizeException()).forEach {
            assertEquals(CryptoFailure.DECRYPTION_FAILED, map(it))
        }
    }

    @Test fun noEnrolledFingerprintOnlyDuringKeyGeneration() {
        assertEquals(CryptoFailure.NO_BIOMETRIC_ENROLLED, map(InvalidAlgorithmParameterException(), keygen = true))
        assertEquals(CryptoFailure.KEYSTORE_FAILURE, map(InvalidAlgorithmParameterException(), keygen = false))
    }

    @Test fun keystoreAndProviderProblems() {
        listOf(KeyStoreException(), ProviderException(), UnrecoverableKeyException(), SecretStoreException(), RuntimeException())
            .forEach { assertEquals(CryptoFailure.KEYSTORE_FAILURE, map(it)) }
    }
}
