package com.shashank.applocker.security

import com.shashank.applocker.di.DefaultDispatcher
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Design:
 * - The AES key lives only in AndroidKeyStore ([BiometricKeyStore]) and needs a fingerprint for
 *   every operation, including the encryption at enable time.
 * - A random 256-bit token is created once, encrypted with that key, and stored with its IV
 *   and a SHA-256 digest in [SecretStore]. The plaintext token exists only in memory, briefly,
 *   and is wiped.
 * - Unlock decrypts the stored ciphertext with a cipher the user authorised, hashes the result
 *   and compares it with the stored digest in constant time. Only a match is `Verified`.
 * - Failures never fall back: an invalidated key is deleted together with the token.
 *
 * Nothing here logs. Exceptions are reduced to [CryptoFailure] by [CryptoErrors].
 */
@Singleton
class DefaultBiometricKeyManager @Inject constructor(
    private val keyStore: BiometricKeyStore,
    private val secretStore: SecretStore,
    private val tokens: TokenGenerator,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : BiometricKeyManager {
    private val mutex = Mutex()

    override suspend fun isEnrolled(): Boolean = guarded {
        try {
            secretStore.get(KEY_RECORD)?.also { it.fill(0) } != null && keyStore.hasKey()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun prepareEnrollment(): CipherPreparation = guarded<CipherPreparation> {
        try {
            removeAll()
            keyStore.generateKey()
            CipherPreparation.Ready(keyStore.newEncryptCipher())
        } catch (e: Exception) {
            removeAllQuietly()
            CipherPreparation.Failed(CryptoErrors.map(e, duringKeyGeneration = true))
        }
    }

    override suspend fun completeEnrollment(cipher: Cipher): EnrollmentResult = guarded<EnrollmentResult> {
        val token = tokens.newToken()
        try {
            val ciphertext = cipher.doFinal(token)
            val record = BiometricTokenRecord(cipher.iv, ciphertext, sha256(token))
            val encoded = record.encode()
            try {
                secretStore.put(KEY_RECORD, encoded)
            } finally {
                encoded.fill(0)
                record.wipe()
            }
            EnrollmentResult.Success
        } catch (e: Exception) {
            val failure = CryptoErrors.map(e)
            // Unless the user simply has not authenticated yet, leave nothing half-enabled.
            if (failure != CryptoFailure.AUTHENTICATION_REQUIRED) removeAllQuietly()
            EnrollmentResult.Failed(failure)
        } finally {
            token.fill(0)
        }
    }

    override suspend fun prepareUnlock(): CipherPreparation = guarded<CipherPreparation> {
        try {
            val bytes = secretStore.get(KEY_RECORD) ?: return@guarded CipherPreparation.Failed(CryptoFailure.NOT_INITIALIZED)
            val record = BiometricTokenRecord.decode(bytes).also { bytes.fill(0) }
                ?: return@guarded CipherPreparation.Failed(CryptoFailure.DECRYPTION_FAILED)
            try {
                if (!keyStore.hasKey()) {
                    // Token without key: the system dropped the key (e.g. fingerprint change).
                    removeAllQuietly()
                    return@guarded CipherPreparation.Failed(CryptoFailure.KEY_INVALIDATED)
                }
                CipherPreparation.Ready(keyStore.newDecryptCipher(record.iv))
            } finally {
                record.wipe()
            }
        } catch (e: Exception) {
            val failure = CryptoErrors.map(e)
            if (failure == CryptoFailure.KEY_INVALIDATED) removeAllQuietly()
            CipherPreparation.Failed(failure)
        }
    }

    override suspend fun completeUnlock(cipher: Cipher): UnlockTokenResult = guarded<UnlockTokenResult> {
        var plaintext: ByteArray? = null
        var digest: ByteArray? = null
        try {
            val bytes = secretStore.get(KEY_RECORD) ?: return@guarded UnlockTokenResult.Failed(CryptoFailure.NOT_INITIALIZED)
            val record = BiometricTokenRecord.decode(bytes).also { bytes.fill(0) }
                ?: return@guarded UnlockTokenResult.Failed(CryptoFailure.DECRYPTION_FAILED)
            try {
                plaintext = cipher.doFinal(record.ciphertext)
                digest = sha256(plaintext)
                if (MessageDigest.isEqual(digest, record.tokenDigest)) {
                    UnlockTokenResult.Verified
                } else {
                    UnlockTokenResult.Failed(CryptoFailure.DECRYPTION_FAILED)
                }
            } finally {
                record.wipe()
            }
        } catch (e: Exception) {
            val failure = CryptoErrors.map(e)
            if (failure == CryptoFailure.KEY_INVALIDATED) removeAllQuietly()
            UnlockTokenResult.Failed(failure)
        } finally {
            plaintext?.fill(0)
            digest?.fill(0)
        }
    }

    override suspend fun disable() = guarded { removeAllQuietly() }

    private fun removeAll() {
        keyStore.deleteKey()
        secretStore.remove(KEY_RECORD)
    }

    /** Tries both deletions even if the first fails. */
    private fun removeAllQuietly() {
        try { keyStore.deleteKey() } catch (e: Exception) { Unit }
        try { secretStore.remove(KEY_RECORD) } catch (e: Exception) { Unit }
    }

    private suspend fun <T> guarded(block: () -> T): T =
        mutex.withLock { withContext(dispatcher) { block() } }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private companion object {
        const val KEY_RECORD = "biometric_token_record"
    }
}
