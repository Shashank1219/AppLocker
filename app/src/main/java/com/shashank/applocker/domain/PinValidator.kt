package com.shashank.applocker.domain

import com.shashank.applocker.domain.model.PIN_LENGTH
import com.shashank.applocker.domain.model.PinRejection
import com.shashank.applocker.domain.model.PinValidationResult

/**
 * PIN rules: exactly 6 digits, not one repeated digit, not a strictly ascending or descending run.
 * This also rejects 000000, 111111, 123456 and 654321.
 */
object PinValidator {
    fun validate(pin: CharSequence): PinValidationResult {
        if (pin.length != PIN_LENGTH || !pin.all { it in '0'..'9' }) {
            return PinValidationResult.Rejected(PinRejection.NOT_SIX_DIGITS)
        }
        val digits = IntArray(pin.length) { pin[it] - '0' }
        if (digits.all { it == digits[0] }) {
            return PinValidationResult.Rejected(PinRejection.REPEATED_DIGITS)
        }
        val steps = (1 until digits.size).map { digits[it] - digits[it - 1] }
        if (steps.all { it == 1 } || steps.all { it == -1 }) {
            return PinValidationResult.Rejected(PinRejection.SEQUENTIAL)
        }
        return PinValidationResult.Valid
    }
}
