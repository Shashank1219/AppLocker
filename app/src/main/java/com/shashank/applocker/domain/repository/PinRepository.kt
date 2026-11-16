package com.shashank.applocker.domain.repository

import com.shashank.applocker.domain.model.PinSetResult
import com.shashank.applocker.domain.model.PinStatus
import com.shashank.applocker.domain.model.PinVerifyResult

/**
 * Both functions take ownership of the [CharArray] and overwrite it before returning.
 */
interface PinRepository {
    suspend fun status(): PinStatus
    suspend fun setPin(pin: CharArray): PinSetResult
    suspend fun verifyPin(pin: CharArray): PinVerifyResult
}
