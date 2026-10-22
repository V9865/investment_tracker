package com.personal.investmenttracker.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.personal.investmenttracker.data.security.LockState
import com.personal.investmenttracker.data.security.LockableStorage
import com.personal.investmenttracker.data.security.MasterKeyManager
import com.personal.investmenttracker.data.security.SessionLockManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsEraseDataTest {
    private class FakeLockableStorage : LockableStorage {
        var isLocked: Boolean = false
        var wasReset: Boolean = false

        override fun closeAndLock() {
            isLocked = true
        }

        override fun deleteDatabaseAndReset() {
            wasReset = true
            isLocked = true
        }
    }

    private lateinit var context: Context
    private lateinit var fakeStorage: FakeLockableStorage
    private lateinit var masterKeyManager: MasterKeyManager
    private lateinit var sessionLockManager: SessionLockManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeStorage = FakeLockableStorage()
        masterKeyManager = MasterKeyManager(context)
        sessionLockManager = SessionLockManager(fakeStorage)
    }

    @Test
    fun testLockNowDoesNotEraseData() {
        sessionLockManager.onUserAuthenticated()
        assertEquals(LockState.Unlocked, sessionLockManager.lockState.value)

        sessionLockManager.lockNow()

        assertEquals(LockState.Locked, sessionLockManager.lockState.value)
        assertTrue(fakeStorage.isLocked)
        assertFalse("Lock must NOT wipe database", fakeStorage.wasReset)
    }

    @Test
    fun testEraseWipesDatabaseAndMasterKey() {
        // Create dummy wrapped key file to simulate initialized master key
        val dummyKeyFile = File(context.filesDir, "wrapped_db_key.bin")
        dummyKeyFile.writeBytes(ByteArray(48))
        assertTrue(dummyKeyFile.exists())

        sessionLockManager.onUserAuthenticated()

        // Trigger erase action
        sessionLockManager.resetDataAndResync(masterKeyManager)

        // Verify database was reset
        assertTrue("Erase must reset database", fakeStorage.wasReset)
        assertTrue("Storage must be locked after erase", fakeStorage.isLocked)
        assertEquals(LockState.Locked, sessionLockManager.lockState.value)

        // Verify master key and wrapped key file were wiped
        assertFalse("Wrapped DB key must be deleted", dummyKeyFile.exists())
    }
}
