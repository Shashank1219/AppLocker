package com.shashank.applocker.feature.pin

import app.cash.turbine.test
import com.shashank.applocker.MainDispatcherRule
import com.shashank.applocker.domain.model.PinVerifyResult
import com.shashank.applocker.security.FakePinRepository
import com.shashank.applocker.security.FingerprintMessage
import com.shashank.applocker.security.FingerprintRig
import com.shashank.applocker.security.CipherPreparation
import com.shashank.applocker.security.CryptoFailure
import com.shashank.applocker.security.UnlockTokenResult
import androidx.biometric.BiometricPrompt
import com.shashank.applocker.security.FakeSettingsRepository
import com.shashank.applocker.security.LockoutManager
import com.shashank.applocker.security.LockoutRig
import com.shashank.applocker.security.PinAuthenticator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PinUnlockViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val rig = LockoutRig()
    private val repo = FakePinRepository()
    private val fp = FingerprintRig(fingerprintEnabled = false)
    private fun newVm() = PinUnlockViewModel(PinAuthenticator(repo, LockoutManager(rig.repo, rig.clock, rig.credentials)), fp.auth)
    // Lazy: the VM starts a coroutine in init, which must happen after MainDispatcherRule has run.
    private val vm by lazy { newVm() }

    private fun type(pin: String, target: PinUnlockViewModel = vm) =
        pin.forEach { target.onEvent(PinUnlockEvent.DigitEntered(it)) }

    @Test fun correctPinEmitsUnlocked() = runTest {
        vm.effects.test {
            type("135790")
            assertEquals(PinUnlockEffect.Unlocked, awaitItem())
        }
        assertEquals("", vm.state.value.pin)
    }

    @Test fun wrongPinShowsGenericErrorAndClearsInput() {
        repo.verifyResult = PinVerifyResult.Incorrect
        type("135791")
        assertEquals(PinUnlockError.Incorrect, vm.state.value.error)
        assertEquals("", vm.state.value.pin)
        assertEquals(LockoutUi.None, vm.state.value.lockout)
    }

    @Test fun errorClearsOnNextDigit() {
        repo.verifyResult = PinVerifyResult.Incorrect
        type("135791")
        type("1")
        assertEquals(null, vm.state.value.error)
        assertEquals("1", vm.state.value.pin)
    }

    @Test fun thirdWrongPinShowsCountdownAndDisablesInput() {
        repo.verifyResult = PinVerifyResult.Incorrect
        repeat(3) { type("135791") }
        assertEquals(LockoutUi.Waiting(30), vm.state.value.lockout)
        assertEquals(false, vm.state.value.inputEnabled)
        type("123")
        assertEquals("", vm.state.value.pin)
        assertEquals(3, repo.verifiedPins.size) // no verification while locked
    }

    @Test fun errorTextStaysGenericAtLockout() {
        repo.verifyResult = PinVerifyResult.Incorrect
        repeat(3) { type("135791") }
        assertEquals(PinUnlockError.Incorrect, vm.state.value.error)
    }

    @Test fun refreshAfterWaitEndsReenablesInput() {
        repo.verifyResult = PinVerifyResult.Incorrect
        repeat(3) { type("135791") }
        rig.clock.advanceSeconds(31)
        vm.onEvent(PinUnlockEvent.Refresh)
        assertEquals(LockoutUi.None, vm.state.value.lockout)
        assertEquals(true, vm.state.value.inputEnabled)
    }

    @Test fun refreshShowsRemainingTimeFromTheClock() {
        repo.verifyResult = PinVerifyResult.Incorrect
        repeat(3) { type("135791") }
        rig.clock.advanceSeconds(12)
        vm.onEvent(PinUnlockEvent.Refresh)
        assertEquals(LockoutUi.Waiting(18), vm.state.value.lockout)
    }

    @Test fun newViewModelAfterProcessDeathStillShowsLockout() {
        repo.verifyResult = PinVerifyResult.Incorrect
        repeat(3) { type("135791") }
        rig.clock.advanceSeconds(5)
        val reborn = newVm()
        assertEquals(LockoutUi.Waiting(25), reborn.state.value.lockout)
        repo.verifyResult = PinVerifyResult.Success
        type("135790", reborn)
        assertEquals("", reborn.state.value.pin)
        assertEquals(0, repo.verifiedPins.size - 3) // correct PIN was not even checked
    }

    @Test fun tenFailuresShowDeviceCredentialAndRecoveryResetsIt() = runTest {
        repo.verifyResult = PinVerifyResult.Incorrect
        repeat(10) { type("135791"); rig.clock.advanceSeconds(400); vm.onEvent(PinUnlockEvent.Refresh) }
        assertEquals(LockoutUi.CredentialRequired, vm.state.value.lockout)
        vm.effects.test {
            vm.onEvent(PinUnlockEvent.DeviceCredentialSucceeded)
            assertEquals(PinUnlockEffect.RecoveryAuthorized, awaitItem())
        }
        assertEquals(LockoutUi.None, vm.state.value.lockout)
        assertEquals(0, rig.repo.state.failedAttempts)
    }

    @Test fun storageFailureIsDistinctFromIncorrect() {
        repo.verifyResult = PinVerifyResult.StorageError
        type("135790")
        assertEquals(PinUnlockError.StorageFailure, vm.state.value.error)
    }

    @Test fun unreadableLockoutStateShowsStorageErrorAndNeverUnlocks() = runTest {
        rig.repo.failing = true
        val broken = newVm()
        assertEquals(PinUnlockError.StorageFailure, broken.state.value.error)
        broken.effects.test {
            type("135790", broken)
            expectNoEvents()
        }
    }

    @Test fun ignoresNonDigits() {
        type("12")
        vm.onEvent(PinUnlockEvent.DigitEntered('a'))
        assertEquals("12", vm.state.value.pin)
    }

    // --- Fingerprint ---

    private fun fingerprintOn() { fp.settings.state.value = fp.settings.state.value.copy(fingerprintEnabled = true) }

    @Test fun fingerprintIsOnlyOfferedWhenEnabledAvailableAndEnrolled() {
        assertEquals(false, vm.state.value.fingerprintOffered)
        fingerprintOn()
        val on = newVm()
        assertEquals(true, on.state.value.fingerprintOffered)
    }

    @Test fun buttonStartsThePromptWithTheKeystoreCipher() = runTest {
        fingerprintOn()
        val on = newVm()
        on.effects.test {
            on.onEvent(PinUnlockEvent.FingerprintRequested())
            assertEquals(PinUnlockEffect.ShowFingerprintPrompt(fp.keys.cipher), awaitItem())
        }
    }

    @Test fun automaticPromptShowsOnlyOnce() = runTest {
        fingerprintOn()
        val on = newVm()
        on.effects.test {
            on.onEvent(PinUnlockEvent.FingerprintRequested(automatic = true))
            awaitItem()
            on.onEvent(PinUnlockEvent.FingerprintError(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
            on.onEvent(PinUnlockEvent.FingerprintRequested(automatic = true))
            expectNoEvents()
            on.onEvent(PinUnlockEvent.FingerprintRequested()) // the button still works
            assertEquals(PinUnlockEffect.ShowFingerprintPrompt(fp.keys.cipher), awaitItem())
        }
    }

    @Test fun verifiedTokenUnlocks() = runTest {
        fingerprintOn()
        val on = newVm()
        on.effects.test {
            on.onEvent(PinUnlockEvent.FingerprintSucceeded(fp.keys.cipher))
            assertEquals(PinUnlockEffect.Unlocked, awaitItem())
        }
    }

    @Test fun biometricSuccessWithoutAVerifiedTokenNeverUnlocks() = runTest {
        fingerprintOn()
        fp.keys.unlockResult = UnlockTokenResult.Failed(CryptoFailure.DECRYPTION_FAILED)
        val on = newVm()
        on.effects.test {
            on.onEvent(PinUnlockEvent.FingerprintSucceeded(fp.keys.cipher))
            expectNoEvents()
        }
        assertEquals(FingerprintMessage.FAILED, on.state.value.fingerprintMessage)
    }

    @Test fun cancelAndUsePinNeverTouchTheFailureCounter() {
        fingerprintOn()
        val on = newVm()
        listOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED)
            .forEach { on.onEvent(PinUnlockEvent.FingerprintError(it)) }
        assertEquals(0, rig.repo.state.failedAttempts)
        assertEquals(FingerprintMessage.NONE, on.state.value.fingerprintMessage)
        assertEquals(LockoutUi.None, on.state.value.lockout)
        assertEquals(null, on.state.value.error)
    }

    @Test fun biometricLockoutFallsBackToPinWithMessageAndNoCounterChange() {
        fingerprintOn()
        val on = newVm()
        on.onEvent(PinUnlockEvent.FingerprintError(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(FingerprintMessage.LOCKED, on.state.value.fingerprintMessage)
        assertEquals(0, rig.repo.state.failedAttempts)
        assertEquals(true, on.state.value.inputEnabled) // PIN keypad stays usable
    }

    @Test fun invalidatedKeyTurnsFingerprintOffAndTellsTheUser() = runTest {
        fingerprintOn()
        fp.keys.unlockPrep = CipherPreparation.Failed(CryptoFailure.KEY_INVALIDATED)
        val on = newVm()
        on.effects.test {
            on.onEvent(PinUnlockEvent.FingerprintRequested())
            expectNoEvents()
        }
        assertEquals(FingerprintMessage.KEY_CHANGED, on.state.value.fingerprintMessage)
        assertEquals(false, on.state.value.fingerprintOffered)
        assertEquals(false, fp.settings.state.value.fingerprintEnabled)
    }

    @Test fun typingThePinClearsTheFingerprintMessage() {
        fingerprintOn()
        val on = newVm()
        on.onEvent(PinUnlockEvent.FingerprintError(BiometricPrompt.ERROR_LOCKOUT))
        type("1", on)
        assertEquals(FingerprintMessage.NONE, on.state.value.fingerprintMessage)
    }

    // runBlocking, not runTest: with a lockout active the countdown coroutine loops forever on
    // virtual time, and runTest would try to run it to completion.
    @Test fun fingerprintWorksWhilePinIsLockedOutButDoesNotResetTheCounter() = kotlinx.coroutines.runBlocking {
        repo.verifyResult = PinVerifyResult.Incorrect
        repeat(3) { type("135791") }
        assertEquals(LockoutUi.Waiting(30), vm.state.value.lockout)
        fingerprintOn()
        val on = newVm()
        on.effects.test {
            on.onEvent(PinUnlockEvent.FingerprintSucceeded(fp.keys.cipher))
            assertEquals(PinUnlockEffect.Unlocked, awaitItem())
        }
        assertEquals(3, rig.repo.state.failedAttempts)
    }
}
