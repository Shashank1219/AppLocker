package com.shashank.applocker.domain.repository

import com.shashank.applocker.domain.model.AppSettings
import com.shashank.applocker.domain.model.RelockDelay
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setFingerprintEnabled(enabled: Boolean)
    suspend fun setRelockDelay(delay: RelockDelay)
    suspend fun setShowSystemApps(show: Boolean)
    suspend fun setIntruderSelfieEnabled(enabled: Boolean)
    suspend fun setIntruderAfterAttempts(attempts: Int)

    /** Resets every setting to its default. Used when wiping app data. */
    suspend fun clear()
}
