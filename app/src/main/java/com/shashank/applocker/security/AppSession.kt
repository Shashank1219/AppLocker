package com.shashank.applocker.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the user has entered the PIN for this run of the locker's own screens.
 * Memory only: process death, restart or reboot starts locked (fail closed). Not persisted,
 * and never restored from saved instance state or navigation state.
 *
 * Leaving the app locks it, with one deliberate exception: when the app itself sends the user to
 * a system settings screen (to switch on the Accessibility service, or to grant a permission),
 * coming straight back must not ask for the PIN again. That exception is short ([TRIP_MS]) and
 * only covers trips the app started; any other way of leaving locks as before.
 */
@Singleton
class AppSession @Inject constructor(private val clock: ElapsedClock) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var tripAllowedUntilMs = 0L
    private var inTrip = false

    @Synchronized
    fun unlock() {
        _unlocked.value = true
    }

    @Synchronized
    fun lock() {
        _unlocked.value = false
        tripAllowedUntilMs = 0L
        inTrip = false
    }

    /** The app is about to open a system settings screen. Call only after it actually opened. */
    @Synchronized
    fun expectSystemTrip() {
        if (_unlocked.value) tripAllowedUntilMs = clock.elapsedRealtimeMs() + TRIP_MS
    }

    /** The app left the foreground (not a rotation). */
    @Synchronized
    fun onLeftForeground() {
        if (_unlocked.value && clock.elapsedRealtimeMs() < tripAllowedUntilMs) inTrip = true else lock()
    }

    /** The app is back in front. A trip that took too long ends here, locked. */
    @Synchronized
    fun onReturned() {
        if (!inTrip) return
        inTrip = false
        val expired = clock.elapsedRealtimeMs() >= tripAllowedUntilMs
        tripAllowedUntilMs = 0L
        if (expired) lock()
    }

    private companion object {
        const val TRIP_MS = 2 * 60 * 1000L
    }
}
