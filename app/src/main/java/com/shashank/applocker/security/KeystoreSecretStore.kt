package com.shashank.applocker.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Values are encrypted with an AES-256-GCM key that lives in AndroidKeyStore (never exported),
 * then stored as base64(iv || ciphertext) in private SharedPreferences. The entry name is bound
 * as AAD so blobs cannot be swapped between entries.
 *
 * Failures (KeyStoreException, ProviderException, UnrecoverableKeyException, bad tag, I/O) all
 * surface as [SecretStoreException]. There is no plaintext fallback, and a missing key is
 * never silently regenerated on read.
 */
@Singleton
class KeystoreSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecretStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun put(key: String, value: ByteArray) = guard {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + cipher.doFinal(value)
        check(prefs.edit().putString(key, Base64.encodeToString(blob, Base64.NO_WRAP)).commit())
    }

    @Synchronized
    override fun get(key: String): ByteArray? = guard {
        val encoded = prefs.getString(key, null) ?: return@guard null
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        check(blob.size > IV_BYTES)
        val secretKey = existingKey() ?: error("missing key")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    @Synchronized
    override fun remove(key: String) = guard {
        check(prefs.edit().remove(key).commit())
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as SecretKey?

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        // Intentionally drops the cause: messages could reference key or entry details.
        throw SecretStoreException()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "applocker_secret_key_v1"
        const val PREFS_NAME = "secure_store"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
