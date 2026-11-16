package com.shashank.applocker.feature.pin

import app.cash.turbine.test
import com.shashank.applocker.MainDispatcherRule
import com.shashank.applocker.domain.model.PinRejection
import com.shashank.applocker.domain.model.PinSetResult
import com.shashank.applocker.security.FakePinRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PinSetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakePinRepository()
    private val vm = PinSetupViewModel(repo)

    private fun type(pin: String) = pin.forEach { vm.onEvent(PinSetupEvent.DigitEntered(it)) }

    @Test fun validPinMovesToConfirm() {
        type("135790")
        assertEquals(SetupStep.CONFIRM, vm.state.value.step)
        assertEquals("", vm.state.value.pin)
    }

    @Test fun trivialPinIsRejectedAndStaysOnEnter() {
        type("123456")
        assertEquals(SetupStep.ENTER, vm.state.value.step)
        assertEquals(PinSetupError.Rejected(PinRejection.SEQUENTIAL), vm.state.value.error)
        type("111111")
        assertEquals(PinSetupError.Rejected(PinRejection.REPEATED_DIGITS), vm.state.value.error)
    }

    @Test fun matchingConfirmSavesAndCompletes() = runTest {
        vm.effects.test {
            type("135790")
            type("135790")
            assertEquals(PinSetupEffect.Completed, awaitItem())
        }
        assertEquals(listOf("135790"), repo.savedPins)
    }

    @Test fun mismatchRestartsWithoutSaving() {
        type("135790")
        type("135791")
        assertEquals(SetupStep.ENTER, vm.state.value.step)
        assertEquals(PinSetupError.Mismatch, vm.state.value.error)
        assertEquals(emptyList<String>(), repo.savedPins)
    }

    @Test fun storageFailureShowsErrorAndRetryWorks() = runTest {
        repo.setResult = PinSetResult.StorageError
        type("135790")
        type("135790")
        assertEquals(PinSetupError.StorageFailure, vm.state.value.error)
        repo.setResult = PinSetResult.Success
        vm.effects.test {
            vm.onEvent(PinSetupEvent.RetrySave)
            assertEquals(PinSetupEffect.Completed, awaitItem())
        }
    }

    @Test fun backspaceAndDigitFilterAndToggle() {
        type("12")
        vm.onEvent(PinSetupEvent.Backspace)
        assertEquals("1", vm.state.value.pin)
        vm.onEvent(PinSetupEvent.DigitEntered('x'))
        assertEquals("1", vm.state.value.pin)
        vm.onEvent(PinSetupEvent.ToggleShowPin)
        assertEquals(true, vm.state.value.showPin)
    }
}
