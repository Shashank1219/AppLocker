package com.shashank.applocker.domain.intruder

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Decides whether a wrong PIN should trigger a photo. Pure, so it can be unit tested. */
object IntruderCapturePolicy {
    fun shouldCapture(
        enabled: Boolean,
        permissionGranted: Boolean,
        hasFrontCamera: Boolean,
        captureInProgress: Boolean,
        /** Consecutive wrong PINs so far, including this one. */
        failedAttempts: Int,
        /** Take photos from this many wrong PINs onward. */
        threshold: Int,
    ): Boolean = enabled && permissionGranted && hasFrontCamera && !captureInProgress && failedAttempts >= threshold
}

/** How many consecutive wrong PINs it takes before photos start. */
object IntruderThreshold {
    val options = listOf(1, 2, 3, 5)
    const val DEFAULT = 3

    /** Unknown or missing values fall back to the default. */
    fun normalize(value: Int?): Int = if (value != null && value in options) value else DEFAULT
}

/** File names for intruder photos: `Intruder-yyyyMMdd-HHmmss.jpg` in local time. */
object IntruderPhotoName {
    private val format = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun format(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "Intruder-" + format.format(Instant.ofEpochMilli(epochMillis).atZone(zone)) + ".jpg"

    /**
     * Returns [base] if it is free, otherwise `Intruder-yyyyMMdd-HHmmss-1.jpg`, `-2.jpg` and so on.
     * [exists] reports whether a name is already taken.
     */
    fun unique(base: String, exists: (String) -> Boolean): String {
        if (!exists(base)) return base
        val stem = base.removeSuffix(".jpg")
        var counter = 1
        while (exists("$stem-$counter.jpg")) counter++
        return "$stem-$counter.jpg"
    }
}

/** Runtime permissions the selfie needs. Storage is only needed on Android 8 and 9 (API 26-28). */
object IntruderPermissions {
    const val CAMERA = "android.permission.CAMERA"
    const val WRITE_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"

    fun required(sdkInt: Int): List<String> = if (sdkInt <= 28) listOf(CAMERA, WRITE_STORAGE) else listOf(CAMERA)
}
