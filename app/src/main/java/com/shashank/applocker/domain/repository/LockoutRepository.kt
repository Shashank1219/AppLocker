package com.shashank.applocker.domain.repository

import com.shashank.applocker.domain.model.LockoutState

/**
 * Persisted lockout state. Implementations must keep it in encrypted storage and must throw
 * [com.shashank.applocker.security.SecretStoreException] when it cannot be read or written,
 * so callers can fail closed.
 */
interface LockoutRepository {
    suspend fun get(): LockoutState
    suspend fun save(state: LockoutState)
    suspend fun reset()
}
