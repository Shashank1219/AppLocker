package com.shashank.applocker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedAppDao {
    @Query("SELECT packageName FROM protected_apps")
    fun observeSelected(): Flow<List<String>>

    @Query("SELECT packageName FROM protected_apps WHERE lockActive = 1")
    fun observeLocked(): Flow<List<String>>

    @Query("SELECT packageName FROM protected_apps")
    suspend fun getSelected(): List<String>

    @Query("SELECT lockActive FROM protected_apps WHERE packageName = :packageName")
    suspend fun isLockActive(packageName: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProtectedAppEntity): Long

    @Query("DELETE FROM protected_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("UPDATE protected_apps SET lockActive = :active WHERE packageName = :packageName")
    suspend fun setLockActive(packageName: String, active: Boolean)

    @Query("DELETE FROM protected_apps WHERE packageName IN (:packageNames)")
    suspend fun deleteAll(packageNames: List<String>)
}
