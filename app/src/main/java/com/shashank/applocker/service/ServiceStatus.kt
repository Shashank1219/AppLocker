package com.shashank.applocker.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ServiceState { DISCONNECTED, CONNECTED, INTERRUPTED }

/**
 * In-memory view of the accessibility service's lifecycle. It starts DISCONNECTED in every new
 * process, so a killed or restarted service never reports itself as running until the system
 * reconnects it. Whether the user enabled the service is a separate question, answered by
 * [AccessibilityStatusSource].
 */
@Singleton
class ServiceStatus @Inject constructor() {
    private val _state = MutableStateFlow(ServiceState.DISCONNECTED)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    fun onConnected() = _state.update { ServiceState.CONNECTED }
    fun onInterrupted() = _state.update { ServiceState.INTERRUPTED }
    fun onDisconnected() = _state.update { ServiceState.DISCONNECTED }

    /** Events flowing again after an interruption means the service is working. */
    fun onEventReceived() = _state.update { if (it == ServiceState.INTERRUPTED) ServiceState.CONNECTED else it }
}
