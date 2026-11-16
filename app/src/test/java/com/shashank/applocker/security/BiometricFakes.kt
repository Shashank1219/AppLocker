package com.shashank.applocker.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.InvalidAlgorithmParameterException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Software AES key standing in for AndroidKeyStore. It cannot enforce user authentication,
 * so tests of "authorised vs unauthorised" use the failure switches below instead.
 */
class FakeBiometricKeyStore : BiometricKeyStore {
    private var key: SecretKey? = null

    /** Simulates a fingerprint being added: the key is still listed but unusable. */
    var invalidated = false
    var noBiometricEnrolled = false
    var generateFailure: Exception? = null
    var encryptInitFailure: Exception? = null
    var generatedKeys = 0

    /** Simulates the system dropping the key entirely. */
    fun dropKey() { key = null }

    override fun hasKey() = key != null

    override fun generateKey() {
        generateFailure?.let { throw it }
        if (noBiometricEnrolled) throw InvalidAlgorithmParameterException()
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        invalidated = false
        generatedKeys++
    }

    override fun deleteKey() { key = null }

    override fun newEncryptCipher(): Cipher {
        encryptInitFailure?.let { throw it }
        return AesGcmCiphers.newEncryptCipher(usableKey())
    }

    override fun newDecryptCipher(iv: ByteArray): Cipher = AesGcmCiphers.newDecryptCipher(usableKey(), iv)

    private fun usableKey(): SecretKey {
        if (invalidated) throw KeyPermanentlyInvalidatedException()
        return key ?: throw MissingKeyException()
    }
}

class FixedTokenGenerator(private val token: ByteArray = ByteArray(32) { (it + 1).toByte() }) : TokenGenerator {
    var lastIssued: ByteArray? = null
    override fun newToken(): ByteArray = token.copyOf().also { lastIssued = it }
}

fun providerFailure(): Exception = ProviderException("boom")
