package com.shashank.applocker.domain.model

/** How long after leaving a protected app before it must be unlocked again. */
enum class RelockDelay(val seconds: Long?) {
    IMMEDIATELY(0),
    AFTER_30_SECONDS(30),
    AFTER_1_MINUTE(60),
    AFTER_5_MINUTES(300),

    /** No timer; relock only when the screen turns off. */
    ON_SCREEN_OFF(null);

    companion object {
        val DEFAULT = IMMEDIATELY

        /** Unknown or missing values fall back to the safest default. */
        fun fromName(name: String?): RelockDelay = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
