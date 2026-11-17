package com.shashank.applocker.service

import com.shashank.applocker.domain.model.RelockDelay
import com.shashank.applocker.security.FakeClock
import com.shashank.applocker.security.UnlockSessionManager
import org.junit.Assert.assertEquals
import org.junit.Test

class LockGateTest {
    private val clock = FakeClock()
    private val sessions = UnlockSessionManager(clock)
    private val tracker = ForegroundTracker { setOf("me", "kb", "ui") }
    private val gate = LockGate(tracker, sessions)
    private val protectedSet = setOf("bank", "mail")
    private var delay = RelockDelay.IMMEDIATELY

    private fun open(pkg: String?) = gate.onWindowChanged(pkg, pkg in protectedSet, delay)

    @Test fun protectedAppShowsLockUnprotectedDoesNot() {
        assertEquals(ForegroundDecision.None, open("game"))
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
    }

    @Test fun ignoredWindowsNeverCountAsLeavingOrLocking() {
        sessions.onForeground("bank"); sessions.onUnlocked("bank")
        listOf("me", "kb", "ui", null, "").forEach { assertEquals(ForegroundDecision.None, open(it)) }
        assertEquals(ForegroundDecision.None, open("bank")) // shade, keyboard, lock screen: still unlocked
    }

    @Test fun afterUnlockTheAppContinuesAndLeavingRelocksImmediately() {
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
        sessions.onUnlocked("bank") // lock screen succeeded
        assertEquals(ForegroundDecision.None, open("bank"))
        open("launcher") // Home
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
    }

    @Test fun goingViaRecentsOrAnotherAppRelocks() {
        open("bank"); sessions.onUnlocked("bank")
        open("recents-launcher")
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
    }

    @Test fun cancelledLockShowsAgainOnReturn() {
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
        gate.onLockDismissed()
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
    }

    @Test fun screenOffRelocksAndNextLookShowsTheLock() {
        delay = RelockDelay.ON_SCREEN_OFF
        open("bank"); sessions.onUnlocked("bank")
        open("launcher"); open("bank")
        assertEquals(ForegroundDecision.None, open("bank")) // still within "until screen off"
        gate.onScreenOff()
        assertEquals("bank", gate.foregroundPackage)
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
    }

    @Test fun timedDelayHonoursTheWindow() {
        delay = RelockDelay.AFTER_1_MINUTE
        open("bank"); sessions.onUnlocked("bank")
        open("launcher")
        clock.now += 30_000
        assertEquals(ForegroundDecision.None, open("bank"))
        open("launcher")
        clock.now += 61_000
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
    }

    @Test fun twoProtectedAppsAreIndependent() {
        open("bank"); sessions.onUnlocked("bank")
        assertEquals(ForegroundDecision.ShowLock, open("mail"))
    }

    @Test fun resetForgetsAllUnlocks() {
        open("bank"); sessions.onUnlocked("bank")
        gate.reset()
        assertEquals(ForegroundDecision.ShowLock, open("bank"))
    }

    @Test fun anAppRemovedFromTheProtectedSetIsNotLocked() {
        open("bank")
        assertEquals(ForegroundDecision.None, gate.onWindowChanged("bank", false, delay))
    }
}
