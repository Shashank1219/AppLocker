package com.shashank.applocker.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shashank.applocker.domain.apps.AppItem
import com.shashank.applocker.domain.apps.AppListBuilder
import com.shashank.applocker.domain.apps.InstalledAppsSnapshot
import com.shashank.applocker.domain.apps.InstalledAppsSource
import com.shashank.applocker.domain.apps.ProtectedAppReconciler
import com.shashank.applocker.domain.repository.ProtectedAppRepository
import com.shashank.applocker.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppSelectionUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val query: String = "",
    val showSystemApps: Boolean = false,
    /** Filtered and sorted rows. */
    val items: List<AppItem> = emptyList(),
    val selectedCount: Int = 0,
    /** An essential app waiting for the user to confirm. */
    val warning: AppItem? = null,
    val saveFailed: Boolean = false,
)

sealed interface AppSelectionEvent {
    data class QueryChanged(val query: String) : AppSelectionEvent
    data class Toggle(val packageName: String, val selected: Boolean) : AppSelectionEvent
    data object ConfirmWarning : AppSelectionEvent
    data object DismissWarning : AppSelectionEvent
    data class ShowSystemApps(val show: Boolean) : AppSelectionEvent
    data object Retry : AppSelectionEvent

    /** Reload quietly, for example when returning to the screen. No loading spinner. */
    data object Refresh : AppSelectionEvent
    data object DismissSaveError : AppSelectionEvent
}

@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    private val source: InstalledAppsSource,
    private val protectedApps: ProtectedAppRepository,
    private val settings: SettingsRepository,
    private val reconciler: ProtectedAppReconciler,
) : ViewModel() {
    private sealed interface Load {
        data object Loading : Load
        data object Failed : Load
        data class Ready(val snapshot: InstalledAppsSnapshot) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)
    private val query = MutableStateFlow("")
    private val warning = MutableStateFlow<AppItem?>(null)
    private val saveFailed = MutableStateFlow(false)

    val state: StateFlow<AppSelectionUiState> = combine(
        load,
        protectedApps.observeSelectedPackages(),
        settings.settings.map { it.showSystemApps },
        query,
        combine(warning, saveFailed) { w, s -> w to s },
    ) { load, selected, showSystem, query, (warning, saveFailed) ->
        val items = (load as? Load.Ready)?.let {
            AppListBuilder.filterAndSort(AppListBuilder.build(it.snapshot, selected, source.ownPackage), query, showSystem)
        }.orEmpty()
        AppSelectionUiState(
            loading = load is Load.Loading,
            error = load is Load.Failed,
            query = query,
            showSystemApps = showSystem,
            items = items,
            selectedCount = selected.size,
            warning = warning,
            saveFailed = saveFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppSelectionUiState())

    init {
        refresh()
    }

    fun onEvent(event: AppSelectionEvent) {
        when (event) {
            is AppSelectionEvent.QueryChanged -> query.value = event.query
            is AppSelectionEvent.Toggle -> toggle(event.packageName, event.selected)
            AppSelectionEvent.ConfirmWarning -> warning.value?.let {
                warning.value = null
                save(it.packageName, true)
            }
            AppSelectionEvent.DismissWarning -> warning.value = null
            is AppSelectionEvent.ShowSystemApps -> viewModelScope.launch { settings.setShowSystemApps(event.show) }
            AppSelectionEvent.Retry -> refresh()
            AppSelectionEvent.Refresh -> refresh(silent = true)
            AppSelectionEvent.DismissSaveError -> saveFailed.value = false
        }
    }

    private fun toggle(packageName: String, select: Boolean) {
        if (packageName == source.ownPackage) return // This app can never be locked.
        if (!select) return save(packageName, false)
        val item = state.value.items.firstOrNull { it.packageName == packageName } ?: return
        if (!item.available) return // A missing app can only be removed.
        if (item.role != null) warning.value = item else save(packageName, true)
    }

    private fun save(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            try {
                protectedApps.setSelected(packageName, selected)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                saveFailed.value = true
            }
        }
    }

    /** Loads the device's apps and drops selections for packages that are really gone. */
    private fun refresh(silent: Boolean = false) {
        if (!silent || load.value !is Load.Ready) load.value = Load.Loading
        viewModelScope.launch {
            val snapshot = try {
                source.load().takeIf { it.apps.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (snapshot == null) {
                // No reconcile on a failed or empty read: that could wipe the whole selection.
                if (!silent || load.value !is Load.Ready) load.value = Load.Failed
                return@launch
            }
            reconciler.reconcile(snapshot)
            load.value = Load.Ready(snapshot)
        }
    }
}
