package com.shashank.applocker.security

import com.shashank.applocker.domain.model.AppSettings
import com.shashank.applocker.domain.model.RelockDelay
import com.shashank.applocker.feature.apps.FakeProtectedApps
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataWiperTest {
    private val sessions = UnlockSessionManager(FakeClock())
    private val keys = FakeBiometricKeyManager()
    private val secrets = FakeSecretStore()
    private val apps = FakeProtectedApps(setOf("a", "b"))
    private val settings = FakeSettingsRepository(AppSettings(fingerprintEnabled = true, relockDelay = RelockDelay.AFTER_5_MINUTES, showSystemApps = true))
    private val wiper = DefaultAppDataWiper(sessions, keys, secrets, apps, settings)

    @Test fun wipeErasesEverything() = runTest {
        secrets.put("pin", byteArrayOf(1))
        sessions.onForeground("a"); sessions.onUnlocked("a")
        assertEquals(WipeResult.SUCCESS, wiper.wipe())
        assertTrue(secrets.data.isEmpty())
        assertEquals(1, keys.disableCalls)
        assertTrue(apps.selected.value.isEmpty())
        assertEquals(AppSettings(), settings.state.value)
        assertTrue(sessions.requiresUnlock("a", RelockDelay.ON_SCREEN_OFF))
    }

    @Test fun aFailingStepDoesNotStopTheOthersAndIsReported() = runTest {
        secrets.put("pin", byteArrayOf(1))
        secrets.failing = true
        assertEquals(WipeResult.INCOMPLETE, wiper.wipe())
        assertEquals(1, keys.disableCalls)
        assertTrue(apps.selected.value.isEmpty())
        assertEquals(AppSettings(), settings.state.value)
        assertFalse(secrets.data.isEmpty()) // the one thing that failed is still there, and was reported
    }
}
