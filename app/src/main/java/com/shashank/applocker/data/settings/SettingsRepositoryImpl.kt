package com.shashank.applocker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.shashank.applocker.domain.intruder.IntruderThreshold
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
                fingerprintEnabled = p[FINGERPRINT_ENABLED] ?: false,
                relockDelay = RelockDelay.fromName(p[RELOCK_DELAY]),
                showSystemApps = p[SHOW_SYSTEM_APPS] ?: false,
                intruderSelfieEnabled = p[INTRUDER_SELFIE] ?: false,
                intruderAfterAttempts = IntruderThreshold.normalize(p[INTRUDER_AFTER]),
            )
        }

    override suspend fun setFingerprintEnabled(enabled: Boolean) {
        dataStore.edit { it[FINGERPRINT_ENABLED] = enabled }
    }

    override suspend fun setRelockDelay(delay: RelockDelay) {
        dataStore.edit { it[RELOCK_DELAY] = delay.name }
    }

    override suspend fun setShowSystemApps(show: Boolean) {
        dataStore.edit { it[SHOW_SYSTEM_APPS] = show }
    }

    override suspend fun setIntruderSelfieEnabled(enabled: Boolean) {
        dataStore.edit { it[INTRUDER_SELFIE] = enabled }
    }

    override suspend fun setIntruderAfterAttempts(attempts: Int) {
        dataStore.edit { it[INTRUDER_AFTER] = IntruderThreshold.normalize(attempts) }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val FINGERPRINT_ENABLED = booleanPreferencesKey("fingerprint_enabled")
        val RELOCK_DELAY = stringPreferencesKey("relock_delay")
        val INTRUDER_AFTER = intPreferencesKey("intruder_after_attempts")
        val INTRUDER_SELFIE = booleanPreferencesKey("intruder_selfie")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
    }
}
