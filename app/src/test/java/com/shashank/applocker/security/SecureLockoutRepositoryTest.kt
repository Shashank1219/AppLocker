package com.shashank.applocker.security

import com.shashank.applocker.domain.model.LockoutState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SecureLockoutRepositoryTest {
    private val store = FakeSecretStore()
    private val repo = SecureLockoutRepository(store, Dispatchers.Unconfined)

    @Test fun emptyMeansNoFailures() = runTest { assertEquals(LockoutState.NONE, repo.get()) }

    @Test fun roundTripsAllFields() = runTest {
        val s = LockoutState(7, 123_456_789L, 60_000L, 42, true)
        repo.save(s)
        assertEquals(s, repo.get())
    }

    @Test fun resetRemovesTheEntry() = runTest {
        repo.save(LockoutState(3, 1L, 30_000L, 1, false))
        repo.reset()
        assertNull(store.data[SecureLockoutRepository.KEY])
        assertEquals(LockoutState.NONE, repo.get())
    }

    @Test fun malformedDataThrowsInsteadOfResettingTheCounter() = runTest {
        store.data[SecureLockoutRepository.KEY] = ByteArray(5)
        assertFailsClosed()
        store.data[SecureLockoutRepository.KEY] = ByteArray(SecureLockoutRepository.encode(LockoutState.NONE).size)
        assertFailsClosed() // version byte 0
    }

    @Test fun storageFailureThrows() = runTest {
        store.failing = true
        assertFailsClosed()
    }

    @Test fun writesGoThroughTheEncryptedStoreOnly() = runTest {
        repo.save(LockoutState(3, 1L, 30_000L, 1, false))
        assertEquals(setOf(SecureLockoutRepository.KEY), store.data.keys)
        assertFalse(store.data.getValue(SecureLockoutRepository.KEY).isEmpty())
    }

    private suspend fun assertFailsClosed() {
        try {
            repo.get()
            throw AssertionError("expected SecretStoreException")
        } catch (e: SecretStoreException) {
            Unit
        }
    }
}
