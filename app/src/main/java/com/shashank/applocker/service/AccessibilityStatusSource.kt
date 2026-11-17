package com.shashank.applocker.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the user has switched this app's accessibility service on in system settings. */
interface AccessibilityStatusSource {
    fun isServiceEnabled(): Boolean
}

@Singleton
class AndroidAccessibilityStatusSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : AccessibilityStatusSource {
    override fun isServiceEnabled(): Boolean = try {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty().any {
            val info = it.resolveInfo?.serviceInfo
            info?.packageName == context.packageName && info.name == AppLockerAccessibilityService::class.java.name
        }
    } catch (_: Exception) {
        false
    }
}
