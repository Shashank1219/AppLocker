package com.shashank.applocker.security

import android.security.keystore.UserNotAuthenticatedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs the real [DefaultBiometricKeyManager] against a software AES key. Robolectric is only
 * here so the Android exception classes (KeyPermanentlyInvalidatedException and friends)
 * can be constructed. What this cannot show is listed in docs/BIOMETRIC_KEY_TESTING.md.
 */
@RunWith(RobolectricTestRunner::class)
class BiometricKeyManagerTest {
    private val keys = FakeBiometricKeyStore()
    private val store = FakeSecretStore()
    private val tokens = FixedTokenGenerator()
    private val manager = DefaultBiometricKeyManager(keys, store, tokens, Dispatchers.Unconfined)

    private suspend fun enroll() {
        val prep = manager.prepareEnrollment() as CipherPreparation.Ready
        assertEquals(EnrollmentResult.Success, manager.completeEnrollment(prep.cipher))
    }

    private suspend fun unlockResult(): UnlockTokenResult {
        return when (val prep = manager.prepareUnlock()) {
            is CipherPreparation.Ready -> manager.completeUnlock(prep.cipher)
            is CipherPreparation.Failed -> UnlockTokenResult.Failed(prep.failure)
        }
    }

    @Test fun enrollThenUnlockIsVerified() = runTest {
        enroll()
        assertTrue(manager.isEnrolled())
        assertEquals(UnlockTokenResult.Verified, unlockResult())
        assertEquals(UnlockTokenResult.Verified, unlockResult()) // reusable, with a fresh cipher each time
    }

    @Test fun unlockBeforeEnrollmentIsNotInitialized() = runTest {
        assertFalse(manager.isEnrolled())
        assertEquals(UnlockTokenResult.Failed(CryptoFailure.NOT_INITIALIZED), unlockResult())
    }

    @Test fun storedRecordNeverContainsThePlaintextToken() = runTest {
        enroll()
        val stored = store.data.getValue("biometric_token_record")
        val token = tokens.lastIssued!!
        assertFalse(String(stored, Charsets.ISO_8859_1).contains(String(token, Charsets.ISO_8859_1)))
        assertEquals(setOf("biometric_token_record"), store.data.keys)
    }

    @Test fun plaintextTokenIsWipedAfterEnrollment() = runTest {
        enroll()
        assertTrue(tokens.lastIssued!!.all { it == 0.toByte() })
    }

    @Test fun recordHoldsIvCiphertextAndDigestOnly() = runTest {
        enroll()
        val record = BiometricTokenRecord.decode(store.data.getValue("biometric_token_record"))!!
        assertEquals(12, record.iv.size)
        assertEquals(32 + 16, record.ciphertext.size) // token + GCM tag
        assertEquals(32, record.tokenDigest.size)
    }

    @Test fun tamperedCiphertextFailsDecryption() = runTest {
        enroll()
        val bytes = store.data.getValue("biometric_token_record")
        bytes[20] = (bytes[20].toInt() xor 0x01).toByte() // inside the ciphertext
        assertEquals(UnlockTokenResult.Failed(CryptoFailure.DECRYPTION_FAILED), unlockResult())
    }

    @Test fun wrongExpectedDigestFailsEvenIfDecryptionWorks() = runTest {
        enroll()
        val record = BiometricTokenRecord.decode(store.data.getValue("biometric_token_record"))!!
        val forged = BiometricTokenRecord(record.iv, record.ciphertext, ByteArray(32) { 7 })
        store.data["biometric_token_record"] = forged.encode()
        assertEquals(UnlockTokenResult.Failed(CryptoFailure.DECRYPTION_FAILED), unlockResult())
    }

    @Test fun corruptRecordFailsClosed() = runTest {
        enroll()
        store.data["biometric_token_record"] = ByteArray(10)
        assertEquals(UnlockTokenResult.Failed(CryptoFailure.DECRYPTION_FAILED), unlockResult())
    }

    @Test fun invalidatedKeyIsReportedAndEverythingIsDeleted() = runTest {
        enroll()
        keys.invalidated = true
        assertEquals(UnlockTokenResult.Failed(CryptoFailure.KEY_INVALIDATED), unlockResult())
        assertFalse(keys.hasKey())
        assertTrue(store.data.isEmpty())
        assertEquals(UnlockTokenResult.Failed(CryptoFailure.NOT_INITIALIZED), unlockResult())
    }

    @Test fun keyDroppedBySystemIsTreatedAsInvalidated() = runTest {
        enroll()
        keys.dropKey()
        assertEquals(UnlockTokenResult.Failed(CryptoFailure.KEY_INVALIDATED), unlockResult())
        assertTrue(store.data.isEmpty())
    }

    @Test fun noEnrolledFingerprintCannotCreateKey() = runTest {
        keys.noBiometricEnrolled = true
        assertEquals(CipherPreparation.Failed(CryptoFailure.NO_BIOMETRIC_ENROLLED), manager.prepareEnrollment())
        assertFalse(manager.isEnrolled())
    }

    @Test fun keystoreFailureOnGenerateIsReported() = runTest {
        keys.generateFailure = providerFailure()
        assertEquals(CipherPreparation.Failed(CryptoFailure.KEYSTORE_FAILURE), manager.prepareEnrollment())
    }

    @Test fun missingAuthenticationAtCipherInitIsReported() = runTest {
        keys.encryptInitFailure = UserNotAuthenticatedException()
        assertEquals(CipherPreparation.Failed(CryptoFailure.AUTHENTICATION_REQUIRED), manager.prepareEnrollment())
    }

    @Test fun secureStorageFailureDuringEnrollmentLeavesNothingBehind() = runTest {
        val prep = manager.prepareEnrollment() as CipherPreparation.Ready
        store.failing = true
        assertEquals(EnrollmentResult.Failed(CryptoFailure.KEYSTORE_FAILURE), manager.completeEnrollment(prep.cipher))
        store.failing = false
        assertFalse(keys.hasKey())
        assertFalse(manager.isEnrolled())
    }

    @Test fun reenrollingReplacesKeyAndToken() = runTest {
        enroll()
        val first = store.data.getValue("biometric_token_record").copyOf()
        enroll()
        assertEquals(2, keys.generatedKeys)
        assertFalse(first.contentEquals(store.data.getValue("biometric_token_record")))
        assertEquals(UnlockTokenResult.Verified, unlockResult())
    }

    @Test fun disableDeletesKeyAndToken() = runTest {
        enroll()
        manager.disable()
        assertFalse(keys.hasKey())
        assertTrue(store.data.isEmpty())
        assertFalse(manager.isEnrolled())
    }

    @Test fun disableWorksWhenNothingIsEnrolledAndWhenStorageFails() = runTest {
        manager.disable()
        store.failing = true
        manager.disable() // must not throw
        assertNotNull(manager)
    }

    @Test fun isEnrolledNeverThrows() = runTest {
        enroll()
        store.failing = true
        assertFalse(manager.isEnrolled())
    }
}
