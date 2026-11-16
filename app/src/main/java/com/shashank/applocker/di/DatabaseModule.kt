package com.shashank.applocker.di

import android.content.Context
import androidx.room.Room
import com.shashank.applocker.data.db.ALL_MIGRATIONS
import com.shashank.applocker.data.db.AppDatabase
import com.shashank.applocker.data.db.ProtectedAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideProtectedAppDao(db: AppDatabase): ProtectedAppDao = db.protectedAppDao()
}
