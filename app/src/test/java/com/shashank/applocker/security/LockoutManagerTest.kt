package com.shashank.applocker.security

import com.shashank.applocker.domain.model.LockoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutManagerTest {
    private val rig = LockoutRig()

    private suspend fun fail(times: Int): LockoutStatus {
        var last: LockoutStatus = LockoutStatus.Open
        repeat(times) { last = rig.manager.recordFailure() }
        return last
    }

    @Test fun firstTwoFailuresDoNotLock() = runTest {
        assertEquals(LockoutStatus.Open, fail(1))
        assertEquals(LockoutStatus.Open, fail(1))
        assertEquals(LockoutStatus.Open, rig.manager.status())
        assertEquals(2, rig.repo.state.failedAttempts)
    }

    @Test fun lockoutDurationsFollowTheTable() = runTest {
        assertEquals(LockoutStatus.Open, fail(2))
        assertEquals(LockoutStatus.Waiting(30_000), fail(1)) // 3rd
        rig.clock.advanceSeconds(31)
        assertEquals(LockoutStatus.Waiting(30_000), fail(1)) // 4th
        rig.clock.advanceSeconds(31)
        assertEquals(LockoutStatus.Waiting(60_000), fail(1)) // 5th
        rig.clock.advanceSeconds(61)
        fail(2) // 6th, 7th
        rig.clock.advanceSeconds(61)
        assertEquals(LockoutStatus.Waiting(300_000), fail(1)) // 8th
        rig.clock.advanceSeconds(301)
        assertEquals(LockoutStatus.Waiting(300_000), fail(1)) // 9th
        rig.clock.advanceSeconds(301)
        assertEquals(LockoutStatus.CredentialRequired, fail(1)) // 10th
    }

    @Test fun remainingTimeCountsDownAndExpires() = runTest {
        fail(3)
        rig.clock.advanceSeconds(10)
        assertEquals(LockoutStatus.Waiting(20_000), rig.manager.status())
        rig.clock.advanceSeconds(19)
        assertEquals(LockoutStatus.Waiting(1_000), rig.manager.status())
        rig.clock.advanceSeconds(1)
        assertEquals(LockoutStatus.Open, rig.manager.status())
    }

    @Test fun counterSurvivesExpiryAndNextFailureLocksAgain() = runTest {
        fail(3)
        rig.clock.advanceSeconds(31)
        assertEquals(LockoutStatus.Open, rig.manager.status())
        assertEquals(3, rig.repo.state.failedAttempts)
        assertEquals(LockoutStatus.Waiting(30_000), fail(1))
    }

    @Test fun successResetsEverything() = runTest {
        fail(4)
        rig.manager.recordSuccess()
        assertEquals(0, rig.repo.state.failedAttempts)
        assertEquals(LockoutStatus.Open, rig.manager.status())
        assertEquals(LockoutStatus.Open, fail(2))
    }

    @Test fun tenthFailureWithoutScreenLockStaysAtFiveMinuteWait() = runTest {
        val noLock = LockoutRig(credentialAvailable = false)
        var last: LockoutStatus = LockoutStatus.Open
        repeat(10) { last = noLock.manager.recordFailure(); noLock.clock.advanceSeconds(301) }
        noLock.clock.now -= 301_000
        assertEquals(LockoutStatus.Waiting(300_000), last)
        assertEquals(false, noLock.repo.state.requiresDeviceCredential)
    }

    @Test fun credentialRequirementPersistsUntilDeviceCredentialSucceeds() = runTest {
        rig.clock.now = 0
        repeat(10) { rig.manager.recordFailure(); rig.clock.advanceSeconds(400) }
        assertEquals(LockoutStatus.CredentialRequired, rig.manager.status())
        rig.clock.advanceSeconds(100_000)
        assertEquals(LockoutStatus.CredentialRequired, rig.manager.status())
        rig.manager.resetAfterDeviceCredential()
        assertEquals(LockoutStatus.Open, rig.manager.status())
        assertEquals(0, rig.repo.state.failedAttempts)
    }

    @Test fun screenLockRemovedAfterRequirementFallsBackToWait() = runTest {
        fail(10)
        rig.credentials.available = false
        assertEquals(LockoutStatus.Waiting(300_000), rig.manager.status())
        assertEquals(false, rig.repo.state.requiresDeviceCredential)
    }

    // --- Process death: a brand-new manager over the same persisted state ---

    @Test fun stateIsRestoredByAFreshManagerAfterProcessDeath() = runTest {
        fail(5)
        rig.clock.advanceSeconds(20)
        val restored = LockoutManager(rig.repo, rig.clock, rig.credentials)
        assertEquals(LockoutStatus.Waiting(40_000), restored.status())
    }

    // --- Reboot and clock tampering ---

    @Test fun rebootRestartsTheFullWait() = runTest {
        fail(5) // 60 s wait
        rig.clock.advanceSeconds(50)
        rig.clock.boot = 6
        rig.clock.now = 5_000 // elapsedRealtime restarts after boot
        assertEquals(LockoutStatus.Waiting(60_000), rig.manager.status())
        // The re-anchored deadline now counts down normally.
        rig.clock.advanceSeconds(30)
        assertEquals(LockoutStatus.Waiting(30_000), rig.manager.status())
    }

    @Test fun clockGoingBackwardsIsTreatedAsRebootEvenIfBootCountUnknown() = runTest {
        rig.clock.boot = -1
        fail(3)
        rig.clock.now -= 500_000
        assertEquals(LockoutStatus.Waiting(30_000), rig.manager.status())
    }

    @Test fun rebootAfterWaitAlreadyEndedDoesNotLockAgain() = runTest {
        fail(3)
        rig.clock.advanceSeconds(31)
        assertEquals(LockoutStatus.Open, rig.manager.status()) // clears the finished deadline
        rig.clock.boot = 9
        rig.clock.now = 1_000
        assertEquals(LockoutStatus.Open, rig.manager.status())
    }

    @Test fun wallClockIsNeverUsed() = runTest {
        // The manager only reads ElapsedClock, so there is nothing a system-time change can affect.
        fail(3)
        assertTrue(rig.repo.state.lockedUntilElapsedMs == rig.clock.now + 30_000)
    }

    @Test fun unreadableStateThrowsSoCallersFailClosed() = runTest {
        rig.repo.failing = true
        try {
            rig.manager.status()
            throw AssertionError("expected SecretStoreException")
        } catch (e: SecretStoreException) {
            Unit
        }
    }
}
