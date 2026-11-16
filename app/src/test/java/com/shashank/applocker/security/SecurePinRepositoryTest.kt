package com.shashank.applocker.security

import com.shashank.applocker.domain.model.PinSetResult
import com.shashank.applocker.domain.model.PinStatus
import com.shashank.applocker.domain.model.PinVerifyResult
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SecurePinRepositoryTest {
    private val store = FakeSecretStore()
    private val repo = SecurePinRepository(
        store, Pbkdf2PinHasher(), FixedIterations(), SecureRandom(), Dispatchers.Unconfined,
    )
    private fun pin(s: String) = s.toCharArray()

    @Test fun statusNotSetThenSet() = runTest {
        assertEquals(PinStatus.NOT_SET, repo.status())
        repo.setPin(pin("135790"))
        assertEquals(PinStatus.SET, repo.status())
    }

    @Test fun correctPinVerifies() = runTest {
        assertEquals(PinSetResult.Success, repo.setPin(pin("135790")))
        assertEquals(PinVerifyResult.Success, repo.verifyPin(pin("135790")))
    }

    @Test fun incorrectPinIsRejectedWithGenericResult() = runTest {
        repo.setPin(pin("135790"))
        listOf("135791", "000000", "097531", "13579").forEach {
            assertEquals(it, PinVerifyResult.Incorrect, repo.verifyPin(pin(it)))
        }
        // Still works afterwards: no lockout in this stage.
        assertEquals(PinVerifyResult.Success, repo.verifyPin(pin("135790")))
    }

    @Test fun verifyBeforeSetupIsNotSet() = runTest {
        assertEquals(PinVerifyResult.NotSet, repo.verifyPin(pin("135790")))
    }

    @Test fun storedBytesNeverContainThePin() = runTest {
        repo.setPin(pin("135790"))
        val bytes = store.data.getValue("pin_record")
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("135790"))
        assertEquals(1, store.data.size)
    }

    @Test fun eachSetPinUsesFreshRandomSalt() = runTest {
        repo.setPin(pin("135790"))
        val first = PinRecord.decode(store.data.getValue("pin_record"))!!
        repo.setPin(pin("135790"))
        val second = PinRecord.decode(store.data.getValue("pin_record"))!!
        assertEquals(16, first.salt.size)
        assertFalse(first.salt.contentEquals(second.salt))
        assertFalse(first.hash.contentEquals(second.hash))
    }

    @Test fun setPinWipesCallerArray() = runTest {
        val p = pin("135790")
        repo.setPin(p)
        assertFalse(String(p) == "135790")
    }

    @Test fun verifyWipesCallerArray() = runTest {
        repo.setPin(pin("135790"))
        val p = pin("135790")
        repo.verifyPin(p)
        assertFalse(String(p) == "135790")
    }

    @Test fun storageFailureOnSetIsReported() = runTest {
        store.failing = true
        assertEquals(PinSetResult.StorageError, repo.setPin(pin("135790")))
    }

    @Test fun storageFailureOnVerifyIsNotTreatedAsIncorrectOrNotSet() = runTest {
        repo.setPin(pin("135790"))
        store.failing = true
        assertEquals(PinVerifyResult.StorageError, repo.verifyPin(pin("135790")))
        assertEquals(PinStatus.UNAVAILABLE, repo.status())
    }

    @Test fun corruptRecordIsStorageErrorNotNotSet() = runTest {
        store.data["pin_record"] = ByteArray(10)
        assertEquals(PinVerifyResult.StorageError, repo.verifyPin(pin("135790")))
        assertEquals(PinStatus.UNAVAILABLE, repo.status())
    }

    @Test fun recordRoundTripsAndRejectsBadVersion() {
        val r = PinRecord(5000, ByteArray(16) { 1 }, ByteArray(32) { 2 })
        val d = PinRecord.decode(r.encode())!!
        assertEquals(5000, d.iterations)
        assertArrayEquals(r.salt, d.salt)
        assertArrayEquals(r.hash, d.hash)
        assertNull(PinRecord.decode(ByteArray(53))) // version 0
    }
}
