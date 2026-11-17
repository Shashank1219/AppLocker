package com.shashank.applocker.security

import androidx.biometric.BiometricManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintAuthTest {
    private val rig = FingerprintRig()
    private val auth get() = rig.auth

    // --- Offered ---

    @Test fun offeredOnlyWhenEnabledAvailableAndEnrolled() = runTest {
        assertTrue(auth.isOffered())
        rig.keys.enrolled = false
        assertFalse(auth.isOffered())
        rig.keys.enrolled = true
        rig.capabilities.code = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        assertFalse(auth.isOffered())
        rig.capabilities.code = BiometricManager.BIOMETRIC_SUCCESS
        rig.settings.setFingerprintEnabled(false)
        assertFalse(auth.isOffered())
    }

    // --- Unlock ---

    @Test fun beginUnlockReturnsTheCipherWhenEverythingIsReady() = runTest {
        assertEquals(FingerprintBegin.Ready(rig.keys.cipher), auth.beginUnlock())
    }

    @Test fun beginUnlockWhenSettingOffIsSilent() = runTest {
        rig.settings.setFingerprintEnabled(false)
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.NONE), auth.beginUnlock())
    }

    @Test fun beginUnlockWithoutEnrolledFingerprintFallsBackWithMessageAndKeepsSetting() = runTest {
        rig.capabilities.code = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.NOT_ENROLLED), auth.beginUnlock())
        assertTrue(rig.enabled)
    }

    @Test fun beginUnlockWithTemporarilyUnavailableSensor() = runTest {
        rig.capabilities.code = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.UNAVAILABLE), auth.beginUnlock())
    }

    @Test fun invalidatedKeyTurnsFingerprintOffAndAsksForPinReEnable() = runTest {
        rig.keys.unlockPrep = CipherPreparation.Failed(CryptoFailure.KEY_INVALIDATED)
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.KEY_CHANGED), auth.beginUnlock())
        assertFalse(rig.enabled)
    }

    @Test fun missingTokenTurnsFingerprintOffQuietly() = runTest {
        rig.keys.unlockPrep = CipherPreparation.Failed(CryptoFailure.NOT_INITIALIZED)
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.NONE), auth.beginUnlock())
        assertFalse(rig.enabled)
    }

    @Test fun otherPreparationFailuresFallBackButDoNotTurnTheSettingOff() = runTest {
        rig.keys.unlockPrep = CipherPreparation.Failed(CryptoFailure.KEYSTORE_FAILURE)
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.FAILED), auth.beginUnlock())
        assertTrue(rig.enabled)
    }

    @Test fun onlyAVerifiedTokenUnlocks() = runTest {
        assertEquals(FingerprintUnlockResult.Verified, auth.completeUnlock(rig.keys.cipher))
        rig.keys.unlockResult = UnlockTokenResult.Failed(CryptoFailure.DECRYPTION_FAILED)
        assertEquals(FingerprintUnlockResult.Failed(FingerprintMessage.FAILED), auth.completeUnlock(rig.keys.cipher))
        rig.keys.unlockResult = UnlockTokenResult.Failed(CryptoFailure.AUTHENTICATION_REQUIRED)
        assertEquals(FingerprintUnlockResult.Failed(FingerprintMessage.FAILED), auth.completeUnlock(rig.keys.cipher))
        assertTrue(rig.enabled)
    }

    @Test fun invalidationDuringDecryptTurnsFingerprintOff() = runTest {
        rig.keys.unlockResult = UnlockTokenResult.Failed(CryptoFailure.KEY_INVALIDATED)
        assertEquals(FingerprintUnlockResult.Failed(FingerprintMessage.KEY_CHANGED), auth.completeUnlock(rig.keys.cipher))
        assertFalse(rig.enabled)
    }

    // --- Enable ---

    @Test fun beginEnableNeedsAnAvailableFingerprint() = runTest {
        rig.capabilities.feature = false
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.UNAVAILABLE), auth.beginEnable())
        rig.capabilities.feature = true
        rig.capabilities.code = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.NOT_ENROLLED), auth.beginEnable())
    }

    @Test fun beginEnableReturnsCipherOrMapsKeyFailures() = runTest {
        assertEquals(FingerprintBegin.Ready(rig.keys.cipher), auth.beginEnable())
        rig.keys.enrollPrep = CipherPreparation.Failed(CryptoFailure.NO_BIOMETRIC_ENROLLED)
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.NOT_ENROLLED), auth.beginEnable())
        rig.keys.enrollPrep = CipherPreparation.Failed(CryptoFailure.KEYSTORE_FAILURE)
        assertEquals(FingerprintBegin.Unavailable(FingerprintMessage.FAILED), auth.beginEnable())
    }

    @Test fun completeEnableSavesTheSettingOnlyOnSuccess() = runTest {
        val off = FingerprintRig(fingerprintEnabled = false)
        assertEquals(FingerprintEnableResult.Enabled, off.auth.completeEnable(off.keys.cipher))
        assertTrue(off.enabled)

        val failing = FingerprintRig(fingerprintEnabled = false)
        failing.keys.enrollResult = EnrollmentResult.Failed(CryptoFailure.AUTHENTICATION_REQUIRED)
        assertEquals(FingerprintEnableResult.Failed(FingerprintMessage.FAILED), failing.auth.completeEnable(failing.keys.cipher))
        assertFalse(failing.enabled)
    }

    @Test fun cancelledEnableLeavesNothingBehind() = runTest {
        auth.cancelEnable()
        assertEquals(1, rig.keys.disableCalls)
        assertFalse(rig.enabled)
    }

    @Test fun disableRemovesKeyAndSetting() = runTest {
        auth.disable()
        assertEquals(1, rig.keys.disableCalls)
        assertFalse(rig.enabled)
    }

    @Test fun reconcileTurnsOffAStaleSettingOnly() = runTest {
        auth.reconcile()
        assertTrue(rig.enabled) // enrolled, nothing to fix
        rig.keys.enrolled = false
        auth.reconcile()
        assertFalse(rig.enabled)
    }

    @Test fun promptErrorsAreMappedWithoutChangingState() = runTest {
        assertEquals(FingerprintMessage.NONE, auth.messageForPromptError(androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertTrue(rig.enabled)
        assertEquals(0, rig.keys.disableCalls)
    }
}
