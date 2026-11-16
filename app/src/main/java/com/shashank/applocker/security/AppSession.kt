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
 */
@Singleton
class AppSession @Inject constructor() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }
}
