package com.shashank.applocker.feature.apps

import com.shashank.applocker.MainDispatcherRule
import com.shashank.applocker.domain.apps.AppListBuilder
import com.shashank.applocker.domain.apps.CriticalRole
import com.shashank.applocker.domain.apps.InstalledApp
import com.shashank.applocker.domain.apps.InstalledAppsSnapshot
import com.shashank.applocker.domain.apps.InstalledAppsSource
import com.shashank.applocker.domain.repository.ProtectedAppRepository
import com.shashank.applocker.security.FakeSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FakeProtectedApps(initial: Set<String> = emptySet()) : ProtectedAppRepository {
    val selected = MutableStateFlow(initial)
    var failWrites = false
    var reconciledWith: Set<String>? = null

    override fun observeSelectedPackages(): Flow<Set<String>> = selected
    override fun observeLockedPackages(): Flow<Set<String>> = selected.map { it }
    override suspend fun isLocked(packageName: String) = packageName in selected.value
    override suspend fun setSelected(packageName: String, selected: Boolean) {
        if (failWrites) error("disk full")
        this.selected.value = if (selected) this.selected.value + packageName else this.selected.value - packageName
    }
    override suspend fun setLockActive(packageName: String, active: Boolean) = Unit
    override suspend fun clearAll() { selected.value = emptySet() }
    override suspend fun reconcile(installedPackages: Set<String>): Set<String> {
        reconciledWith = installedPackages
        val stale = selected.value - installedPackages
        selected.value = selected.value intersect installedPackages
        return stale
    }
}

class FakeAppsSource : InstalledAppsSource {
    override val ownPackage = "com.me.locker"
    var apps = listOf(
        InstalledApp("com.b.camera", "camera", false),
        InstalledApp("com.a.Zoo", "Zoo", false),
        InstalledApp("com.sys.clock", "Clock", true),
        InstalledApp("com.sys.dialer", "Phone", true),
        InstalledApp("com.me.locker", "Locker", false), // would be filtered, also by the VM
    )
    var roles = mapOf("com.sys.dialer" to CriticalRole.PHONE, "com.kb" to CriticalRole.KEYBOARD)
    var failLoad = false
    var installedElsewhere = emptySet<String>()
    override suspend fun load(): InstalledAppsSnapshot {
        if (failLoad) error("package manager died")
        return InstalledAppsSnapshot(apps, roles)
    }
    override suspend fun isInstalled(packageName: String) = packageName in installedElsewhere
}

class AppSelectionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val source = FakeAppsSource()
    private val repo = FakeProtectedApps()
    private val settings = FakeSettingsRepository()
    // Lazy: the ViewModel loads in init, after MainDispatcherRule has run.
    private val vm by lazy { AppSelectionViewModel(source, repo, settings) }
    private val labels get() = vm.state.value.items.map { it.label }

    @Test fun listsUserAppsAlphabeticallyAndHidesSystemAppsByDefault() {
        assertFalse(vm.state.value.loading)
        assertEquals(listOf("camera", "Zoo"), labels) // case-insensitive order
    }

    @Test fun showSystemAppsSettingIsPersistedAndIncludesThem() {
        vm.onEvent(AppSelectionEvent.ShowSystemApps(true))
        assertTrue(settings.state.value.showSystemApps)
        assertEquals(listOf("camera", "Clock", "Phone", "Zoo"), labels)
    }

    @Test fun searchMatchesLabelOrPackageNameIgnoringCase() {
        vm.onEvent(AppSelectionEvent.QueryChanged("ZOO"))
        assertEquals(listOf("Zoo"), labels)
        vm.onEvent(AppSelectionEvent.QueryChanged("com.b."))
        assertEquals(listOf("camera"), labels)
        vm.onEvent(AppSelectionEvent.QueryChanged("nothing"))
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test fun togglingPersistsThroughTheRepository() {
        vm.onEvent(AppSelectionEvent.Toggle("com.a.Zoo", true))
        assertEquals(setOf("com.a.Zoo"), repo.selected.value)
        assertEquals(1, vm.state.value.selectedCount)
        assertTrue(vm.state.value.items.first { it.packageName == "com.a.Zoo" }.selected)
        vm.onEvent(AppSelectionEvent.Toggle("com.a.Zoo", false))
        assertTrue(repo.selected.value.isEmpty())
    }

    @Test fun theLockerItselfCanNeverBeSelected() {
        vm.onEvent(AppSelectionEvent.ShowSystemApps(true))
        assertFalse(vm.state.value.items.any { it.packageName == "com.me.locker" })
        vm.onEvent(AppSelectionEvent.Toggle("com.me.locker", true))
        assertTrue(repo.selected.value.isEmpty())
        assertNull(vm.state.value.warning)
    }

    @Test fun essentialAppAsksForConfirmationBeforeBeingSaved() {
        vm.onEvent(AppSelectionEvent.ShowSystemApps(true))
        vm.onEvent(AppSelectionEvent.Toggle("com.sys.dialer", true))
        assertEquals(CriticalRole.PHONE, vm.state.value.warning?.role)
        assertTrue(repo.selected.value.isEmpty()) // nothing saved yet
        vm.onEvent(AppSelectionEvent.ConfirmWarning)
        assertEquals(setOf("com.sys.dialer"), repo.selected.value)
        assertNull(vm.state.value.warning)
    }

    @Test fun decliningTheWarningSavesNothing() {
        vm.onEvent(AppSelectionEvent.ShowSystemApps(true))
        vm.onEvent(AppSelectionEvent.Toggle("com.sys.dialer", true))
        vm.onEvent(AppSelectionEvent.DismissWarning)
        assertTrue(repo.selected.value.isEmpty())
        assertNull(vm.state.value.warning)
    }

    @Test fun unprotectingAnEssentialAppNeedsNoWarning() {
        repo.selected.value = setOf("com.sys.dialer")
        vm.onEvent(AppSelectionEvent.Toggle("com.sys.dialer", false))
        assertNull(vm.state.value.warning)
        assertTrue(repo.selected.value.isEmpty())
    }

    @Test fun uninstalledAppsAreDroppedOnLoad() {
        repo.selected.value = setOf("com.a.Zoo", "com.gone")
        vm.state.value
        assertEquals(setOf("com.a.Zoo"), repo.selected.value)
    }

    @Test fun disabledButInstalledAppIsKeptAndShownAsUnavailable() {
        repo.selected.value = setOf("com.disabled")
        source.installedElsewhere = setOf("com.disabled")
        val item = vm.state.value.items.single { it.packageName == "com.disabled" }
        assertFalse(item.available)
        assertTrue(item.selected)
        vm.onEvent(AppSelectionEvent.Toggle("com.disabled", false)) // it can still be removed
        assertTrue(repo.selected.value.isEmpty())
    }

    @Test fun anUnavailableAppCannotBeSelected() {
        // A stale row that is not selected cannot exist, so a toggle for an unknown package is ignored.
        vm.onEvent(AppSelectionEvent.Toggle("com.unknown", true))
        assertTrue(repo.selected.value.isEmpty())
    }

    @Test fun failedLoadShowsErrorAndNeverTouchesTheSelection() {
        repo.selected.value = setOf("com.a.Zoo")
        source.failLoad = true
        assertTrue(vm.state.value.error)
        assertNull(repo.reconciledWith)
        assertEquals(setOf("com.a.Zoo"), repo.selected.value)
        source.failLoad = false
        vm.onEvent(AppSelectionEvent.Retry)
        assertFalse(vm.state.value.error)
        assertEquals(listOf("camera", "Zoo"), labels)
    }

    @Test fun emptyDeviceListIsAnErrorNotAWipe() {
        repo.selected.value = setOf("com.a.Zoo")
        source.apps = emptyList()
        assertTrue(vm.state.value.error)
        assertEquals(setOf("com.a.Zoo"), repo.selected.value)
    }

    @Test fun saveFailureIsReportedAndCanBeDismissed() {
        repo.failWrites = true
        vm.onEvent(AppSelectionEvent.Toggle("com.a.Zoo", true))
        assertTrue(vm.state.value.saveFailed)
        vm.onEvent(AppSelectionEvent.DismissSaveError)
        assertFalse(vm.state.value.saveFailed)
    }

    // --- Pure list logic ---

    @Test fun selectedSystemAppStaysVisibleWhenSystemAppsAreHidden() {
        val snap = InstalledAppsSnapshot(listOf(InstalledApp("s", "Sys", true)), emptyMap())
        val items = AppListBuilder.build(snap, setOf("s"), "me")
        assertEquals(1, AppListBuilder.filterAndSort(items, "", showSystem = false).size)
    }
}
