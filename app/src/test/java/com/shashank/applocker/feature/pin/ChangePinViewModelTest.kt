package com.shashank.applocker.feature.pin

import com.shashank.applocker.MainDispatcherRule
import com.shashank.applocker.domain.model.PinRejection
import com.shashank.applocker.domain.model.PinSetResult
import com.shashank.applocker.domain.model.PinVerifyResult
import com.shashank.applocker.security.FakePinRepository
import com.shashank.applocker.security.LockoutManager
import com.shashank.applocker.security.LockoutRig
import com.shashank.applocker.security.PinAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ChangePinViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val lock = LockoutRig()
    private val pins = FakePinRepository()
    private val vm by lazy { ChangePinViewModel(PinAuthenticator(pins, LockoutManager(lock.repo, lock.clock, lock.credentials)), pins) }

    private fun type(pin: String) = pin.forEach { vm.onEvent(ChangePinEvent.Digit(it)) }

    @Test fun startsByAskingForTheCurrentPin() = assertEquals(ChangePinStep.CURRENT, vm.state.value.step)

    @Test fun wrongCurrentPinIsGenericAndNeverAdvances() {
        pins.verifyResult = PinVerifyResult.Incorrect
        type("135791")
        assertEquals(ChangePinStep.CURRENT, vm.state.value.step)
        assertEquals(ChangePinError.IncorrectCurrent, vm.state.value.error)
        assertEquals("", vm.state.value.pin)
        assertEquals(emptyList<String>(), pins.savedPins)
    }

    @Test fun wrongCurrentPinsCountTowardTheNormalLockout() {
        pins.verifyResult = PinVerifyResult.Incorrect
        repeat(3) { type("135791") }
        assertEquals(ChangePinError.Locked, vm.state.value.error)
        assertEquals(3, lock.repo.state.failedAttempts)
    }

    @Test fun fullFlowSavesTheNewPinOnce() {
        type("135790") // current
        assertEquals(ChangePinStep.NEW, vm.state.value.step)
        type("246813")
        assertEquals(ChangePinStep.CONFIRM, vm.state.value.step)
        type("246813")
        assertEquals(ChangePinStep.DONE, vm.state.value.step)
        assertEquals(listOf("246813"), pins.savedPins)
        assertEquals("", vm.state.value.pin)
    }

    @Test fun weakNewPinIsRejected() {
        type("135790")
        type("111111")
        assertEquals(ChangePinError.Rejected(PinRejection.REPEATED_DIGITS), vm.state.value.error)
        assertEquals(ChangePinStep.NEW, vm.state.value.step)
        assertEquals(emptyList<String>(), pins.savedPins)
    }

    @Test fun newPinMustDifferFromTheCurrentOne() {
        type("135790")
        type("135790")
        assertEquals(ChangePinError.SameAsCurrent, vm.state.value.error)
        assertEquals(ChangePinStep.NEW, vm.state.value.step)
    }

    @Test fun mismatchedConfirmationGoesBackToTheNewPinStep() {
        type("135790")
        type("246813")
        type("246814")
        assertEquals(ChangePinError.Mismatch, vm.state.value.error)
        assertEquals(ChangePinStep.NEW, vm.state.value.step)
        assertEquals(emptyList<String>(), pins.savedPins)
    }

    @Test fun storageFailureWhileSavingKeepsTheOldPinAndAllowsRetry() {
        pins.setResult = PinSetResult.StorageError
        type("135790")
        type("246813")
        type("246813")
        assertEquals(ChangePinError.StorageFailure, vm.state.value.error)
        assertEquals(ChangePinStep.NEW, vm.state.value.step)
    }

    @Test fun inputIsIgnoredAfterCompletionAndForNonDigits() {
        type("135790"); type("246813"); type("246813")
        type("123456")
        vm.onEvent(ChangePinEvent.Digit('x'))
        assertEquals(ChangePinStep.DONE, vm.state.value.step)
        assertEquals("", vm.state.value.pin)
    }

    @Test fun backspaceAndShowToggle() {
        vm.onEvent(ChangePinEvent.Digit('1'))
        vm.onEvent(ChangePinEvent.Backspace)
        assertEquals("", vm.state.value.pin)
        vm.onEvent(ChangePinEvent.ToggleShowPin)
        assertEquals(true, vm.state.value.showPin)
        assertNull(vm.state.value.error)
    }
}
