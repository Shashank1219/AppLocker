package com.shashank.applocker.feature.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import app.cash.turbine.test
import com.shashank.applocker.MainDispatcherRule
import com.shashank.applocker.domain.model.PinVerifyResult
import com.shashank.applocker.security.CipherPreparation
import com.shashank.applocker.security.CryptoFailure
import com.shashank.applocker.security.EnrollmentResult
import com.shashank.applocker.security.FakePinRepository
import com.shashank.applocker.security.FingerprintMessage
import com.shashank.applocker.security.FingerprintRig
import com.shashank.applocker.security.LockoutManager
import com.shashank.applocker.security.LockoutRig
import com.shashank.applocker.security.PinAuthenticator
import com.shashank.applocker.security.UnlockSessionManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val lock = LockoutRig()
    private val pins = FakePinRepository()
    private val fp = FingerprintRig(fingerprintEnabled = false)
    private val sessions = UnlockSessionManager(lock.clock)
    // Lazy: the ViewModel starts coroutines in init, after MainDispatcherRule has run.
    private val vm by lazy {
        SettingsViewModel(fp.settings, PinAuthenticator(pins, LockoutManager(lock.repo, lock.clock, lock.credentials)), fp.auth, sessions)
    }

    private fun typePin(pin: String) = pin.forEach { vm.onEvent(SettingsEvent.PinDigit(it)) }

    @Test fun relockDelayIsSavedAndReflected() {
        vm.onEvent(SettingsEvent.SetRelockDelay(com.shashank.applocker.domain.model.RelockDelay.AFTER_5_MINUTES))
        assertEquals(com.shashank.applocker.domain.model.RelockDelay.AFTER_5_MINUTES, fp.settings.state.value.relockDelay)
        assertEquals(com.shashank.applocker.domain.model.RelockDelay.AFTER_5_MINUTES, vm.state.value.relockDelay)
    }

    @Test fun lockAllNowClearsEveryUnlock() {
        sessions.onForeground("a")
        sessions.onUnlocked("a")
        vm.onEvent(SettingsEvent.LockAllNow)
        assertTrue(sessions.requiresUnlock("a", com.shashank.applocker.domain.model.RelockDelay.ON_SCREEN_OFF))
        assertEquals(SettingsMessage.AllLocked, vm.state.value.message)
    }

    @Test fun startsOffAndReportsAvailability() {
        assertFalse(vm.state.value.fingerprintEnabled)
        assertEquals(com.shashank.applocker.security.BiometricAvailability.AVAILABLE, vm.state.value.availability)
        assertTrue(vm.state.value.fingerprintToggleEnabled)
    }

    @Test fun togglingOnAsksForThePinFirst() = runTest {
        vm.effects.test {
            vm.onEvent(SettingsEvent.ToggleFingerprint(true))
            assertEquals(PinConfirmState(), vm.state.value.pinConfirm)
            expectNoEvents() // no key, no prompt before the PIN is confirmed
        }
        assertEquals(0, fp.keys.disableCalls)
    }

    @Test fun noHardwareMeansSwitchIsDisabledAndTogglingExplains() {
        fp.capabilities.feature = false
        vm.onEvent(SettingsEvent.Refresh)
        assertFalse(vm.state.value.fingerprintToggleEnabled)
        vm.onEvent(SettingsEvent.ToggleFingerprint(true))
        assertNull(vm.state.value.pinConfirm)
        assertEquals(SettingsMessage.Fingerprint(FingerprintMessage.UNAVAILABLE), vm.state.value.message)
    }

    @Test fun noEnrolledFingerprintExplainsAndDoesNotAskForPin() {
        fp.capabilities.code = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        vm.onEvent(SettingsEvent.ToggleFingerprint(true))
        assertNull(vm.state.value.pinConfirm)
        assertEquals(SettingsMessage.Fingerprint(FingerprintMessage.NOT_ENROLLED), vm.state.value.message)
    }

    @Test fun wrongPinShowsGenericErrorAndNeverStartsEnrollment() = runTest {
        pins.verifyResult = PinVerifyResult.Incorrect
        vm.effects.test {
            vm.onEvent(SettingsEvent.ToggleFingerprint(true))
            typePin("135791")
            assertEquals(PinConfirmError.INCORRECT, vm.state.value.pinConfirm?.error)
            assertEquals("", vm.state.value.pinConfirm?.pin)
            expectNoEvents()
        }
        assertFalse(fp.enabled)
    }

    @Test fun repeatedWrongPinsHitTheNormalLockout() {
        pins.verifyResult = PinVerifyResult.Incorrect
        vm.onEvent(SettingsEvent.ToggleFingerprint(true))
        repeat(3) { typePin("135791") }
        assertEquals(PinConfirmError.LOCKED, vm.state.value.pinConfirm?.error)
        assertEquals(3, lock.repo.state.failedAttempts)
    }

    @Test fun correctPinStartsEnrollmentWithTheKeystoreCipher() = runTest {
        vm.effects.test {
            vm.onEvent(SettingsEvent.ToggleFingerprint(true))
            typePin("135790")
            assertEquals(SettingsEffect.ShowEnrollPrompt(fp.keys.cipher), awaitItem())
        }
        assertNull(vm.state.value.pinConfirm)
        assertFalse(fp.enabled) // not enabled until the fingerprint prompt succeeds
    }

    @Test fun successfulPromptEnablesFingerprint() = runTest {
        vm.onEvent(SettingsEvent.EnrollmentAuthenticated(fp.keys.cipher))
        assertTrue(fp.enabled)
        assertTrue(vm.state.value.fingerprintEnabled)
        assertEquals(SettingsMessage.FingerprintEnabled, vm.state.value.message)
    }

    @Test fun enrollmentFailureLeavesFingerprintOff() {
        fp.keys.enrollResult = EnrollmentResult.Failed(CryptoFailure.AUTHENTICATION_REQUIRED)
        vm.onEvent(SettingsEvent.EnrollmentAuthenticated(fp.keys.cipher))
        assertFalse(fp.enabled)
        assertEquals(SettingsMessage.Fingerprint(FingerprintMessage.FAILED), vm.state.value.message)
    }

    @Test fun cancellingThePromptCleansUpAndStaysQuiet() {
        vm.onEvent(SettingsEvent.EnrollmentError(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertEquals(1, fp.keys.disableCalls)
        assertFalse(fp.enabled)
        assertNull(vm.state.value.message)
    }

    @Test fun promptLockoutErrorCleansUpAndExplains() {
        vm.onEvent(SettingsEvent.EnrollmentError(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(1, fp.keys.disableCalls)
        assertEquals(SettingsMessage.Fingerprint(FingerprintMessage.LOCKED), vm.state.value.message)
    }

    @Test fun keyCreationFailureIsReported() = runTest {
        fp.keys.enrollPrep = CipherPreparation.Failed(CryptoFailure.NO_BIOMETRIC_ENROLLED)
        vm.effects.test {
            vm.onEvent(SettingsEvent.ToggleFingerprint(true))
            typePin("135790")
            expectNoEvents()
        }
        assertEquals(SettingsMessage.Fingerprint(FingerprintMessage.NOT_ENROLLED), vm.state.value.message)
    }

    @Test fun cancellingPinConfirmationReturnsToSettings() {
        vm.onEvent(SettingsEvent.ToggleFingerprint(true))
        vm.onEvent(SettingsEvent.CancelPinConfirm)
        assertNull(vm.state.value.pinConfirm)
    }

    @Test fun turningOffNeedsNoPinAndRemovesKeyAndToken() {
        fp.settings.state.value = fp.settings.state.value.copy(fingerprintEnabled = true)
        vm.onEvent(SettingsEvent.ToggleFingerprint(false))
        assertEquals(1, fp.keys.disableCalls)
        assertFalse(fp.enabled)
        assertEquals(SettingsMessage.FingerprintDisabled, vm.state.value.message)
        assertNull(vm.state.value.pinConfirm)
    }

    @Test fun switchCanAlwaysBeTurnedOffEvenWhenFingerprintIsGone() {
        fp.settings.state.value = fp.settings.state.value.copy(fingerprintEnabled = true)
        fp.capabilities.feature = false
        vm.onEvent(SettingsEvent.Refresh)
        assertTrue(vm.state.value.fingerprintToggleEnabled)
    }

    @Test fun staleOnSettingIsCorrectedWhenNothingIsEnrolled() {
        fp.settings.state.value = fp.settings.state.value.copy(fingerprintEnabled = true)
        fp.keys.enrolled = false
        vm.onEvent(SettingsEvent.Refresh)
        assertFalse(fp.enabled)
        assertFalse(vm.state.value.fingerprintEnabled)
    }

    @Test fun invalidationElsewhereIsReflectedInTheSwitch() {
        fp.settings.state.value = fp.settings.state.value.copy(fingerprintEnabled = true)
        assertTrue(vm.state.value.fingerprintEnabled)
        fp.settings.state.value = fp.settings.state.value.copy(fingerprintEnabled = false) // unlock screen found the key invalidated
        assertFalse(vm.state.value.fingerprintEnabled)
    }
}
