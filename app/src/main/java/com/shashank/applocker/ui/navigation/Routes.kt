package com.shashank.applocker.ui.navigation

/** Navigation destinations inside the main app. The app-lock screen is a separate Activity. */
sealed class Route(val path: String) {
    data object PinSetup : Route("pin_setup")
    data object PinUnlock : Route("pin_unlock")
    data object ChangePin : Route("change_pin")
    data object AppSelection : Route("app_selection")
    data object Settings : Route("settings")
    data object Protection : Route("protection")
}
