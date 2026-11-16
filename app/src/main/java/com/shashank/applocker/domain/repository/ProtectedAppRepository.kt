package com.shashank.applocker.domain.repository

import kotlinx.coroutines.flow.Flow

interface ProtectedAppRepository {
    /** Package names the user selected for protection (locked or paused). */
    fun observeSelectedPackages(): Flow<Set<String>>

    /** Selected packages whose lock is currently active. */
    fun observeLockedPackages(): Flow<Set<String>>

    suspend fun isLocked(packageName: String): Boolean

    /** Adds or removes [packageName] from the selection. New entries start locked. */
    suspend fun setSelected(packageName: String, selected: Boolean)

    /** Pauses or resumes the lock for an already selected package. No-op if not selected. */
    suspend fun setLockActive(packageName: String, active: Boolean)

    /** Drops selected packages that are not in [installedPackages]. Returns the removed names. */
    suspend fun reconcile(installedPackages: Set<String>): Set<String>
}
