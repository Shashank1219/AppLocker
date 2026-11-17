package com.shashank.applocker.security

import com.shashank.applocker.domain.repository.ProtectedAppRepository
import com.shashank.applocker.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

enum class WipeResult { SUCCESS, INCOMPLETE }

/** Erases everything this app stores. The caller must already have confirmed with the PIN. */
interface AppDataWiper {
    suspend fun wipe(): WipeResult
}

/**
 * Erases, in this order: unlocks, the fingerprint key and token, every secret (PIN verifier,
 * lockout state) and the Keystore key that protected them, protected-app selections, then
 * settings. Every step is attempted even if an earlier one fails, so a partial failure cannot
 * leave more behind than necessary; the result says whether anything was missed.
 */
@Singleton
class DefaultAppDataWiper @Inject constructor(
    private val sessions: UnlockSessionManager,
    private val keys: BiometricKeyManager,
    private val secrets: SecretStore,
    private val protectedApps: ProtectedAppRepository,
    private val settings: SettingsRepository,
) : AppDataWiper {
    override suspend fun wipe(): WipeResult {
        var complete = true
        suspend fun step(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                complete = false
            }
        }
        step { sessions.lockAll() }
        step { keys.disable() }
        step { secrets.clear() }
        step { protectedApps.clearAll() }
        step { settings.clear() }
        return if (complete) WipeResult.SUCCESS else WipeResult.INCOMPLETE
    }
}
