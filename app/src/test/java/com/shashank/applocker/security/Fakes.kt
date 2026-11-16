package com.shashank.applocker.security

import com.shashank.applocker.domain.model.PinSetResult
import com.shashank.applocker.domain.model.PinStatus
import com.shashank.applocker.domain.model.PinVerifyResult
import com.shashank.applocker.domain.repository.PinRepository

class FakeSecretStore : SecretStore {
    val data = mutableMapOf<String, ByteArray>()
    var failing = false

    override fun put(key: String, value: ByteArray) {
        if (failing) throw SecretStoreException()
        data[key] = value.copyOf()
    }

    override fun get(key: String): ByteArray? {
        if (failing) throw SecretStoreException()
        return data[key]?.copyOf()
    }

    override fun remove(key: String) {
        data.remove(key)
    }
}

/** Real PBKDF2 but a tiny work factor so tests stay fast. */
class FixedIterations(private val n: Int = 1_000) : IterationPolicy {
    override fun iterationsForNewPin() = n
}

class FakePinRepository : PinRepository {
    var status = PinStatus.NOT_SET
    var setResult: PinSetResult = PinSetResult.Success
    var verifyResult: PinVerifyResult = PinVerifyResult.Success
    val savedPins = mutableListOf<String>()
    val verifiedPins = mutableListOf<String>()

    override suspend fun status() = status

    override suspend fun setPin(pin: CharArray): PinSetResult {
        savedPins += String(pin)
        return setResult
    }

    override suspend fun verifyPin(pin: CharArray): PinVerifyResult {
        verifiedPins += String(pin)
        return verifyResult
    }
}
