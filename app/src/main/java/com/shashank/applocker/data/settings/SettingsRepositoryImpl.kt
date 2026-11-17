package com.shashank.applocker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shashank.applocker.domain.model.AppSettings
import com.shashank.applocker.domain.model.RelockDelay
import com.shashank.applocker.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Non-sensitive settings in DataStore. Never put a PIN, key or token here. */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                onboardingCompleted = p[ONBOARDING_COMPLETED] ?: false,
                fingerprintEnabled = p[FINGERPRINT_ENABLED] ?: false,
                relockDelay = RelockDelay.fromName(p[RELOCK_DELAY]),
                accessibilityServiceEnabled = p[ACCESSIBILITY_ENABLED] ?: false,
                showSystemApps = p[SHOW_SYSTEM_APPS] ?: false,
            )
        }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setFingerprintEnabled(enabled: Boolean) {
        dataStore.edit { it[FINGERPRINT_ENABLED] = enabled }
    }

    override suspend fun setRelockDelay(delay: RelockDelay) {
        dataStore.edit { it[RELOCK_DELAY] = delay.name }
    }

    override suspend fun setAccessibilityServiceEnabled(enabled: Boolean) {
        dataStore.edit { it[ACCESSIBILITY_ENABLED] = enabled }
    }

    override suspend fun setShowSystemApps(show: Boolean) {
        dataStore.edit { it[SHOW_SYSTEM_APPS] = show }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val FINGERPRINT_ENABLED = booleanPreferencesKey("fingerprint_enabled")
        val RELOCK_DELAY = stringPreferencesKey("relock_delay")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_service_enabled")
    }
}
