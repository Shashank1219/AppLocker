package com.shashank.applocker.security

import com.shashank.applocker.domain.model.RelockDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockSessionManagerTest {
    private val clock = FakeClock()
    private val sessions = UnlockSessionManager(clock)

    private fun unlockInFront(pkg: String) {
        sessions.onForeground(pkg)
        sessions.onUnlocked(pkg)
    }

    private fun leave(to: String = "launcher") = sessions.onForeground(to)
    private fun secondsLater(s: Long) { clock.now += s * 1000 }

    @Test fun nothingIsUnlockedAtStart() {
        assertTrue(sessions.requiresUnlock("a", RelockDelay.ON_SCREEN_OFF))
        assertNull(sessions.foregroundPackage)
    }

    @Test fun staysUnlockedWhileInFrontForAnyDelay() {
        unlockInFront("a")
        secondsLater(3600)
        RelockDelay.entries.forEach { assertFalse(sessions.requiresUnlock("a", it)) }
    }

    @Test fun immediatelyRelocksAsSoonAsTheUserLeaves() {
        unlockInFront("a")
        leave()
        assertTrue(sessions.requiresUnlock("a", RelockDelay.IMMEDIATELY))
    }

    @Test fun timedDelayKeepsTheAppUnlockedInsideTheWindowAndRelocksAfter() {
        unlockInFront("a")
        leave()
        secondsLater(29)
        assertFalse(sessions.requiresUnlock("a", RelockDelay.AFTER_30_SECONDS))
        sessions.onForeground("a")
        leave() // leaves again: the timer starts over
        secondsLater(30)
        assertTrue(sessions.requiresUnlock("a", RelockDelay.AFTER_30_SECONDS))
    }

    @Test fun eachTimedDelayHasItsOwnBoundary() {
        listOf(RelockDelay.AFTER_30_SECONDS to 30L, RelockDelay.AFTER_1_MINUTE to 60L, RelockDelay.AFTER_5_MINUTES to 300L).forEach { (delay, secs) ->
            val s = UnlockSessionManager(clock)
            s.onForeground("a"); s.onUnlocked("a"); s.onForeground("x")
            clock.now += (secs - 1) * 1000
            assertFalse("$delay just inside", s.requiresUnlock("a", delay))
            s.onForeground("a"); s.onForeground("x")
            clock.now += secs * 1000
            assertTrue("$delay at boundary", s.requiresUnlock("a", delay))
        }
    }

    @Test fun onScreenOffModeNeverRelocksByTime() {
        unlockInFront("a")
        leave()
        secondsLater(24 * 3600)
        assertFalse(sessions.requiresUnlock("a", RelockDelay.ON_SCREEN_OFF))
    }

    @Test fun lockAllRelocksEverythingInEveryMode() {
        unlockInFront("a")
        sessions.onUnlocked("b")
        sessions.lockAll()
        RelockDelay.entries.forEach {
            assertTrue(sessions.requiresUnlock("a", it))
            assertTrue(sessions.requiresUnlock("b", it))
        }
    }

    @Test fun lockAllWhileTheAppIsInFrontStillLocksIt() {
        unlockInFront("a")
        sessions.lockAll()
        assertTrue(sessions.requiresUnlock("a", RelockDelay.ON_SCREEN_OFF))
    }

    @Test fun unlockingOneAppDoesNotUnlockAnother() {
        unlockInFront("a")
        assertTrue(sessions.requiresUnlock("b", RelockDelay.ON_SCREEN_OFF))
    }

    @Test fun anAppUnlockedWhileNotInFrontStartsItsTimerImmediately() {
        sessions.onForeground("launcher")
        sessions.onUnlocked("a")
        assertTrue(sessions.requiresUnlock("a", RelockDelay.IMMEDIATELY))
    }

    @Test fun switchingBetweenAppsOnlyStartsTheTimerForTheOneLeft() {
        unlockInFront("a")
        unlockInFront("b") // user went to b, unlocked it; a was left
        assertTrue(sessions.requiresUnlock("a", RelockDelay.IMMEDIATELY))
        assertFalse(sessions.requiresUnlock("b", RelockDelay.IMMEDIATELY))
    }

    @Test fun repeatForegroundEventsForTheSameAppAreHarmless() {
        unlockInFront("a")
        repeat(5) { sessions.onForeground("a") }
        assertFalse(sessions.requiresUnlock("a", RelockDelay.IMMEDIATELY))
    }

    @Test fun aNewManagerModelsProcessDeathAndEverythingIsLocked() {
        unlockInFront("a")
        val afterRestart = UnlockSessionManager(clock)
        RelockDelay.entries.forEach { assertTrue(afterRestart.requiresUnlock("a", it)) }
    }

    @Test fun reportsTheFrontApp() {
        sessions.onForeground("a")
        assertEquals("a", sessions.foregroundPackage)
    }
}
