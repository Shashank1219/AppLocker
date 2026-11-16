package com.shashank.applocker.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM cipher construction shared by the Keystore implementation and test fakes. */
object AesGcmCiphers {
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BITS = 128
    const val TAG_BYTES = TAG_BITS / 8

    /** The provider chooses a fresh random IV (AndroidKeyStore requires this). Read it with [Cipher.getIV]. */
    fun newEncryptCipher(key: SecretKey): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

    fun newDecryptCipher(key: SecretKey, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
}
