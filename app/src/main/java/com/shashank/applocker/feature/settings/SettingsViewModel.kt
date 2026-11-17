package com.shashank.applocker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shashank.applocker.domain.model.AttemptResult
import com.shashank.applocker.domain.model.LockoutStatus
import com.shashank.applocker.domain.model.RelockDelay
import com.shashank.applocker.domain.model.PIN_LENGTH
import com.shashank.applocker.domain.repository.SettingsRepository
import com.shashank.applocker.security.AppDataWiper
import com.shashank.applocker.security.WipeResult
import com.shashank.applocker.security.BiometricAvailability
import com.shashank.applocker.security.FingerprintAuth
import com.shashank.applocker.security.FingerprintBegin
import com.shashank.applocker.security.FingerprintEnableResult
import com.shashank.applocker.security.FingerprintMessage
import com.shashank.applocker.security.PinAuthenticator
import com.shashank.applocker.security.UnlockSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConfirmPurpose { FINGERPRINT, WIPE }

enum class PinConfirmError { INCORRECT, LOCKED, STORAGE }

/** PIN re-entry shown before fingerprint unlock can be turned on. */
data class PinConfirmState(
    val pin: String = "",
    val error: PinConfirmError? = null,
    val isVerifying: Boolean = false,
    val purpose: ConfirmPurpose = ConfirmPurpose.FINGERPRINT,
)

sealed interface SettingsMessage {
    data object FingerprintEnabled : SettingsMessage
    data object FingerprintDisabled : SettingsMessage
    data object AllLocked : SettingsMessage
    data object WipeIncomplete : SettingsMessage
    data class Fingerprint(val message: FingerprintMessage) : SettingsMessage
}

data class SettingsUiState(
    val fingerprintEnabled: Boolean = false,
    val relockDelay: RelockDelay = RelockDelay.DEFAULT,
    val availability: BiometricAvailability = BiometricAvailability.UNSUPPORTED,
    val pinConfirm: PinConfirmState? = null,
    /** The final "erase everything?" dialog, shown after the PIN was confirmed. */
    val confirmWipe: Boolean = false,
    val wiping: Boolean = false,
    val message: SettingsMessage? = null,
) {
    /** The switch can be turned on only when a Strong fingerprint is available. Turning off always works. */
    val fingerprintToggleEnabled: Boolean
        get() = fingerprintEnabled || availability == BiometricAvailability.AVAILABLE
}

sealed interface SettingsEvent {
    data class ToggleFingerprint(val enable: Boolean) : SettingsEvent
    data class PinDigit(val digit: Char) : SettingsEvent
    data object PinBackspace : SettingsEvent
    data object CancelPinConfirm : SettingsEvent
    data object Refresh : SettingsEvent
    data class SetRelockDelay(val delay: RelockDelay) : SettingsEvent
    data object LockAllNow : SettingsEvent
    data object WipeRequested : SettingsEvent
    data object ConfirmWipe : SettingsEvent
    data object DismissWipe : SettingsEvent

    /** The enable prompt authenticated with the Keystore-authorised [cipher]. */
    data class EnrollmentAuthenticated(val cipher: Cipher) : SettingsEvent
    data class EnrollmentError(val errorCode: Int) : SettingsEvent
}

sealed interface SettingsEffect {
    /** Show the system fingerprint prompt using this cipher in a CryptoObject. */
    data class ShowEnrollPrompt(val cipher: Cipher) : SettingsEffect

    /** Everything was erased. The app must go back to first-run setup. */
    data object Wiped : SettingsEffect
}

/**
 * Turning fingerprint on: switch -> PIN confirmation (counted by the normal lockout) ->
 * system fingerprint prompt bound to the new Keystore key -> token stored -> setting saved.
 * Any failure or cancel along the way leaves fingerprint off with no key or token behind.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val authenticator: PinAuthenticator,
    private val fingerprint: FingerprintAuth,
    private val sessions: UnlockSessionManager,
    private val wiper: AppDataWiper,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        settings.settings
            .onEach { s -> _state.update { it.copy(fingerprintEnabled = s.fingerprintEnabled, relockDelay = s.relockDelay) } }
            .launchIn(viewModelScope)
        refresh()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ToggleFingerprint -> if (event.enable) startEnable() else disable()
            is SettingsEvent.PinDigit -> onPinDigit(event.digit)
            SettingsEvent.PinBackspace -> _state.update { s ->
                val c = s.pinConfirm
                if (c == null || c.isVerifying) s else s.copy(pinConfirm = c.copy(pin = c.pin.dropLast(1), error = null))
            }
            SettingsEvent.CancelPinConfirm -> _state.update { it.copy(pinConfirm = null) }
            SettingsEvent.Refresh -> refresh()
            is SettingsEvent.SetRelockDelay -> viewModelScope.launch { settings.setRelockDelay(event.delay) }
            SettingsEvent.WipeRequested -> _state.update { it.copy(pinConfirm = PinConfirmState(purpose = ConfirmPurpose.WIPE), message = null) }
            SettingsEvent.DismissWipe -> _state.update { if (it.wiping) it else it.copy(confirmWipe = false) }
            SettingsEvent.ConfirmWipe -> wipe()
            SettingsEvent.LockAllNow -> {
                sessions.lockAll()
                _state.update { it.copy(message = SettingsMessage.AllLocked) }
            }
            is SettingsEvent.EnrollmentAuthenticated -> completeEnable(event.cipher)
            is SettingsEvent.EnrollmentError -> enrollmentError(event.errorCode)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            fingerprint.reconcile()
            _state.update { it.copy(availability = fingerprint.availability()) }
        }
    }

    private fun startEnable() {
        val availability = fingerprint.availability()
        _state.update { it.copy(availability = availability) }
        if (availability != BiometricAvailability.AVAILABLE) {
            _state.update { it.copy(message = SettingsMessage.Fingerprint(com.shashank.applocker.security.BiometricErrorPolicy.messageFor(availability))) }
            return
        }
        _state.update { it.copy(pinConfirm = PinConfirmState(), message = null) }
    }

    private fun disable() {
        viewModelScope.launch {
            fingerprint.disable()
            _state.update { it.copy(message = SettingsMessage.FingerprintDisabled) }
        }
    }

    private fun onPinDigit(digit: Char) {
        val confirm = _state.value.pinConfirm ?: return
        if (confirm.isVerifying || digit !in '0'..'9' || confirm.pin.length >= PIN_LENGTH) return
        val pin = confirm.pin + digit
        _state.update { it.copy(pinConfirm = confirm.copy(pin = pin, error = null)) }
        if (pin.length == PIN_LENGTH) verifyPin(pin)
    }

    private fun verifyPin(pin: String) {
        val confirm = _state.value.pinConfirm ?: return
        _state.update { s -> s.copy(pinConfirm = s.pinConfirm?.copy(isVerifying = true)) }
        viewModelScope.launch {
            val result = authenticator.attempt(pin.toCharArray())
            if (result == AttemptResult.Success) {
                val purpose = confirm.purpose
                _state.update { it.copy(pinConfirm = null, confirmWipe = purpose == ConfirmPurpose.WIPE) }
                if (purpose == ConfirmPurpose.FINGERPRINT) beginEnrollment()
            } else {
                val error = when (result) {
                    is AttemptResult.Incorrect ->
                        if (result.status == LockoutStatus.Open) PinConfirmError.INCORRECT else PinConfirmError.LOCKED
                    is AttemptResult.Locked -> PinConfirmError.LOCKED
                    else -> PinConfirmError.STORAGE
                }
                _state.update { it.copy(pinConfirm = confirm.copy(pin = "", error = error, isVerifying = false)) }
            }
        }
    }

    private fun wipe() {
        if (!_state.value.confirmWipe || _state.value.wiping) return
        _state.update { it.copy(wiping = true) }
        viewModelScope.launch {
            val result = wiper.wipe()
            _state.update { it.copy(wiping = false, confirmWipe = false, message = if (result == WipeResult.SUCCESS) null else SettingsMessage.WipeIncomplete) }
            if (result == WipeResult.SUCCESS) _effects.send(SettingsEffect.Wiped)
        }
    }

    private suspend fun beginEnrollment() {
        when (val begin = fingerprint.beginEnable()) {
            is FingerprintBegin.Ready -> _effects.send(SettingsEffect.ShowEnrollPrompt(begin.cipher))
            is FingerprintBegin.Unavailable ->
                _state.update { it.copy(message = SettingsMessage.Fingerprint(begin.message)) }
        }
    }

    private fun completeEnable(cipher: Cipher) {
        viewModelScope.launch {
            val message = when (val result = fingerprint.completeEnable(cipher)) {
                FingerprintEnableResult.Enabled -> SettingsMessage.FingerprintEnabled
                is FingerprintEnableResult.Failed -> SettingsMessage.Fingerprint(result.message)
            }
            _state.update { it.copy(message = message) }
        }
    }

    private fun enrollmentError(errorCode: Int) {
        viewModelScope.launch {
            fingerprint.cancelEnable()
            val message = fingerprint.messageForPromptError(errorCode)
            _state.update { it.copy(message = if (message == FingerprintMessage.NONE) null else SettingsMessage.Fingerprint(message)) }
        }
    }
}
