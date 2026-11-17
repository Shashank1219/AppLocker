package com.shashank.applocker.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place that touches the biometric-bound key. The key never leaves AndroidKeyStore;
 * this interface hands out initialised [Cipher]s, never key bytes. All functions may throw
 * platform crypto exceptions, which [CryptoErrors] maps to [CryptoFailure].
 */
interface BiometricKeyStore {
    /** @return true if the key exists. */
    fun hasKey(): Boolean

    /** Deletes any existing key, then creates a new one that needs authentication for every use. */
    fun generateKey()

    fun deleteKey()

    /** @throws java.security.InvalidKeyException (including KeyPermanentlyInvalidatedException) if unusable. */
    fun newEncryptCipher(): Cipher

    fun newDecryptCipher(iv: ByteArray): Cipher
}

/**
 * AES-256-GCM key in AndroidKeyStore with:
 * - `setUserAuthenticationRequired(true)` and per-use authentication (no validity window),
 *   so every cipher operation must be authorised by a fingerprint through a CryptoObject;
 * - `setInvalidatedByBiometricEnrollment(true)`, so adding a fingerprint kills the key;
 * - on API 30+, `AUTH_BIOMETRIC_STRONG` only, matching the Strong-only rule.
 * No `DEVICE_CREDENTIAL`: it cannot be combined with a CryptoObject on API 28 and 29.
 *
 * Not testable without a device.
 */
@Singleton
class AndroidBiometricKeyStore @Inject constructor() : BiometricKeyStore {

    override fun hasKey(): Boolean = loadKey() != null

    override fun generateKey() {
        deleteKey()
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1) // -1 = authenticate every use
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(builder.build()) }
            .generateKey()
    }

    override fun deleteKey() {
        val store = keyStore()
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }

    override fun newEncryptCipher(): Cipher = AesGcmCiphers.newEncryptCipher(requireKey())

    override fun newDecryptCipher(iv: ByteArray): Cipher = AesGcmCiphers.newDecryptCipher(requireKey(), iv)

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun loadKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as SecretKey?

    private fun requireKey(): SecretKey = loadKey() ?: throw MissingKeyException()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "applocker_biometric_key_v1"
    }
}

/** The key is not in the Keystore. */
class MissingKeyException : java.security.GeneralSecurityException("missing key")
