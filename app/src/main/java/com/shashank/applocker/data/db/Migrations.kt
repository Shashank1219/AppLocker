package com.shashank.applocker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 -> 2: lockout state moved out of Room into Keystore-encrypted storage, so the table is
 * dropped. Protected-app selections are untouched.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS lockout_state")
    }
}

/**
 * Explicit migrations. Never use fallbackToDestructiveMigration: an update must keep the
 * user's selections. Add each new Migration here and cover it with a migration test.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
