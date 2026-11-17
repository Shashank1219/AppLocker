package com.shashank.applocker.di

import com.shashank.applocker.data.intruder.AndroidCameraPermissionChecker
import com.shashank.applocker.data.intruder.CameraXIntruderCamera
import com.shashank.applocker.data.intruder.MediaStoreIntruderPhotoSink
import com.shashank.applocker.data.intruder.PackageManagerFrontCamera
import com.shashank.applocker.service.CameraPermissionChecker
import com.shashank.applocker.service.FrontCameraAvailability
import com.shashank.applocker.service.IntruderCamera
import com.shashank.applocker.service.IntruderPhotoSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class IntruderModule {
    @Binds abstract fun bindPermission(impl: AndroidCameraPermissionChecker): CameraPermissionChecker
    @Binds abstract fun bindFrontCamera(impl: PackageManagerFrontCamera): FrontCameraAvailability
    @Binds abstract fun bindCamera(impl: CameraXIntruderCamera): IntruderCamera
    @Binds abstract fun bindSink(impl: MediaStoreIntruderPhotoSink): IntruderPhotoSink
}
