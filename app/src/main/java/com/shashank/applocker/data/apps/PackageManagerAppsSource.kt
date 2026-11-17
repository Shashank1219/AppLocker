package com.shashank.applocker.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.shashank.applocker.domain.apps.CriticalRole
import com.shashank.applocker.domain.apps.InstalledApp
import com.shashank.applocker.domain.apps.InstalledAppsSnapshot
import com.shashank.applocker.domain.apps.InstalledAppsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads launchable apps through the queries block in the manifest (no QUERY_ALL_PACKAGES).
 * Essential apps (home, dialer, settings, keyboards) are resolved at runtime, never by a
 * hardcoded package name.
 */
@Singleton
class PackageManagerAppsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstalledAppsSource {
    private val pm: PackageManager get() = context.packageManager

    override val ownPackage: String = context.packageName

    override suspend fun load(): InstalledAppsSnapshot = withContext(Dispatchers.IO) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = queryActivities(launcher)
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val pkg = activity.packageName
                if (pkg == ownPackage) return@mapNotNull null
                val flags = activity.applicationInfo?.flags ?: 0
                // An updated system app (a browser, a video app) is something the user thinks of as theirs.
                val system = flags and ApplicationInfo.FLAG_SYSTEM != 0 && flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                InstalledApp(pkg, info.loadLabel(pm).toString().ifBlank { pkg }, system)
            }
            .distinctBy { it.packageName }
        InstalledAppsSnapshot(apps, resolveRoles())
    }

    override suspend fun isInstalled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun resolveRoles(): Map<String, CriticalRole> {
        val roles = LinkedHashMap<String, CriticalRole>()
        fun add(pkg: String?, role: CriticalRole) {
            // "android" is the system chooser, returned when no default is set.
            if (pkg != null && pkg != "android" && pkg != ownPackage) roles.putIfAbsent(pkg, role)
        }
        runCatching {
            queryActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                .forEach { add(it.activityInfo?.packageName, CriticalRole.LAUNCHER) }
        }
        runCatching { add(defaultHandler(Intent(Intent.ACTION_DIAL)), CriticalRole.PHONE) }
        runCatching { add(defaultHandler(Intent(Settings.ACTION_SETTINGS)), CriticalRole.SETTINGS) }
        runCatching {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.enabledInputMethodList?.forEach { add(it.packageName, CriticalRole.KEYBOARD) }
        }
        return roles
    }

    private fun queryActivities(intent: Intent) =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun defaultHandler(intent: Intent): String? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }?.activityInfo?.packageName
}
