package com.shashank.applocker.feature.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shashank.applocker.domain.PinValidator
import com.shashank.applocker.domain.model.PIN_LENGTH
import com.shashank.applocker.domain.model.PinRejection
import com.shashank.applocker.domain.model.PinSetResult
import com.shashank.applocker.domain.model.PinValidationResult
import com.shashank.applocker.domain.repository.PinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep { ENTER, CONFIRM }

sealed interface PinSetupError {
    data class Rejected(val reason: PinRejection) : PinSetupError
    data object Mismatch : PinSetupError
    data object StorageFailure : PinSetupError
}

data class PinSetupUiState(
    val step: SetupStep = SetupStep.ENTER,
    /** Digits typed so far on the current step. Only shown on screen if [showPin]. */
    val pin: String = "",
    val showPin: Boolean = false,
    val error: PinSetupError? = null,
    val isSaving: Boolean = false,
)

sealed interface PinSetupEvent {
    data class DigitEntered(val digit: Char) : PinSetupEvent
    data object Backspace : PinSetupEvent
    data object ToggleShowPin : PinSetupEvent
    data object RetrySave : PinSetupEvent
}

sealed interface PinSetupEffect {
    data object Completed : PinSetupEffect
}

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val pinRepository: PinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PinSetupUiState())
    val state: StateFlow<PinSetupUiState> = _state.asStateFlow()

    private val _effects = Channel<PinSetupEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** The validated first entry, held only until it is confirmed or abandoned. */
    private var firstEntry: String? = null

    fun onEvent(event: PinSetupEvent) {
        when (event) {
            is PinSetupEvent.DigitEntered -> onDigit(event.digit)
            PinSetupEvent.Backspace -> _state.update {
                if (it.isSaving) it else it.copy(pin = it.pin.dropLast(1), error = null)
            }
            PinSetupEvent.ToggleShowPin -> _state.update { it.copy(showPin = !it.showPin) }
            PinSetupEvent.RetrySave -> firstEntry?.let(::save)
        }
    }

    private fun onDigit(digit: Char) {
        val current = _state.value
        if (current.isSaving || digit !in '0'..'9' || current.pin.length >= PIN_LENGTH) return
        val pin = current.pin + digit
        _state.update { it.copy(pin = pin, error = null) }
        if (pin.length == PIN_LENGTH) onPinComplete(pin)
    }

    private fun onPinComplete(pin: String) {
        when (_state.value.step) {
            SetupStep.ENTER -> when (val result = PinValidator.validate(pin)) {
                PinValidationResult.Valid -> {
                    firstEntry = pin
                    _state.update { it.copy(step = SetupStep.CONFIRM, pin = "") }
                }
                is PinValidationResult.Rejected ->
                    _state.update { it.copy(pin = "", error = PinSetupError.Rejected(result.reason)) }
            }
            SetupStep.CONFIRM -> if (pin == firstEntry) {
                save(pin)
            } else {
                firstEntry = null
                _state.update { PinSetupUiState(showPin = it.showPin, error = PinSetupError.Mismatch) }
            }
        }
    }

    private fun save(pin: String) {
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (pinRepository.setPin(pin.toCharArray())) {
                PinSetResult.Success -> {
                    firstEntry = null
                    _state.update { it.copy(isSaving = false, pin = "") }
                    _effects.send(PinSetupEffect.Completed)
                }
                PinSetResult.StorageError ->
                    _state.update { it.copy(isSaving = false, pin = "", error = PinSetupError.StorageFailure) }
            }
        }
    }
}
