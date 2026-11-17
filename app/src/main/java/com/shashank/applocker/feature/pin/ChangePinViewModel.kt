package com.shashank.applocker.feature.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shashank.applocker.domain.PinValidator
import com.shashank.applocker.domain.model.AttemptResult
import com.shashank.applocker.domain.model.LockoutStatus
import com.shashank.applocker.domain.model.PIN_LENGTH
import com.shashank.applocker.domain.model.PinRejection
import com.shashank.applocker.domain.model.PinSetResult
import com.shashank.applocker.domain.model.PinValidationResult
import com.shashank.applocker.domain.repository.PinRepository
import com.shashank.applocker.security.PinAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChangePinStep { CURRENT, NEW, CONFIRM, DONE }

sealed interface ChangePinError {
    data object IncorrectCurrent : ChangePinError
    data object Locked : ChangePinError
    data object SameAsCurrent : ChangePinError
    data class Rejected(val reason: PinRejection) : ChangePinError
    data object Mismatch : ChangePinError
    data object StorageFailure : ChangePinError
}

data class ChangePinUiState(
    val step: ChangePinStep = ChangePinStep.CURRENT,
    val pin: String = "",
    val showPin: Boolean = false,
    val error: ChangePinError? = null,
    val isBusy: Boolean = false,
)

sealed interface ChangePinEvent {
    data class Digit(val digit: Char) : ChangePinEvent
    data object Backspace : ChangePinEvent
    data object ToggleShowPin : ChangePinEvent
}

/**
 * Change PIN: current PIN (checked by [PinAuthenticator], so wrong tries count toward the
 * normal lockout), then the new PIN twice. The new PIN gets a fresh random salt and verifier.
 *
 * Nothing else needs re-keying: the AndroidKeyStore keys (the secret-store key and the
 * fingerprint key) are random and independent of the PIN, and the fingerprint token is random,
 * so fingerprint unlock keeps working unchanged. The PIN is held in memory only until this
 * flow ends.
 */
@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val authenticator: PinAuthenticator,
    private val pinRepository: PinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChangePinUiState())
    val state: StateFlow<ChangePinUiState> = _state.asStateFlow()

    private var currentPin: String? = null
    private var firstEntry: String? = null

    fun onEvent(event: ChangePinEvent) {
        when (event) {
            is ChangePinEvent.Digit -> onDigit(event.digit)
            ChangePinEvent.Backspace -> _state.update { if (it.isBusy) it else it.copy(pin = it.pin.dropLast(1), error = null) }
            ChangePinEvent.ToggleShowPin -> _state.update { it.copy(showPin = !it.showPin) }
        }
    }

    private fun onDigit(digit: Char) {
        val s = _state.value
        if (s.isBusy || s.step == ChangePinStep.DONE || digit !in '0'..'9' || s.pin.length >= PIN_LENGTH) return
        val pin = s.pin + digit
        _state.update { it.copy(pin = pin, error = null) }
        if (pin.length == PIN_LENGTH) onComplete(s.step, pin)
    }

    private fun onComplete(step: ChangePinStep, pin: String) {
        when (step) {
            ChangePinStep.CURRENT -> verifyCurrent(pin)
            ChangePinStep.NEW -> when (val v = PinValidator.validate(pin)) {
                PinValidationResult.Valid ->
                    if (pin == currentPin) {
                        _state.update { it.copy(pin = "", error = ChangePinError.SameAsCurrent) }
                    } else {
                        firstEntry = pin
                        _state.update { it.copy(step = ChangePinStep.CONFIRM, pin = "") }
                    }
                is PinValidationResult.Rejected -> _state.update { it.copy(pin = "", error = ChangePinError.Rejected(v.reason)) }
            }
            ChangePinStep.CONFIRM ->
                if (pin == firstEntry) {
                    save(pin)
                } else {
                    firstEntry = null
                    _state.update { it.copy(step = ChangePinStep.NEW, pin = "", error = ChangePinError.Mismatch) }
                }
            ChangePinStep.DONE -> Unit
        }
    }

    private fun verifyCurrent(pin: String) {
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = authenticator.attempt(pin.toCharArray())
            if (result == AttemptResult.Success) {
                currentPin = pin
                _state.update { it.copy(step = ChangePinStep.NEW, pin = "", isBusy = false) }
            } else {
                val error = when (result) {
                    is AttemptResult.Incorrect ->
                        if (result.status == LockoutStatus.Open) ChangePinError.IncorrectCurrent else ChangePinError.Locked
                    is AttemptResult.Locked -> ChangePinError.Locked
                    else -> ChangePinError.StorageFailure
                }
                _state.update { it.copy(pin = "", isBusy = false, error = error) }
            }
        }
    }

    private fun save(pin: String) {
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (pinRepository.setPin(pin.toCharArray())) {
                PinSetResult.Success -> {
                    currentPin = null
                    firstEntry = null
                    _state.update { it.copy(step = ChangePinStep.DONE, pin = "", isBusy = false) }
                }
                PinSetResult.StorageError -> {
                    firstEntry = null
                    _state.update { it.copy(step = ChangePinStep.NEW, pin = "", isBusy = false, error = ChangePinError.StorageFailure) }
                }
            }
        }
    }
}
