package com.shashank.applocker.ui.navigation

/** Navigation destinations. Stage 1 placeholders only. */
sealed class Route(val path: String) {
    data object Onboarding : Route("onboarding")
    data object PinSetup : Route("pin_setup")
    data object PinUnlock : Route("pin_unlock")
    data object BiometricUnlock : Route("biometric_unlock")
    data object AppSelection : Route("app_selection")
    data object Settings : Route("settings")
    data object Protection : Route("protection")
    data object AppLock : Route("app_lock")

    companion object {
        val all: List<Route> = listOf(
            Onboarding, PinSetup, PinUnlock, BiometricUnlock, AppSelection, Settings, Protection, AppLock
        )
    }
}
