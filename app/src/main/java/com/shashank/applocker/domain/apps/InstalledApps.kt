package com.shashank.applocker.domain.apps

/** An installed app that has a launcher entry. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/** Apps the phone needs to stay usable. Locking one gets a warning first. */
enum class CriticalRole { LAUNCHER, PHONE, SETTINGS, KEYBOARD }

/** What the device reports right now. */
data class InstalledAppsSnapshot(
    /** Launchable apps, never including this app. */
    val apps: List<InstalledApp>,
    /** Essential apps, resolved at runtime. Keyed by package name. */
    val criticalRoles: Map<String, CriticalRole>,
)

interface InstalledAppsSource {
    /** This app's own package. It can never be selected. */
    val ownPackage: String

    /** Throws if the device cannot be queried. */
    suspend fun load(): InstalledAppsSnapshot

    /** True if the package is still installed, even if disabled or not launchable now. */
    suspend fun isInstalled(packageName: String): Boolean
}
