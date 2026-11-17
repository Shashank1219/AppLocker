package com.shashank.applocker.security

import androidx.biometric.BiometricManager
import com.shashank.applocker.domain.model.AppSettings
import com.shashank.applocker.domain.model.RelockDelay
import com.shashank.applocker.domain.repository.SettingsRepository
import javax.crypto.Cipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings = state
    override suspend fun setOnboardingCompleted(completed: Boolean) = state.update { it.copy(onboardingCompleted = completed) }
    override suspend fun setFingerprintEnabled(enabled: Boolean) = state.update { it.copy(fingerprintEnabled = enabled) }
    override suspend fun setRelockDelay(delay: RelockDelay) = state.update { it.copy(relockDelay = delay) }
    override suspend fun setAccessibilityServiceEnabled(enabled: Boolean) = state.update { it.copy(accessibilityServiceEnabled = enabled) }
}

class FakeCapabilities(
    var feature: Boolean = true,
    var code: Int = BiometricManager.BIOMETRIC_SUCCESS,
) : BiometricCapabilitySource {
    var canAuthenticateCalls = 0
    override fun hasFingerprintFeature() = feature
    override fun canAuthenticateStrong(): Int { canAuthenticateCalls++; return code }
}

/** Programmable stand-in for the Keystore-backed manager. */
class FakeBiometricKeyManager : BiometricKeyManager {
    val cipher: Cipher = Cipher.getInstance(AesGcmCiphers.TRANSFORMATION)
    var enrolled = true
    var unlockPrep: CipherPreparation = CipherPreparation.Ready(cipher)
    var enrollPrep: CipherPreparation = CipherPreparation.Ready(cipher)
    var unlockResult: UnlockTokenResult = UnlockTokenResult.Verified
    var enrollResult: EnrollmentResult = EnrollmentResult.Success
    var disableCalls = 0

    override suspend fun isEnrolled() = enrolled
    override suspend fun prepareEnrollment() = enrollPrep
    override suspend fun completeEnrollment(cipher: Cipher) = enrollResult
    override suspend fun prepareUnlock() = unlockPrep
    override suspend fun completeUnlock(cipher: Cipher) = unlockResult
    override suspend fun disable() { disableCalls++; enrolled = false }
}

/** Wires [FingerprintAuth] the way tests need it. */
class FingerprintRig(fingerprintEnabled: Boolean = true) {
    val settings = FakeSettingsRepository(AppSettings(fingerprintEnabled = fingerprintEnabled))
    val capabilities = FakeCapabilities()
    val keys = FakeBiometricKeyManager()
    val auth = FingerprintAuth(settings, BiometricAvailabilityChecker(capabilities), keys)
    val enabled get() = settings.state.value.fingerprintEnabled
}
