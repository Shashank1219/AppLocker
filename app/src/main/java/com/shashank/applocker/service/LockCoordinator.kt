package com.shashank.applocker.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-off between the accessibility service and the lock Activity, in memory only.
 * The service names the app being locked; the Activity reports a cancel back so the service can
 * send the user Home (the Activity cannot do that itself). The target is memory-only, so after
 * process death the lock Activity has no target and closes instead of unlocking anything.
 */
@Singleton
class LockCoordinator @Inject constructor() {
    private val _cancelled = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val cancelled: SharedFlow<Unit> = _cancelled.asSharedFlow()

    private val _target = MutableStateFlow<String?>(null)

    /** The package the lock screen is currently unlocking. */
    val target: StateFlow<String?> = _target.asStateFlow()

    fun request(packageName: String) {
        _target.value = packageName
    }

    fun clear() {
        _target.value = null
    }

    fun cancel() {
        _cancelled.tryEmit(Unit)
    }
}
