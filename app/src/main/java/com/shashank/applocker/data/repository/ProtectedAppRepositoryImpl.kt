package com.shashank.applocker.data.repository

import androidx.room.withTransaction
import com.shashank.applocker.data.db.AppDatabase
import com.shashank.applocker.data.db.ProtectedAppEntity
import com.shashank.applocker.domain.repository.ProtectedAppRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ProtectedAppRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
) : ProtectedAppRepository {
    private val dao = db.protectedAppDao()

    override fun observeSelectedPackages(): Flow<Set<String>> = dao.observeSelected().map { it.toSet() }

    override fun observeLockedPackages(): Flow<Set<String>> = dao.observeLocked().map { it.toSet() }

    override suspend fun isLocked(packageName: String): Boolean = dao.isLockActive(packageName) == true

    override suspend fun setSelected(packageName: String, selected: Boolean) {
        if (selected) dao.insert(ProtectedAppEntity(packageName)) else dao.delete(packageName)
    }

    override suspend fun setLockActive(packageName: String, active: Boolean) =
        dao.setLockActive(packageName, active)

    override suspend fun clearAll() = dao.deleteEverything()

    override suspend fun reconcile(installedPackages: Set<String>): Set<String> =
        db.withTransaction {
            val stale = dao.getSelected().filterNot { it in installedPackages }
            // Chunked to stay under SQLite's bound-variable limit.
            stale.chunked(500).forEach { dao.deleteAll(it) }
            stale.toSet()
        }
}
