package com.shashank.applocker.domain.apps

import com.shashank.applocker.domain.repository.ProtectedAppRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Keeps the protected-app list in step with what is really installed, so an uninstalled app
 * disappears from the app.
 *
 * Used when the app-selection screen opens, when the accessibility service starts, and when the
 * service sees a package being removed.
 */
@Singleton
class ProtectedAppReconciler @Inject constructor(
    private val source: InstalledAppsSource,
    private val protectedApps: ProtectedAppRepository,
) {
    /**
     * Drops selections for packages that are really gone. A package that is installed but disabled
     * or not launchable right now is kept. If the device cannot be read, or reports no apps at all,
     * nothing is touched: that could wipe the whole selection by mistake.
     *
     * @param snapshot an already loaded snapshot, to avoid reading the device twice.
     * @return true if the list was reconciled.
     */
    suspend fun reconcile(snapshot: InstalledAppsSnapshot? = null): Boolean {
        val current = snapshot ?: try {
            source.load()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        if (current.apps.isEmpty()) return false
        return try {
            val launchable = current.apps.mapTo(HashSet()) { it.packageName }
            val keep = protectedApps.observeSelectedPackages().first().filterTo(HashSet()) {
                it in launchable || source.isInstalled(it)
            }
            protectedApps.reconcile(keep)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false // stale entries are tried again next time
        }
    }

    /** A package was just uninstalled. */
    suspend fun onPackageRemoved(packageName: String) {
        try {
            protectedApps.setSelected(packageName, false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The next reconcile will remove it.
        }
    }
}
