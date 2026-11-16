package com.shashank.applocker.domain.model

/** Non-sensitive user settings. Holds no secrets. */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val fingerprintEnabled: Boolean = false,
    val relockDelay: RelockDelay = RelockDelay.DEFAULT,
    /** Last known preference only. The real state always comes from AccessibilityManager. */
    val accessibilityServiceEnabled: Boolean = false,
)
