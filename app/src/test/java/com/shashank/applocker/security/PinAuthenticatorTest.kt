package com.shashank.applocker.security

import com.shashank.applocker.domain.model.AttemptResult
import com.shashank.applocker.domain.model.LockoutStatus
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Real hashing and verification, fake storage and clock. */
class PinAuthenticatorTest {
    private val rig = LockoutRig()
    private val store = FakeSecretStore()
    private val pins = SecurePinRepository(store, Pbkdf2PinHasher(), FixedIterations(), SecureRandom(), Dispatchers.Unconfined)
    private val auth = PinAuthenticator(pins, rig.manager)

    private fun pin(s: String) = s.toCharArray()
    private suspend fun setUp() { pins.setPin(pin("135790")) }

    @Test fun correctPinSucceeds() = runTest {
        setUp()
        assertEquals(AttemptResult.Success, auth.attempt(pin("135790")))
    }

    @Test fun incorrectPinIsGenericAndCounted() = runTest {
        setUp()
        assertEquals(AttemptResult.Incorrect(LockoutStatus.Open), auth.attempt(pin("135791")))
        assertEquals(1, rig.repo.state.failedAttempts)
        assertEquals(AttemptResult.Incorrect(LockoutStatus.Open), auth.attempt(pin("000001")))
        assertEquals(2, rig.repo.state.failedAttempts)
    }

    @Test fun thirdWrongPinStartsThirtySecondLockout() = runTest {
        setUp()
        auth.attempt(pin("111112"))
        auth.attempt(pin("111113"))
        assertEquals(AttemptResult.Incorrect(LockoutStatus.Waiting(30_000)), auth.attempt(pin("111114")))
    }

    @Test fun correctPinIsRefusedWhileLockedAndNotEvenChecked() = runTest {
        setUp()
        repeat(3) { auth.attempt(pin("111112")) }
        val guess = pin("135790")
        assertEquals(AttemptResult.Locked(LockoutStatus.Waiting(30_000)), auth.attempt(guess))
        assertFalse(String(guess) == "135790") // wiped even when refused
        assertEquals(3, rig.repo.state.failedAttempts) // a refused attempt is not counted
    }

    @Test fun correctPinWorksAfterExpiryAndResetsCounter() = runTest {
        setUp()
        repeat(3) { auth.attempt(pin("111112")) }
        rig.clock.advanceSeconds(31)
        assertEquals(AttemptResult.Success, auth.attempt(pin("135790")))
        assertEquals(0, rig.repo.state.failedAttempts)
    }

    @Test fun tenFailuresRequireDeviceCredentialAndRefuseTheCorrectPin() = runTest {
        setUp()
        repeat(10) { auth.attempt(pin("111112")); rig.clock.advanceSeconds(400) }
        assertEquals(LockoutStatus.CredentialRequired, auth.status())
        assertEquals(AttemptResult.Locked(LockoutStatus.CredentialRequired), auth.attempt(pin("135790")))
        assertEquals(true, auth.resetAfterDeviceCredential())
        assertEquals(AttemptResult.Success, auth.attempt(pin("135790")))
    }

    @Test fun lockoutSurvivesProcessDeath() = runTest {
        setUp()
        repeat(3) { auth.attempt(pin("111112")) }
        // New process: new manager and authenticator over the same persisted state.
        val reborn = PinAuthenticator(pins, LockoutManager(rig.repo, rig.clock, rig.credentials))
        assertEquals(LockoutStatus.Waiting(30_000), reborn.status())
        assertEquals(AttemptResult.Locked(LockoutStatus.Waiting(30_000)), reborn.attempt(pin("135790")))
    }

    @Test fun unreadableLockoutStateFailsClosed() = runTest {
        setUp()
        rig.repo.failing = true
        assertEquals(LockoutStatus.Unavailable, auth.status())
        assertEquals(AttemptResult.StorageError, auth.attempt(pin("135790")))
    }

    @Test fun failedCounterWriteOnWrongPinIsReportedNotIgnored() = runTest {
        setUp()
        val flaky = object : com.shashank.applocker.domain.repository.LockoutRepository {
            private var writes = 0
            override suspend fun get() = rig.repo.get()
            override suspend fun save(state: com.shashank.applocker.domain.model.LockoutState) {
                if (++writes >= 1) throw SecretStoreException()
            }
            override suspend fun reset() = rig.repo.reset()
        }
        val a = PinAuthenticator(pins, LockoutManager(flaky, rig.clock, rig.credentials))
        assertEquals(AttemptResult.StorageError, a.attempt(pin("111112")))
    }

    @Test fun unreadablePinStorageIsNotCountedAsAFailure() = runTest {
        setUp()
        store.failing = true
        assertEquals(AttemptResult.StorageError, auth.attempt(pin("135790")))
        assertEquals(0, rig.repo.state.failedAttempts)
    }
}
