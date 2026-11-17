package com.shashank.applocker.domain.model

/** Non-sensitive user settings. Holds no secrets. */
data class AppSettings(
    val fingerprintEnabled: Boolean = false,
    val relockDelay: RelockDelay = RelockDelay.DEFAULT,
    /** Whether the app picker also lists system apps. Off by default. */
    val showSystemApps: Boolean = false,
)
