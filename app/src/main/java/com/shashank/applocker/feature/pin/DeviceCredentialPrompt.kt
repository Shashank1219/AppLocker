package com.shashank.applocker.feature.pin

import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks the system to confirm the device screen lock (PIN, pattern or password). This is the
 * app's only recovery path. No CryptoObject and no biometric is involved. Cancel and errors
 * are ignored on purpose; the user can simply press the button again.
 */
fun FragmentActivity.showDeviceCredentialPrompt(title: String, subtitle: String, onSuccess: () -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    }
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}
