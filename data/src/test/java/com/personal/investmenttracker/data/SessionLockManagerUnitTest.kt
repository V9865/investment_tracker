package com.personal.investmenttracker.data

import com.personal.investmenttracker.data.security.LockState
import com.personal.investmenttracker.data.security.LockableStorage
import com.personal.investmenttracker.data.security.SessionLockManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeLockableStorage : LockableStorage {
    var isLocked: Boolean = true
    var wasReset: Boolean = false

    override fun closeAndLock() {
        isLocked = true
    }

    override fun deleteDatabaseAndReset() {
        isLocked = true
        wasReset = true
    }
}

class SessionLockManagerUnitTest {
    private lateinit var fakeStorage: FakeLockableStorage

    @Before
    fun setUp() {
        fakeStorage = FakeLockableStorage()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testSessionRelockOnBackgroundAndTimeout() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val testScope = TestScope(testDispatcher)
            val lockManager =
                SessionLockManager(
                    lockableStorage = fakeStorage,
                    scope = testScope,
                    lockTimeoutMillis = 1000L,
                )

            fakeStorage.isLocked = false

            // Unlock session
            lockManager.onUserAuthenticated()
            assertEquals(LockState.Unlocked, lockManager.lockState.value)

            // Advance time partially -> still unlocked
            testScheduler.advanceTimeBy(500L)
            assertEquals(LockState.Unlocked, lockManager.lockState.value)

            // Advance past timeout (1000ms total) -> relocks automatically
            testScheduler.advanceTimeBy(600L)
            assertEquals(LockState.Locked, lockManager.lockState.value)
            assertTrue(fakeStorage.isLocked)

            // Re-authenticate and test backgrounding
            fakeStorage.isLocked = false
            lockManager.onUserAuthenticated()
            assertEquals(LockState.Unlocked, lockManager.lockState.value)

            lockManager.onAppBackgrounded()
            assertEquals(LockState.Locked, lockManager.lockState.value)
            assertTrue(fakeStorage.isLocked)
        }

    @Test
    fun testInvalidationFailsClosed() {
        val lockManager = SessionLockManager(fakeStorage)
        fakeStorage.isLocked = false

        lockManager.onUserAuthenticated()
        assertEquals(LockState.Unlocked, lockManager.lockState.value)

        // Trigger biometric enrollment change invalidation
        lockManager.onKeyPermanentlyInvalidated("New biometric enrolled")
        assertTrue(lockManager.lockState.value is LockState.PermanentlyInvalidated)
        assertTrue(fakeStorage.isLocked)

        // Re-locking doesn't revive invalidated state; it remains fail-closed
        lockManager.lockNow()
        assertTrue(lockManager.lockState.value is LockState.PermanentlyInvalidated)
    }

    @Test
    fun testFlagSecureToggle() {
        val lockManager = SessionLockManager(fakeStorage)
        assertTrue(lockManager.isFlagSecureEnabled)

        lockManager.setFlagSecure(false)
        assertFalse(lockManager.isFlagSecureEnabled)

        lockManager.setFlagSecure(true)
        assertTrue(lockManager.isFlagSecureEnabled)
    }
}
