package com.shashank.applocker.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.shashank.applocker.domain.apps.ProtectedAppReconciler
import com.shashank.applocker.domain.model.RelockDelay
import com.shashank.applocker.domain.repository.ProtectedAppRepository
import com.shashank.applocker.domain.repository.SettingsRepository
import com.shashank.applocker.feature.lock.LockActivity
import com.shashank.applocker.security.UnlockSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Detects which app comes to the front and shows the lock screen for protected ones.
 *
 * Deliberately minimal: only window-state-changed events, no window content, no gestures, no key
 * events (see res/xml/accessibility_service_config.xml). Nothing here logs or stores package
 * names, and nothing is persisted: a killed, restarted or reconnected service starts with no
 * unlock state, so every app is locked.
 *
 * Everything runs on the main thread, which is where the system delivers events and broadcasts.
 */
@AndroidEntryPoint
class AppLockerAccessibilityService : AccessibilityService() {
    @Inject lateinit var protectedApps: ProtectedAppRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sessions: UnlockSessionManager
    @Inject lateinit var status: ServiceStatus
    @Inject lateinit var coordinator: LockCoordinator
    @Inject lateinit var reconciler: ProtectedAppReconciler

    private var scope: CoroutineScope? = null
    private var ignored: Set<String> = emptySet()
    private val tracker = ForegroundTracker { ignored }
    private val gate by lazy { LockGate(tracker, sessions) }

    /** Locked packages, kept up to date from the database. Null until the first read. */
    private var locked: Set<String>? = null

    /** Safest value until settings have been read. */
    private var relockDelay = RelockDelay.IMMEDIATELY

    private var homePackages: Set<String> = emptySet()
    private val realAppCache = HashMap<String, Boolean>()
    private var receiverRegistered = false
    private var packageReceiverRegistered = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
            // An app was uninstalled: it must disappear from the protected list.
            val removed = intent.data?.schemeSpecificPart ?: return
            scope?.launch { reconciler.onPackageRemoved(removed) }
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> gate.onScreenOff()
                // The front app may not send a new event when the screen comes back, so look again.
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    realAppCache.clear()
                    gate.foregroundPackage?.let(::evaluate)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The system can reconnect an existing instance: start from a clean slate.
        stop()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        ignored = resolveIgnoredPackages()
        realAppCache.clear()
        homePackages = resolveHomePackages()
        newScope.launch { protectedApps.observeLockedPackages().collect { locked = it } }
        newScope.launch { settings.settings.map { it.relockDelay }.collect { relockDelay = it } }
        newScope.launch {
            coordinator.cancelled.collect {
                gate.onLockDismissed()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED).apply { addDataScheme("package") }
        ContextCompat.registerReceiver(this, packageReceiver, packageFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        packageReceiverRegistered = true
        // Drop entries for apps uninstalled while the service was not running.
        newScope.launch { reconciler.reconcile() }
        status.onConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        status.onEventReceived()
        val pkg = event.packageName?.toString()
        if (pkg == null || tracker.isIgnored(pkg) || !isRealApp(pkg)) return
        evaluate(pkg)
    }

    /**
     * Only a real app counts as "something came to the front": one with a launcher entry, or a
     * home screen. System overlays (fingerprint dialogs, permission pop-ups, vendor panels) are
     * not, so they can neither lock nor relock anything. Cached per package.
     */
    private fun isRealApp(pkg: String): Boolean = realAppCache.getOrPut(pkg) {
        pkg in homePackages || runCatching { packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(true)
    }

    private fun resolveHomePackages(): Set<String> = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(home, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
    }.getOrDefault(emptySet())

    /** Decides for [pkg] and shows the lock if needed. */
    private fun evaluate(pkg: String) {
        val known = locked
        if (known != null) {
            handle(pkg, pkg in known)
        } else {
            // First events can arrive before the first database read finishes. Failing to read
            // means we cannot tell, so treat the app as protected rather than let it through.
            scope?.launch { handle(pkg, runCatching { protectedApps.isLocked(pkg) }.getOrDefault(true)) }
        }
    }

    private fun handle(pkg: String, isProtected: Boolean) {
        if (gate.onWindowChanged(pkg, isProtected, relockDelay) == ForegroundDecision.ShowLock) showLock(pkg)
    }

    private fun showLock(pkg: String) {
        coordinator.request(pkg)
        try {
            startActivity(
                Intent(this, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        } catch (_: Exception) {
            // A device refused the launch. Fail closed: move the user out of the protected app.
            gate.onLockDismissed()
            coordinator.clear()
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        status.onInterrupted()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun stop() {
        scope?.cancel()
        scope = null
        locked = null
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        if (packageReceiverRegistered) {
            runCatching { unregisterReceiver(packageReceiver) }
            packageReceiverRegistered = false
        }
        gate.reset() // a service that stops forgets every unlock
        coordinator.clear()
        status.onDisconnected()
    }

    /**
     * Windows that are not "an app came to the front": this app (its lock screen), keyboards
     * (resolved at runtime), and the system UI that hosts the notification shade, volume panel
     * and similar. System UI uses the AOSP package name, which OEMs normally keep; this is the
     * one hardcoded package and needs checking on each brand.
     */
    private fun resolveIgnoredPackages(): Set<String> {
        val result = HashSet<String>()
        result += packageName
        result += SYSTEM_UI_PACKAGE
        runCatching {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { result += it.packageName }
        }
        return result
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
