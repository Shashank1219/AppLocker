package com.shashank.applocker.security

import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

interface TokenGenerator {
    /** A new random unlock token. The caller owns the array and should wipe it after use. */
    fun newToken(): ByteArray
}

@Singleton
class SecureRandomTokenGenerator @Inject constructor(
    private val random: SecureRandom,
) : TokenGenerator {
    override fun newToken(): ByteArray = ByteArray(TOKEN_BYTES).also(random::nextBytes)

    companion object {
        const val TOKEN_BYTES = 32
    }
}
