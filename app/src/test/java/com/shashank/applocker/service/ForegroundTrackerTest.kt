package com.shashank.applocker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTrackerTest {
    private var ignored = setOf("me", "kb")
    private val tracker = ForegroundTracker { ignored }

    @Test fun lockedAppShowsLock() =
        assertEquals(ForegroundDecision.ShowLock, tracker.onWindowChanged("bank", true))

    @Test fun unprotectedAppDoesNothing() =
        assertEquals(ForegroundDecision.None, tracker.onWindowChanged("game", false))

    @Test fun missingOrBlankPackageIsIgnored() {
        assertEquals(ForegroundDecision.None, tracker.onWindowChanged(null, true))
        assertEquals(ForegroundDecision.None, tracker.onWindowChanged("", true))
        assertTrue(tracker.isIgnored(" "))
    }

    @Test fun ownAppAndKeyboardsAreIgnoredEvenIfLocked() {
        assertEquals(ForegroundDecision.None, tracker.onWindowChanged("me", true))
        assertEquals(ForegroundDecision.None, tracker.onWindowChanged("kb", true))
        assertTrue(tracker.isIgnored("kb"))
        assertFalse(tracker.isIgnored("bank"))
    }

    @Test fun repeatEventsFromTheSameAppDoNotStackLockScreens() {
        assertEquals(ForegroundDecision.ShowLock, tracker.onWindowChanged("bank", true))
        assertEquals(ForegroundDecision.None, tracker.onWindowChanged("bank", true))
    }

    @Test fun ignoredWindowsInBetweenDoNotResetTheLock() {
        tracker.onWindowChanged("bank", true)
        tracker.onWindowChanged("me", true) // our own lock screen appears
        assertEquals(ForegroundDecision.None, tracker.onWindowChanged("bank", true))
    }

    @Test fun leavingAndReturningLocksAgain() {
        tracker.onWindowChanged("bank", true)
        tracker.onWindowChanged("game", false)
        assertEquals(ForegroundDecision.ShowLock, tracker.onWindowChanged("bank", true))
    }

    @Test fun switchingBetweenTwoLockedAppsLocksEach() {
        assertEquals(ForegroundDecision.ShowLock, tracker.onWindowChanged("a", true))
        assertEquals(ForegroundDecision.ShowLock, tracker.onWindowChanged("b", true))
    }

    @Test fun dismissedLockShowsAgainWhenTheAppReturns() {
        tracker.onWindowChanged("bank", true)
        tracker.onLockDismissed()
        assertEquals(ForegroundDecision.ShowLock, tracker.onWindowChanged("bank", true))
    }

    @Test fun resetForgetsEverything() {
        tracker.onWindowChanged("bank", true)
        tracker.reset()
        assertEquals(ForegroundDecision.ShowLock, tracker.onWindowChanged("bank", true))
    }

    @Test fun ignoreListIsReadLive() {
        ignored = emptySet()
        assertEquals(ForegroundDecision.ShowLock, tracker.onWindowChanged("kb", true))
    }
}
