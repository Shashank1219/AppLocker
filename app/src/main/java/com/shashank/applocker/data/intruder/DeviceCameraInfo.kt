package com.shashank.applocker.data.intruder

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.shashank.applocker.domain.intruder.IntruderPermissions
import com.shashank.applocker.service.CameraPermissionChecker
import com.shashank.applocker.service.FrontCameraAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCameraPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraPermissionChecker {
    /** Re-checked every time: the user or Android's auto-reset can revoke it later. */
    override fun isGranted(): Boolean = IntruderPermissions.required(Build.VERSION.SDK_INT).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

@Singleton
class PackageManagerFrontCamera @Inject constructor(
    @ApplicationContext private val context: Context,
) : FrontCameraAvailability {
    override fun hasFrontCamera(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
}
