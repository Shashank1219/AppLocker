package com.shashank.applocker.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricAvailabilityTest {
    private val source = FakeCapabilities()
    private val checker = BiometricAvailabilityChecker(source)

    private fun check(code: Int): BiometricAvailability {
        source.code = code
        return checker.check()
    }

    @Test fun availableWhenFeaturePresentAndStrongSucceeds() =
        assertEquals(BiometricAvailability.AVAILABLE, check(BiometricManager.BIOMETRIC_SUCCESS))

    @Test fun missingFeatureMeansNoHardwareAndTheManagerIsNeverAsked() {
        source.feature = false
        assertEquals(BiometricAvailability.NO_HARDWARE, checker.check())
        assertEquals(0, source.canAuthenticateCalls)
    }

    @Test fun noEnrolledFingerprint() =
        assertEquals(BiometricAvailability.NOT_ENROLLED, check(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))

    @Test fun reportedNoHardware() =
        assertEquals(BiometricAvailability.NO_HARDWARE, check(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))

    @Test fun temporarilyUnavailable() =
        assertEquals(BiometricAvailability.TEMPORARILY_UNAVAILABLE, check(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))

    @Test fun securityUpdateRequired() =
        assertEquals(BiometricAvailability.UPDATE_REQUIRED, check(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED))

    @Test fun unknownAndUnsupportedAreNeverAvailable() {
        assertEquals(BiometricAvailability.UNSUPPORTED, check(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED))
        assertEquals(BiometricAvailability.UNSUPPORTED, check(BiometricManager.BIOMETRIC_STATUS_UNKNOWN))
        assertEquals(BiometricAvailability.UNSUPPORTED, check(12345))
    }

    // --- Fallback decisions for prompt errors ---

    @Test fun cancelAndUsePinFallBackSilently() {
        listOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED)
            .forEach { assertEquals(FingerprintMessage.NONE, BiometricErrorPolicy.messageFor(it)) }
    }

    @Test fun lockoutFallsBackWithLockedMessage() {
        listOf(BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
            .forEach { assertEquals(FingerprintMessage.LOCKED, BiometricErrorPolicy.messageFor(it)) }
    }

    @Test fun hardwareProblemsFallBackAsUnavailable() {
        listOf(BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_HW_NOT_PRESENT)
            .forEach { assertEquals(FingerprintMessage.UNAVAILABLE, BiometricErrorPolicy.messageFor(it)) }
    }

    @Test fun noBiometricsFallsBackAsNotEnrolled() =
        assertEquals(FingerprintMessage.NOT_ENROLLED, BiometricErrorPolicy.messageFor(BiometricPrompt.ERROR_NO_BIOMETRICS))

    @Test fun anyOtherErrorFallsBackWithGenericMessage() {
        listOf(BiometricPrompt.ERROR_TIMEOUT, BiometricPrompt.ERROR_VENDOR, BiometricPrompt.ERROR_UNABLE_TO_PROCESS, -1, 9999)
            .forEach { assertEquals(FingerprintMessage.FAILED, BiometricErrorPolicy.messageFor(it)) }
    }

    @Test fun availabilityMapsToMessages() {
        assertEquals(FingerprintMessage.NONE, BiometricErrorPolicy.messageFor(BiometricAvailability.AVAILABLE))
        assertEquals(FingerprintMessage.NOT_ENROLLED, BiometricErrorPolicy.messageFor(BiometricAvailability.NOT_ENROLLED))
        assertEquals(FingerprintMessage.UNAVAILABLE, BiometricErrorPolicy.messageFor(BiometricAvailability.NO_HARDWARE))
        assertEquals(FingerprintMessage.UNAVAILABLE, BiometricErrorPolicy.messageFor(BiometricAvailability.TEMPORARILY_UNAVAILABLE))
    }
}
