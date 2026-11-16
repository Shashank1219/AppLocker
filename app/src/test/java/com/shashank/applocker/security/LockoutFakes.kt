package com.shashank.applocker.security

import com.shashank.applocker.domain.model.LockoutState
import com.shashank.applocker.domain.repository.LockoutRepository

class FakeLockoutRepository : LockoutRepository {
    var state = LockoutState.NONE
    var failing = false

    override suspend fun get(): LockoutState {
        if (failing) throw SecretStoreException()
        return state
    }

    override suspend fun save(state: LockoutState) {
        if (failing) throw SecretStoreException()
        this.state = state
    }

    override suspend fun reset() {
        if (failing) throw SecretStoreException()
        state = LockoutState.NONE
    }
}

class FakeClock(var now: Long = 1_000_000L, var boot: Int = 5) : ElapsedClock {
    override fun elapsedRealtimeMs() = now
    override fun bootCount() = boot
    fun advanceSeconds(s: Long) { now += s * 1000 }
}

class FakeCredentials(var available: Boolean = true) : DeviceCredentialAvailability {
    override fun isAvailable() = available
}

/** Everything the lockout tests need, wired the way production wires it. */
class LockoutRig(credentialAvailable: Boolean = true) {
    val repo = FakeLockoutRepository()
    val clock = FakeClock()
    val credentials = FakeCredentials(credentialAvailable)
    val manager get() = LockoutManager(repo, clock, credentials)
}
