package com.personal.investmenttracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.investmenttracker.data.local.entity.ListingEntity
import com.personal.investmenttracker.data.security.DatabaseEncryptionException
import com.personal.investmenttracker.data.security.EncryptedDatabaseManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class EncryptedStorageDeviceSecurityTest {
    private lateinit var context: Context
    private lateinit var dbManager: EncryptedDatabaseManager
    private val testDbName = "device_test_security_encrypted.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbManager = EncryptedDatabaseManager(context, testDbName)
        dbManager.deleteDatabaseAndReset()
    }

    @After
    fun tearDown() {
        dbManager.deleteDatabaseAndReset()
    }

    @Test
    fun testDatabaseCreationAndWrongKeyCannotOpen() {
        val correctKey = ByteArray(32)
        val wrongKey = ByteArray(32)
        SecureRandom().nextBytes(correctKey)
        SecureRandom().nextBytes(wrongKey)

        // 1. Open and write data with correct key
        val db = dbManager.openDatabase(correctKey)
        assertTrue(dbManager.isDatabaseOpen())

        val listingId =
            db.listingDao().insert(
                ListingEntity(
                    isin = "INE002A01018",
                    exchange = "NSE",
                    symbol = "RELIANCE",
                    scripCode = null,
                    brokerRef = null,
                ),
            )
        assertTrue(listingId > 0)

        dbManager.closeAndLock()
        assertFalse(dbManager.isDatabaseOpen())

        // 2. Attempt to open with wrong key -> must fail closed with exception
        val wrongManager = EncryptedDatabaseManager(context, testDbName)
        try {
            wrongManager.openDatabase(wrongKey)
            fail("Opening database with wrong key should throw DatabaseEncryptionException")
        } catch (_: DatabaseEncryptionException) {
            // Expected
        }

        // 3. Confirm reopening with correct key succeeds
        val reopenManager = EncryptedDatabaseManager(context, testDbName)
        val dbReopened = reopenManager.openDatabase(correctKey)
        val listing = dbReopened.listingDao().getByExchangeAndSymbol("NSE", "RELIANCE")
        assertNotNull(listing)
        assertEquals("INE002A01018", listing?.isin)

        reopenManager.closeAndLock()
    }

    @Test
    fun testDatabaseFileContainsNoPlaintextMarkers() {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        val sensitiveSymbol = "SECRET_INFY_SYMBOL"
        val sensitiveIsin = "INE009A01021_SENSITIVE"

        val db = dbManager.openDatabase(key)
        db.listingDao().insert(
            ListingEntity(
                isin = sensitiveIsin,
                exchange = "NSE",
                symbol = sensitiveSymbol,
                scripCode = null,
                brokerRef = null,
            ),
        )
        dbManager.closeAndLock()

        val dbFile = dbManager.getDatabaseFile()
        assertTrue(dbFile.exists())
        val fileBytes = dbFile.readBytes()

        // SQLCipher header is encrypted/random salt; standard SQLite header "SQLite format 3" must NOT be present
        val sqliteHeader = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        assertFalse(
            "Raw SQLite header must not exist in encrypted database file",
            containsBytes(fileBytes, sqliteHeader),
        )

        // Sensitive plaintexts must not exist in the raw database file bytes
        val symbolBytes = sensitiveSymbol.toByteArray(Charsets.UTF_8)
        val isinBytes = sensitiveIsin.toByteArray(Charsets.UTF_8)

        assertFalse(
            "Plaintext symbol must not exist in encrypted file",
            containsBytes(fileBytes, symbolBytes),
        )
        assertFalse(
            "Plaintext ISIN must not exist in encrypted file",
            containsBytes(fileBytes, isinBytes),
        )
    }

    private fun containsBytes(
        source: ByteArray,
        target: ByteArray,
    ): Boolean {
        if (target.isEmpty() || source.size < target.size) return false
        for (i in 0..source.size - target.size) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return true
        }
        return false
    }
}
