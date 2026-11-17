package com.shashank.applocker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shashank.applocker.domain.model.PinStatus
import com.shashank.applocker.domain.repository.PinRepository
import com.shashank.applocker.security.AppSession
import com.shashank.applocker.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StartState {
    data object Loading : StartState
    data class Ready(val route: String) : StartState

    /** Secure storage failed. Never routed to PIN setup, which would overwrite the PIN. */
    data object StorageError : StartState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val pinRepository: PinRepository,
    private val session: AppSession,
) : ViewModel() {
    private val _start = MutableStateFlow<StartState>(StartState.Loading)
    val start: StateFlow<StartState> = _start.asStateFlow()

    /** In-memory only. False after process death, so a restored screen can never skip the PIN. */
    val isUnlocked: StateFlow<Boolean> = session.unlocked

    init {
        load()
    }

    fun load() {
        _start.value = StartState.Loading
        viewModelScope.launch {
            _start.value = when (pinRepository.status()) {
                PinStatus.NOT_SET -> StartState.Ready(Route.PinSetup.path)
                PinStatus.SET -> StartState.Ready(Route.PinUnlock.path)
                PinStatus.UNAVAILABLE -> StartState.StorageError
            }
        }
    }

    fun onUnlocked() = session.unlock()

    /** All data was erased: reload so the app starts again at PIN setup, with the session locked. */
    fun onDataWiped() {
        load()
        session.lock()
    }

    /** Called when the app leaves the foreground (not for rotation). */
    fun onLeftForeground() = session.onLeftForeground()

    /** Called when the app is back in front. */
    fun onReturned() = session.onReturned()
}
