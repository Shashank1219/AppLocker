package com.shashank.applocker.service

import com.shashank.applocker.domain.model.RelockDelay
import com.shashank.applocker.security.UnlockSessionManager

/**
 * Combines the protected-package check, the unlock sessions and the duplicate-lock tracking into
 * the single decision the service needs. Pure logic with no Android dependency.
 */
class LockGate(
    private val tracker: ForegroundTracker,
    private val sessions: UnlockSessionManager,
) {
    /** The real app currently in front, if known. */
    val foregroundPackage: String? get() = sessions.foregroundPackage

    fun onWindowChanged(packageName: String?, isProtected: Boolean, delay: RelockDelay): ForegroundDecision {
        if (packageName == null || tracker.isIgnored(packageName)) return ForegroundDecision.None
        sessions.onForeground(packageName)
        val needsUnlock = isProtected && sessions.requiresUnlock(packageName, delay)
        return tracker.onWindowChanged(packageName, needsUnlock)
    }

    /** The screen turned off: everything locks, and the next look at the front app may show a lock. */
    fun onScreenOff() {
        sessions.lockAll()
        tracker.reset()
    }

    fun onLockDismissed() = tracker.onLockDismissed()

    fun reset() {
        sessions.lockAll()
        tracker.reset()
    }
}
