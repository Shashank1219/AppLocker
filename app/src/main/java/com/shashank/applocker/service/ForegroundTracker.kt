package com.shashank.applocker.service

/** What the service should do about a window change. */
sealed interface ForegroundDecision {
    data object None : ForegroundDecision
    data object ShowLock : ForegroundDecision
}

/**
 * Pure decision logic for window-change events, with no Android dependency.
 *
 * - Events with no package, or from an ignored package (this app, keyboards), change nothing.
 * - A locked package shows the lock once. Further events from the same package (dialogs, popups)
 *   do not stack more lock screens while one is already up for it.
 * - Any other app coming to the front clears that, so returning to the locked app locks again.
 *
 * Must be used from one thread (the service's main thread). It holds no package names beyond the
 * current one, in memory only.
 */
class ForegroundTracker(private val ignoredPackages: () -> Set<String>) {
    private var lockShownFor: String? = null

    fun isIgnored(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName in ignoredPackages()

    fun onWindowChanged(packageName: String?, isLocked: Boolean): ForegroundDecision {
        if (packageName == null || isIgnored(packageName)) return ForegroundDecision.None
        if (!isLocked) {
            lockShownFor = null
            return ForegroundDecision.None
        }
        if (lockShownFor == packageName) return ForegroundDecision.None
        lockShownFor = packageName
        return ForegroundDecision.ShowLock
    }

    /** The lock screen was cancelled or closed without unlocking. */
    fun onLockDismissed() {
        lockShownFor = null
    }

    fun reset() {
        lockShownFor = null
    }
}
