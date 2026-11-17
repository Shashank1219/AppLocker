package com.shashank.applocker.feature.biometric

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * The system fingerprint dialog, bound to a Keystore cipher through a CryptoObject. There is
 * no custom UI. Strong biometrics only, and no DEVICE_CREDENTIAL (not allowed together with a
 * CryptoObject on API 28-29), so the negative button is the "Use PIN" fallback.
 *
 * [onSuccess] receives the cipher from the authentication result, which the Keystore has
 * authorised. A success without a cipher is reported as an error, never as success.
 * Rejected fingerprints are retried by the system dialog and are not reported here.
 */
fun FragmentActivity.showFingerprintPrompt(
    cipher: Cipher,
    title: String,
    subtitle: String,
    negativeText: String,
    onSuccess: (Cipher) -> Unit,
    onError: (Int) -> Unit,
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val authorised = result.cryptoObject?.cipher
            if (authorised != null) onSuccess(authorised) else onError(BiometricPrompt.ERROR_VENDOR)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode)
    }
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setNegativeButtonText(negativeText)
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}
