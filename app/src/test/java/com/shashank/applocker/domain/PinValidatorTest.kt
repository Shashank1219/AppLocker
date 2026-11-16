package com.shashank.applocker.domain

import com.shashank.applocker.domain.model.PinRejection
import com.shashank.applocker.domain.model.PinValidationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PinValidatorTest {
    private fun reason(pin: String) = (PinValidator.validate(pin) as? PinValidationResult.Rejected)?.reason

    @Test fun acceptsNonTrivialPins() {
        listOf("135790", "482916", "908172", "121212", "123450", "246802").forEach {
            assertEquals(it, PinValidationResult.Valid, PinValidator.validate(it))
        }
    }

    @Test fun rejectsWrongLengthOrNonDigits() {
        listOf("", "1", "13579", "1357902", "12a456", "13 579", "１３５７９０").forEach {
            assertEquals(it, PinRejection.NOT_SIX_DIGITS, reason(it))
        }
    }

    @Test fun rejectsRepeatedDigits() {
        listOf("000000", "111111", "999999").forEach { assertEquals(PinRejection.REPEATED_DIGITS, reason(it)) }
    }

    @Test fun rejectsSequentialAscendingAndDescending() {
        listOf("123456", "012345", "234567", "345678", "456789", "654321", "987654", "543210")
            .forEach { assertEquals(it, PinRejection.SEQUENTIAL, reason(it)) }
    }

    @Test fun namedBlockedPinsAreRejected() {
        listOf("000000", "111111", "123456", "654321").forEach {
            assertEquals(it, true, PinValidator.validate(it) is PinValidationResult.Rejected)
        }
    }

    @Test fun nearSequencesAreAllowed() {
        // Not strictly sequential: wrap-around and gaps are fine.
        listOf("123457", "890123", "123321").forEach {
            assertEquals(it, PinValidationResult.Valid, PinValidator.validate(it))
        }
    }
}
