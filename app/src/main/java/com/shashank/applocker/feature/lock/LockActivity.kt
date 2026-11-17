package com.shashank.applocker.feature.lock

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.shashank.applocker.feature.pin.PinUnlockScreen
import com.shashank.applocker.security.UnlockSessionManager
import com.shashank.applocker.service.LockCoordinator
import com.shashank.applocker.ui.theme.AppLockerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The lock screen the accessibility service shows over a protected app. It reuses the PIN unlock
 * screen, which offers fingerprint only when enabled and available.
 *
 * - Success: the target app is marked unlocked and this task is removed, revealing the app.
 * - Back (or a device-credential recovery that is dismissed): the service sends the user Home.
 * - Recents: excluded in the manifest, and FLAG_SECURE hides it from screenshots and the switcher.
 * - If the process died, the target is gone: this closes without unlocking anything.
 *
 * Extends [FragmentActivity] because BiometricPrompt requires it.
 */
@AndroidEntryPoint
class LockActivity : FragmentActivity() {
    @Inject lateinit var coordinator: LockCoordinator
    @Inject lateinit var sessions: UnlockSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        if (coordinator.target.value == null) {
            // Restored after process death: nothing to unlock, and nothing is revealed.
            finishAndRemoveTask()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        setContent {
            AppLockerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    PinUnlockScreen(
                        onUnlocked = ::unlocked,
                        // A confirmed device screen lock also proves ownership. The PIN itself can be
                        // changed from the main app.
                        onRecoveryAuthorized = ::unlocked,
                        modifier = Modifier.padding(inner),
                    )
                }
            }
        }
        // Ignore touches while another window (an overlay) covers this one.
        findViewById<View>(android.R.id.content).filterTouchesWhenObscured = true
    }

    private fun unlocked() {
        val target = coordinator.target.value
        if (target == null) {
            leave()
            return
        }
        sessions.onUnlocked(target)
        coordinator.clear()
        finishAndRemoveTask()
    }

    private fun leave() {
        coordinator.cancel()
        coordinator.clear()
        finishAndRemoveTask()
    }
}
