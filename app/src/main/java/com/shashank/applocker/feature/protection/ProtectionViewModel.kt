package com.shashank.applocker.feature.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shashank.applocker.service.AccessibilityStatusSource
import com.shashank.applocker.service.ServiceState
import com.shashank.applocker.service.ServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ProtectionStatus {
    /** The service is not switched on in system settings. */
    OFF,

    /** Switched on, but the system has not connected it yet (just enabled, or restarting). */
    STARTING,
    ON,
}

data class ProtectionUiState(val status: ProtectionStatus = ProtectionStatus.OFF)

@HiltViewModel
class ProtectionViewModel @Inject constructor(
    private val source: AccessibilityStatusSource,
    serviceStatus: ServiceStatus,
) : ViewModel() {
    private val enabled = MutableStateFlow(source.isServiceEnabled())

    val state: StateFlow<ProtectionUiState> = combine(enabled, serviceStatus.state) { enabled, service ->
        ProtectionUiState(
            when {
                !enabled -> ProtectionStatus.OFF
                service == ServiceState.CONNECTED -> ProtectionStatus.ON
                else -> ProtectionStatus.STARTING
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProtectionUiState())

    /** Call on resume: the user changes this in system settings, outside the app. */
    fun refresh() {
        enabled.value = source.isServiceEnabled()
    }
}
