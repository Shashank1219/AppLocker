package com.shashank.applocker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.shashank.applocker.domain.model.AppSettings
import com.shashank.applocker.domain.model.RelockDelay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * In-memory DataStore. The real file-backed one fails its atomic rename on Windows hosts when
 * the file is written repeatedly in a test, so the repository logic is tested against this.
 */
private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        return state.updateAndGet { next }
    }
}

class SettingsRepositoryTest {
    private val store = FakePreferencesDataStore()
    private val repo = SettingsRepositoryImpl(store)

    @Test
    fun defaultsAreSafe() = runTest {
        assertEquals(AppSettings(), repo.settings.first())
    }

    @Test
    fun settersPersistAndEmit() = runTest {
        repo.settings.test {
            assertEquals(AppSettings(), awaitItem())
            repo.setOnboardingCompleted(true)
            assertEquals(true, awaitItem().onboardingCompleted)
            repo.setFingerprintEnabled(true)
            assertEquals(true, awaitItem().fingerprintEnabled)
            repo.setRelockDelay(RelockDelay.AFTER_5_MINUTES)
            assertEquals(RelockDelay.AFTER_5_MINUTES, awaitItem().relockDelay)
            repo.setAccessibilityServiceEnabled(true)
            assertEquals(AppSettings(true, true, RelockDelay.AFTER_5_MINUTES, true), awaitItem())
        }
    }

    @Test
    fun settingsCanBeTurnedBackOff() = runTest {
        repo.setFingerprintEnabled(true)
        repo.setFingerprintEnabled(false)
        assertEquals(false, repo.settings.first().fingerprintEnabled)
    }

    @Test
    fun unknownRelockValueFallsBackToImmediately() = runTest {
        store.edit { it[stringPreferencesKey("relock_delay")] = "garbage" }
        assertEquals(RelockDelay.IMMEDIATELY, repo.settings.first().relockDelay)
    }

    @Test
    fun relockDelayFromNameAndSeconds() {
        assertEquals(RelockDelay.IMMEDIATELY, RelockDelay.fromName(null))
        assertEquals(30L, RelockDelay.AFTER_30_SECONDS.seconds)
        assertEquals(null, RelockDelay.ON_SCREEN_OFF.seconds)
    }

    @Test
    fun showSystemAppsDefaultsOffAndPersists() = runTest {
        assertEquals(false, repo.settings.first().showSystemApps)
        repo.setShowSystemApps(true)
        assertEquals(true, repo.settings.first().showSystemApps)
    }
}
