package com.shashank.applocker.domain.repository

import com.shashank.applocker.domain.model.AppSettings
import com.shashank.applocker.domain.model.RelockDelay
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setFingerprintEnabled(enabled: Boolean)
    suspend fun setRelockDelay(delay: RelockDelay)
    suspend fun setAccessibilityServiceEnabled(enabled: Boolean)
}
