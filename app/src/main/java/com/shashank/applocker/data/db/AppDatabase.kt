package com.shashank.applocker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProtectedAppEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun protectedAppDao(): ProtectedAppDao

    companion object {
        const val NAME = "applocker.db"
    }
}
