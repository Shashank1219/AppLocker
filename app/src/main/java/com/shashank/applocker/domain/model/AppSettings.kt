package com.shashank.applocker.domain.model

/** Non-sensitive user settings. Holds no secrets. */
data class AppSettings(
    val fingerprintEnabled: Boolean = false,
    val relockDelay: RelockDelay = RelockDelay.DEFAULT,
    /** Whether the app picker also lists system apps. Off by default. */
    val showSystemApps: Boolean = false,
    /** Intruder selfie after a wrong PIN on the lock screen. Off by default. */
    val intruderSelfieEnabled: Boolean = false,
    /** Photos start from this many consecutive wrong PINs. */
    val intruderAfterAttempts: Int = com.shashank.applocker.domain.intruder.IntruderThreshold.DEFAULT,
)
