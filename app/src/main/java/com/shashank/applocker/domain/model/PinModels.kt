package com.shashank.applocker.domain.model

const val PIN_LENGTH = 6

enum class PinRejection { NOT_SIX_DIGITS, REPEATED_DIGITS, SEQUENTIAL }

sealed interface PinValidationResult {
    data object Valid : PinValidationResult
    data class Rejected(val reason: PinRejection) : PinValidationResult
}

enum class PinStatus {
    NOT_SET,
    SET,

    /** Secure storage failed or held unreadable data. Never treated as "not set". */
    UNAVAILABLE,
}

sealed interface PinSetResult {
    data object Success : PinSetResult
    data object StorageError : PinSetResult
}

sealed interface PinVerifyResult {
    data object Success : PinVerifyResult
    data object Incorrect : PinVerifyResult
    data object NotSet : PinVerifyResult
    data object StorageError : PinVerifyResult
}
