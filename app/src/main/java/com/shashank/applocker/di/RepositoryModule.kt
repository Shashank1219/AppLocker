package com.shashank.applocker.di

import com.shashank.applocker.data.repository.ProtectedAppRepositoryImpl
import com.shashank.applocker.data.settings.SettingsRepositoryImpl
import com.shashank.applocker.domain.repository.ProtectedAppRepository
import com.shashank.applocker.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindProtectedApps(impl: ProtectedAppRepositoryImpl): ProtectedAppRepository
    @Binds abstract fun bindSettings(impl: SettingsRepositoryImpl): SettingsRepository
}
