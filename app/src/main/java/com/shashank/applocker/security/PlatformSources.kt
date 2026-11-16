package com.shashank.applocker.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Time source for lockout. Never the wall clock. */
interface ElapsedClock {
    /** Milliseconds since boot, including deep sleep. Goes back to ~0 on reboot. */
    fun elapsedRealtimeMs(): Long

    /** Number of boots recorded by the system, or -1 if the device does not expose it. */
    fun bootCount(): Int
}

@Singleton
class SystemElapsedClock @Inject constructor(
    @ApplicationContext private val context: Context,
) : ElapsedClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (e: RuntimeException) {
        -1
    }
}

/** Whether the device has a PIN, pattern or password screen lock to use as recovery. */
interface DeviceCredentialAvailability {
    fun isAvailable(): Boolean
}

@Singleton
class KeyguardCredentialAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceCredentialAvailability {
    override fun isAvailable(): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true
}
