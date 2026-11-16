package com.shashank.applocker.security

import com.shashank.applocker.di.DefaultDispatcher
import com.shashank.applocker.domain.model.PinSetResult
import com.shashank.applocker.domain.model.PinStatus
import com.shashank.applocker.domain.model.PinVerifyResult
import com.shashank.applocker.domain.repository.PinRepository
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Stores only salt + PBKDF2 verifier (see [PinRecord]) through [SecretStore], which encrypts it
 * with an AndroidKeyStore key, so the stored bytes cannot be brute-forced off the device.
 * Verification compares in constant time and returns the same [PinVerifyResult.Incorrect]
 * for every wrong PIN. Nothing here logs.
 */
@Singleton
class SecurePinRepository @Inject constructor(
    private val secretStore: SecretStore,
    private val hasher: PinHasher,
    private val iterationPolicy: IterationPolicy,
    private val random: SecureRandom,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : PinRepository {

    override suspend fun status(): PinStatus = withContext(dispatcher) {
        val bytes = try {
            secretStore.get(KEY_PIN_RECORD)
        } catch (e: SecretStoreException) {
            return@withContext PinStatus.UNAVAILABLE
        } ?: return@withContext PinStatus.NOT_SET
        val record = PinRecord.decode(bytes)
        bytes.fill(0)
        if (record == null) PinStatus.UNAVAILABLE else PinStatus.SET.also { record.wipe() }
    }

    override suspend fun setPin(pin: CharArray): PinSetResult = withContext(dispatcher) {
        try {
            val salt = ByteArray(PinRecord.SALT_BYTES).also(random::nextBytes)
            val iterations = iterationPolicy.iterationsForNewPin()
            val hash = hasher.hash(pin, salt, iterations)
            val record = PinRecord(iterations, salt, hash)
            val encoded = record.encode()
            try {
                secretStore.put(KEY_PIN_RECORD, encoded)
            } finally {
                encoded.fill(0)
                record.wipe()
            }
            PinSetResult.Success
        } catch (e: SecretStoreException) {
            PinSetResult.StorageError
        } finally {
            pin.fill('0')
        }
    }

    override suspend fun verifyPin(pin: CharArray): PinVerifyResult = withContext(dispatcher) {
        try {
            val bytes = try {
                secretStore.get(KEY_PIN_RECORD)
            } catch (e: SecretStoreException) {
                return@withContext PinVerifyResult.StorageError
            } ?: return@withContext PinVerifyResult.NotSet
            val record = PinRecord.decode(bytes)
            bytes.fill(0)
            if (record == null) return@withContext PinVerifyResult.StorageError
            val candidate = hasher.hash(pin, record.salt, record.iterations)
            val matches = MessageDigest.isEqual(candidate, record.hash)
            candidate.fill(0)
            record.wipe()
            if (matches) PinVerifyResult.Success else PinVerifyResult.Incorrect
        } finally {
            pin.fill('0')
        }
    }

    private companion object {
        const val KEY_PIN_RECORD = "pin_record"
    }
}
