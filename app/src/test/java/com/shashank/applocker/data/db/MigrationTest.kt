package com.shashank.applocker.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Builds a version-1 database by hand (same DDL as schemas/.../1.json), opens it with the
 * current Room database and the real migrations, and checks that selections survive.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @Test
    fun migrate1To2KeepsSelectionsAndDropsLockoutTable() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { v1 ->
            v1.execSQL("CREATE TABLE IF NOT EXISTS protected_apps (packageName TEXT NOT NULL, lockActive INTEGER NOT NULL, PRIMARY KEY(packageName))")
            v1.execSQL("CREATE TABLE IF NOT EXISTS lockout_state (id INTEGER NOT NULL, failedAttempts INTEGER NOT NULL, lockedUntilElapsedMs INTEGER NOT NULL, bootCount INTEGER NOT NULL, requiresDeviceCredential INTEGER NOT NULL, PRIMARY KEY(id))")
            v1.execSQL("INSERT INTO protected_apps VALUES ('a.b', 1), ('c.d', 0)")
            v1.execSQL("INSERT INTO lockout_state VALUES (1, 3, 100, 1, 0)")
            v1.version = 1
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.protectedAppDao()
            assertEquals(setOf("a.b", "c.d"), dao.getSelected().toSet())
            assertEquals(false, dao.isLockActive("c.d"))
            val cursor = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE name = 'lockout_state'")
            cursor.use { assertEquals(0, it.count) }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
