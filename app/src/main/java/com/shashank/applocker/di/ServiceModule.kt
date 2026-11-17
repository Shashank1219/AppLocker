package com.shashank.applocker.di

import com.shashank.applocker.service.AccessibilityStatusSource
import com.shashank.applocker.service.AndroidAccessibilityStatusSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds abstract fun bindAccessibilityStatus(impl: AndroidAccessibilityStatusSource): AccessibilityStatusSource
}
