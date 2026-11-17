package com.shashank.applocker.security

import com.shashank.applocker.domain.repository.SettingsRepository
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

sealed interface FingerprintBegin {
    /** Wrap [cipher] in a CryptoObject, show the system prompt, then pass the authorised cipher back. */
    data class Ready(val cipher: Cipher) : FingerprintBegin

    /** Fingerprint cannot be used now. Fall back to the PIN and show [message] unless it is NONE. */
    data class Unavailable(val message: FingerprintMessage) : FingerprintBegin
}

sealed interface FingerprintUnlockResult {
    /** The Keystore-decrypted token matched. The only result that may unlock. */
    data object Verified : FingerprintUnlockResult
    data class Failed(val message: FingerprintMessage) : FingerprintUnlockResult
}

sealed interface FingerprintEnableResult {
    data object Enabled : FingerprintEnableResult
    data class Failed(val message: FingerprintMessage) : FingerprintEnableResult
}

/**
 * Glue between availability, the user's setting and [BiometricKeyManager]. It contains no UI.
 * A biometric success is meaningless until [completeUnlock] returns Verified.
 */
@Singleton
class FingerprintAuth @Inject constructor(
    private val settings: SettingsRepository,
    private val availability: BiometricAvailabilityChecker,
    private val keys: BiometricKeyManager,
) {
    fun availability(): BiometricAvailability = availability.check()

    /** True if the unlock screen should offer fingerprint. Never throws. */
    suspend fun isOffered(): Boolean =
        settings.settings.first().fingerprintEnabled &&
            availability.check() == BiometricAvailability.AVAILABLE &&
            keys.isEnrolled()

    suspend fun beginUnlock(): FingerprintBegin {
        if (!settings.settings.first().fingerprintEnabled) return FingerprintBegin.Unavailable(FingerprintMessage.NONE)
        val state = availability.check()
        if (state != BiometricAvailability.AVAILABLE) {
            return FingerprintBegin.Unavailable(BiometricErrorPolicy.messageFor(state))
        }
        return when (val prep = keys.prepareUnlock()) {
            is CipherPreparation.Ready -> FingerprintBegin.Ready(prep.cipher)
            is CipherPreparation.Failed -> {
                val message = failureMessage(prep.failure)
                if (prep.failure.disablesFingerprint()) settings.setFingerprintEnabled(false)
                FingerprintBegin.Unavailable(message)
            }
        }
    }

    suspend fun completeUnlock(cipher: Cipher): FingerprintUnlockResult =
        when (val result = keys.completeUnlock(cipher)) {
            UnlockTokenResult.Verified -> FingerprintUnlockResult.Verified
            is UnlockTokenResult.Failed -> {
                if (result.failure.disablesFingerprint()) settings.setFingerprintEnabled(false)
                FingerprintUnlockResult.Failed(failureMessage(result.failure))
            }
        }

    /** Message for a prompt error; no state changes. */
    fun messageForPromptError(errorCode: Int): FingerprintMessage = BiometricErrorPolicy.messageFor(errorCode)

    /** Call only after the PIN has been verified. */
    suspend fun beginEnable(): FingerprintBegin {
        val state = availability.check()
        if (state != BiometricAvailability.AVAILABLE) {
            return FingerprintBegin.Unavailable(BiometricErrorPolicy.messageFor(state))
        }
        return when (val prep = keys.prepareEnrollment()) {
            is CipherPreparation.Ready -> FingerprintBegin.Ready(prep.cipher)
            is CipherPreparation.Failed -> FingerprintBegin.Unavailable(
                if (prep.failure == CryptoFailure.NO_BIOMETRIC_ENROLLED) FingerprintMessage.NOT_ENROLLED
                else FingerprintMessage.FAILED,
            )
        }
    }

    suspend fun completeEnable(cipher: Cipher): FingerprintEnableResult =
        when (val result = keys.completeEnrollment(cipher)) {
            EnrollmentResult.Success -> {
                settings.setFingerprintEnabled(true)
                FingerprintEnableResult.Enabled
            }
            is EnrollmentResult.Failed -> {
                settings.setFingerprintEnabled(false)
                FingerprintEnableResult.Failed(failureMessage(result.failure))
            }
        }

    /** The enable prompt was cancelled or failed: leave no key or token behind. */
    suspend fun cancelEnable() {
        keys.disable()
        settings.setFingerprintEnabled(false)
    }

    suspend fun disable() {
        keys.disable()
        settings.setFingerprintEnabled(false)
    }

    /** Settings says on but nothing is enrolled (e.g. data cleared): correct the setting. */
    suspend fun reconcile() {
        if (settings.settings.first().fingerprintEnabled && !keys.isEnrolled()) settings.setFingerprintEnabled(false)
    }

    private fun CryptoFailure.disablesFingerprint() =
        this == CryptoFailure.KEY_INVALIDATED || this == CryptoFailure.NOT_INITIALIZED

    private fun failureMessage(failure: CryptoFailure): FingerprintMessage = when (failure) {
        CryptoFailure.KEY_INVALIDATED -> FingerprintMessage.KEY_CHANGED
        CryptoFailure.NOT_INITIALIZED -> FingerprintMessage.NONE
        else -> FingerprintMessage.FAILED
    }
}
