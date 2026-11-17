package com.shashank.applocker.feature.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shashank.applocker.domain.model.AttemptResult
import com.shashank.applocker.domain.model.LockoutStatus
import com.shashank.applocker.domain.model.PIN_LENGTH
import com.shashank.applocker.security.FingerprintAuth
import com.shashank.applocker.security.FingerprintBegin
import com.shashank.applocker.security.FingerprintMessage
import com.shashank.applocker.security.FingerprintUnlockResult
import com.shashank.applocker.security.PinAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PinUnlockError {
    /** Generic on purpose: one message for every wrong PIN. */
    data object Incorrect : PinUnlockError
    data object StorageFailure : PinUnlockError
}

sealed interface LockoutUi {
    data object None : LockoutUi
    data class Waiting(val remainingSeconds: Int) : LockoutUi
    data object CredentialRequired : LockoutUi
}

data class PinUnlockUiState(
    val pin: String = "",
    val showPin: Boolean = false,
    val error: PinUnlockError? = null,
    val isVerifying: Boolean = false,
    val lockout: LockoutUi = LockoutUi.None,
    /** Fingerprint is enabled, available and enrolled: show the "Use fingerprint" button. */
    val fingerprintOffered: Boolean = false,
    /** Why fingerprint fell back to the PIN. NONE shows nothing. */
    val fingerprintMessage: FingerprintMessage = FingerprintMessage.NONE,
) {
    val inputEnabled: Boolean get() = !isVerifying && lockout == LockoutUi.None
}

sealed interface PinUnlockEvent {
    data class DigitEntered(val digit: Char) : PinUnlockEvent
    data object Backspace : PinUnlockEvent
    data object ToggleShowPin : PinUnlockEvent

    /** Re-read the lockout state, e.g. when the screen comes back to the foreground. */
    data object Refresh : PinUnlockEvent

    /** The system confirmed the device screen lock (recovery after 10 failures). */
    data object DeviceCredentialSucceeded : PinUnlockEvent

    /** The button, or [automatic] = true for the one prompt shown when the screen first appears. */
    data class FingerprintRequested(val automatic: Boolean = false) : PinUnlockEvent

    /** The prompt authenticated; [cipher] is the Keystore-authorised cipher from the CryptoObject. */
    data class FingerprintSucceeded(val cipher: Cipher) : PinUnlockEvent

    /** The prompt ended without success (cancel, "Use PIN", lockout, hardware problem...). */
    data class FingerprintError(val errorCode: Int) : PinUnlockEvent
}

sealed interface PinUnlockEffect {
    data object Unlocked : PinUnlockEffect

    /** Device credential confirmed: the user may now choose a new PIN. */
    data object RecoveryAuthorized : PinUnlockEffect

    /** Show the system fingerprint prompt using this cipher in a CryptoObject. */
    data class ShowFingerprintPrompt(val cipher: Cipher) : PinUnlockEffect
}

/**
 * The lockout state is read from encrypted storage on creation and on every tick, so rotation,
 * backgrounding, process death and relaunch all show the same countdown. The countdown is
 * recomputed from the clock each second, never decremented locally.
 *
 * Fingerprint never touches the PIN failure counter, and only a Keystore-verified token
 * ([FingerprintUnlockResult.Verified]) produces [PinUnlockEffect.Unlocked].
 */
@HiltViewModel
class PinUnlockViewModel @Inject constructor(
    private val authenticator: PinAuthenticator,
    private val fingerprint: FingerprintAuth,
) : ViewModel() {
    private val _state = MutableStateFlow(PinUnlockUiState())
    val state: StateFlow<PinUnlockUiState> = _state.asStateFlow()

    private val _effects = Channel<PinUnlockEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var ticker: Job? = null
    private var autoPromptDone = false
    private var promptActive = false

    init {
        refresh()
    }

    fun onEvent(event: PinUnlockEvent) {
        when (event) {
            is PinUnlockEvent.DigitEntered -> onDigit(event.digit)
            PinUnlockEvent.Backspace -> _state.update {
                if (!it.inputEnabled) it else it.copy(pin = it.pin.dropLast(1), error = null)
            }
            PinUnlockEvent.ToggleShowPin -> _state.update { it.copy(showPin = !it.showPin) }
            PinUnlockEvent.Refresh -> refresh()
            PinUnlockEvent.DeviceCredentialSucceeded -> onDeviceCredential()
            is PinUnlockEvent.FingerprintRequested -> onFingerprintRequested(event.automatic)
            is PinUnlockEvent.FingerprintSucceeded -> onFingerprintSucceeded(event.cipher)
            is PinUnlockEvent.FingerprintError -> onFingerprintError(event.errorCode)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            applyStatus(authenticator.status())
            val offered = fingerprint.isOffered()
            _state.update { it.copy(fingerprintOffered = offered) }
        }
    }

    private fun onDigit(digit: Char) {
        val current = _state.value
        if (!current.inputEnabled || digit !in '0'..'9' || current.pin.length >= PIN_LENGTH) return
        val pin = current.pin + digit
        _state.update { it.copy(pin = pin, error = null, fingerprintMessage = FingerprintMessage.NONE) }
        if (pin.length == PIN_LENGTH) verify(pin)
    }

    private fun verify(pin: String) {
        _state.update { it.copy(isVerifying = true) }
        viewModelScope.launch {
            val result = authenticator.attempt(pin.toCharArray())
            _state.update { it.copy(isVerifying = false, pin = "") }
            when (result) {
                AttemptResult.Success -> _effects.send(PinUnlockEffect.Unlocked)
                is AttemptResult.Incorrect -> {
                    _state.update { it.copy(error = PinUnlockError.Incorrect) }
                    applyStatus(result.status)
                }
                is AttemptResult.Locked -> applyStatus(result.status)
                AttemptResult.StorageError ->
                    _state.update { it.copy(error = PinUnlockError.StorageFailure) }
            }
        }
    }

    private fun onFingerprintRequested(automatic: Boolean) {
        if (promptActive || _state.value.isVerifying) return
        if (automatic) {
            if (autoPromptDone) return
            autoPromptDone = true
        }
        promptActive = true
        viewModelScope.launch {
            when (val begin = fingerprint.beginUnlock()) {
                is FingerprintBegin.Ready -> _effects.send(PinUnlockEffect.ShowFingerprintPrompt(begin.cipher))
                is FingerprintBegin.Unavailable -> {
                    promptActive = false
                    val offered = fingerprint.isOffered()
                    _state.update { it.copy(fingerprintOffered = offered, fingerprintMessage = begin.message) }
                }
            }
        }
    }

    private fun onFingerprintSucceeded(cipher: Cipher) {
        viewModelScope.launch {
            val result = fingerprint.completeUnlock(cipher)
            promptActive = false
            when (result) {
                FingerprintUnlockResult.Verified -> _effects.send(PinUnlockEffect.Unlocked)
                is FingerprintUnlockResult.Failed -> {
                    val offered = fingerprint.isOffered()
                    _state.update { it.copy(fingerprintOffered = offered, fingerprintMessage = result.message) }
                }
            }
        }
    }

    /** Cancel and "Use PIN" are not failed attempts: only the message changes, never the counter. */
    private fun onFingerprintError(errorCode: Int) {
        promptActive = false
        _state.update { it.copy(fingerprintMessage = fingerprint.messageForPromptError(errorCode)) }
    }

    private fun onDeviceCredential() {
        viewModelScope.launch {
            if (authenticator.resetAfterDeviceCredential()) {
                applyStatus(LockoutStatus.Open)
                _effects.send(PinUnlockEffect.RecoveryAuthorized)
            } else {
                _state.update { it.copy(error = PinUnlockError.StorageFailure) }
            }
        }
    }

    private fun applyStatus(status: LockoutStatus) {
        ticker?.cancel()
        val ui = when (status) {
            LockoutStatus.Open -> LockoutUi.None
            is LockoutStatus.Waiting -> LockoutUi.Waiting(secondsCeil(status.remainingMs))
            LockoutStatus.CredentialRequired -> LockoutUi.CredentialRequired
            LockoutStatus.Unavailable -> LockoutUi.None
        }
        _state.update {
            it.copy(
                lockout = ui,
                error = if (status == LockoutStatus.Unavailable) PinUnlockError.StorageFailure else it.error,
            )
        }
        if (status is LockoutStatus.Waiting) {
            ticker = viewModelScope.launch {
                while (true) {
                    delay(TICK_MS)
                    val next = authenticator.status()
                    if (next is LockoutStatus.Waiting) {
                        _state.update { it.copy(lockout = LockoutUi.Waiting(secondsCeil(next.remainingMs))) }
                    } else {
                        applyStatus(next)
                        break
                    }
                }
            }
        }
    }

    private fun secondsCeil(ms: Long): Int = ((ms + 999) / 1000).toInt().coerceAtLeast(1)

    private companion object {
        const val TICK_MS = 1_000L
    }
}
