package com.shashank.applocker.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricAvailability {
    AVAILABLE,

    /** No fingerprint sensor (feature missing or reported absent). PIN-only mode. */
    NO_HARDWARE,

    /** A sensor exists but no Strong biometric is enrolled. */
    NOT_ENROLLED,

    /** Sensor busy, locked out or not ready. May recover on its own. */
    TEMPORARILY_UNAVAILABLE,

    UPDATE_REQUIRED,

    /** Anything else, including an unknown status. Never treated as available. */
    UNSUPPORTED,
}

/** Raw platform answers, behind an interface so the decision logic can be tested without a device. */
interface BiometricCapabilitySource {
    /** `PackageManager.FEATURE_FINGERPRINT` is present. */
    fun hasFingerprintFeature(): Boolean

    /** `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`. */
    fun canAuthenticateStrong(): Int
}

@Singleton
class AndroidBiometricCapabilitySource @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricCapabilitySource {
    override fun hasFingerprintFeature(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

    override fun canAuthenticateStrong(): Int =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)
}

/**
 * Availability, checked in the order CLAUDE.md requires:
 * 1. the fingerprint feature is present, then
 * 2. `canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS`.
 * There is no downgrade to weak biometrics: a weak match could not be bound to the Keystore key.
 */
@Singleton
class BiometricAvailabilityChecker @Inject constructor(
    private val source: BiometricCapabilitySource,
) {
    fun check(): BiometricAvailability {
        if (!source.hasFingerprintFeature()) return BiometricAvailability.NO_HARDWARE
        return when (source.canAuthenticateStrong()) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.TEMPORARILY_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.UPDATE_REQUIRED
            else -> BiometricAvailability.UNSUPPORTED
        }
    }
}
