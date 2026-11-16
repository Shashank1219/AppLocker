package com.shashank.applocker.data.db

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProtectedAppDaoTest : DatabaseTestBase() {
    private val dao get() = db.protectedAppDao()

    @Test
    fun insertAndObserve() = runTest {
        dao.observeSelected().test {
            assertEquals(emptyList<String>(), awaitItem())
            dao.insert(ProtectedAppEntity("a.b"))
            assertEquals(listOf("a.b"), awaitItem())
        }
    }

    @Test
    fun duplicateInsertIsIgnoredAndKeepsFlag() = runTest {
        dao.insert(ProtectedAppEntity("a.b", lockActive = false))
        dao.insert(ProtectedAppEntity("a.b", lockActive = true))
        assertEquals(false, dao.isLockActive("a.b"))
    }

    @Test
    fun lockedFlowExcludesPaused() = runTest {
        dao.insert(ProtectedAppEntity("a"))
        dao.insert(ProtectedAppEntity("b"))
        dao.setLockActive("b", false)
        dao.observeLocked().test { assertEquals(listOf("a"), awaitItem()) }
    }

    @Test
    fun deleteAndUnknownLookup() = runTest {
        dao.insert(ProtectedAppEntity("a"))
        dao.delete("a")
        assertNull(dao.isLockActive("a"))
    }
}
