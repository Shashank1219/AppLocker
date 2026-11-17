package com.shashank.applocker.data.repository

import app.cash.turbine.test
import com.shashank.applocker.data.db.DatabaseTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProtectedAppRepositoryTest : DatabaseTestBase() {
    private val repo get() = ProtectedAppRepositoryImpl(db)

    @Test
    fun selectStartsLockedAndUnselectRemoves() = runTest {
        repo.setSelected("a", true)
        assertTrue(repo.isLocked("a"))
        repo.setSelected("a", false)
        assertFalse(repo.isLocked("a"))
        repo.observeSelectedPackages().test { assertEquals(emptySet<String>(), awaitItem()) }
    }

    @Test
    fun pauseLockKeepsSelection() = runTest {
        repo.setSelected("a", true)
        repo.setLockActive("a", false)
        assertFalse(repo.isLocked("a"))
        repo.observeSelectedPackages().test { assertEquals(setOf("a"), awaitItem()) }
        repo.observeLockedPackages().test { assertEquals(emptySet<String>(), awaitItem()) }
    }

    @Test
    fun setLockActiveOnUnselectedIsNoOp() = runTest {
        repo.setLockActive("ghost", true)
        assertFalse(repo.isLocked("ghost"))
    }

    @Test
    fun reconcileDropsUninstalled() = runTest {
        repo.setSelected("a", true)
        repo.setSelected("b", true)
        repo.setSelected("c", true)
        val removed = repo.reconcile(setOf("a", "c", "other"))
        assertEquals(setOf("b"), removed)
        repo.observeSelectedPackages().test { assertEquals(setOf("a", "c"), awaitItem()) }
    }

    @Test
    fun reconcileHandlesManyStaleEntries() = runTest {
        repeat(1200) { repo.setSelected("p.$it", true) }
        val removed = repo.reconcile(emptySet())
        assertEquals(1200, removed.size)
        repo.observeSelectedPackages().test { assertEquals(emptySet<String>(), awaitItem()) }
    }

    @Test
    fun selectingTwiceKeepsOneRowAndUnselectingUnknownIsHarmless() = runTest {
        repo.setSelected("a", true)
        repo.setSelected("a", true)
        repo.setSelected("never-added", false)
        repo.observeSelectedPackages().test { assertEquals(setOf("a"), awaitItem()) }
    }

    @Test
    fun clearAllRemovesEverySelection() = runTest {
        repo.setSelected("a", true)
        repo.setSelected("b", true)
        repo.clearAll()
        repo.observeSelectedPackages().test { assertEquals(emptySet<String>(), awaitItem()) }
    }

    @Test
    fun reconcileKeepsInstalledAndDropsMissing() = runTest {
        repo.setSelected("kept", true)
        repo.setSelected("gone", true)
        assertEquals(setOf("gone"), repo.reconcile(setOf("kept", "other")))
        repo.observeSelectedPackages().test { assertEquals(setOf("kept"), awaitItem()) }
    }
}
