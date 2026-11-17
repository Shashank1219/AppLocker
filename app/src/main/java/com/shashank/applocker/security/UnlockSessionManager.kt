package com.shashank.applocker.security

import com.shashank.applocker.domain.model.RelockDelay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which protected apps the user has unlocked, held in memory only. Process death, a service
 * restart or a reboot all start with nothing unlocked, so every app is locked again (fail closed).
 * Nothing here is written to disk, and no static state is used.
 *
 * Re-lock rule: an unlocked app stays unlocked while it is in front. The delay counts from the
 * moment the user LEAVES it (another real app, the launcher or Recents came to the front):
 * - [RelockDelay.IMMEDIATELY]: leaving it locks it.
 * - timed delays: coming back within the delay keeps it unlocked, later locks it.
 * - [RelockDelay.ON_SCREEN_OFF]: no timer; only [lockAll] (screen off, "Lock all now") locks it.
 * Timers use [ElapsedClock] (elapsed realtime), never the wall clock.
 */
@Singleton
class UnlockSessionManager @Inject constructor(private val clock: ElapsedClock) {
    private class Session(var leftAtMs: Long?)

    private val sessions = HashMap<String, Session>()

    /** The last real app seen in front. Null until one is seen. Never holds ignored windows. */
    @get:Synchronized
    var foregroundPackage: String? = null
        private set

    /** A real app (not this app, keyboard or system UI) came to the front. */
    @Synchronized
    fun onForeground(packageName: String) {
        if (packageName == foregroundPackage) return
        foregroundPackage?.let { sessions[it] }?.let { if (it.leftAtMs == null) it.leftAtMs = clock.elapsedRealtimeMs() }
        foregroundPackage = packageName
    }

    /** True if [packageName] must be unlocked before it can be used. May end an expired session. */
    @Synchronized
    fun requiresUnlock(packageName: String, delay: RelockDelay): Boolean {
        val session = sessions[packageName] ?: return true
        val leftAt = session.leftAtMs ?: return false // still in front
        val seconds = delay.seconds
        if (seconds == null) { // only screen off relocks
            session.leftAtMs = null
            return false
        }
        if (clock.elapsedRealtimeMs() - leftAt >= seconds * 1000) {
            sessions.remove(packageName)
            return true
        }
        session.leftAtMs = null // back within the grace period
        return false
    }

    /** The user authenticated for [packageName]. */
    @Synchronized
    fun onUnlocked(packageName: String) {
        val inFront = packageName == foregroundPackage
        sessions[packageName] = Session(if (inFront) null else clock.elapsedRealtimeMs())
    }

    /** Screen off, "Lock all now", or the service going away. */
    @Synchronized
    fun lockAll() {
        sessions.clear()
    }
}
