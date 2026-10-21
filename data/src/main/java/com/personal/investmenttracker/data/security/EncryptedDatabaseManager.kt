package com.personal.investmenttracker.data.security

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import com.personal.investmenttracker.data.local.AppDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File
import java.io.IOException
import java.util.Arrays

class DatabaseEncryptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class EncryptedDatabaseManager(
    private val context: Context,
    private val databaseName: String = AppDatabase.DATABASE_NAME
) : LockableStorage {
    @Volatile
    private var activeDatabase: AppDatabase? = null

    @Volatile
    private var activePassphrase: ByteArray? = null

    fun isDatabaseOpen(): Boolean = activeDatabase?.isOpen == true

    fun openDatabase(passphrase: ByteArray): AppDatabase {
        synchronized(this) {
            val existing = activeDatabase
            if (existing != null && existing.isOpen) {
                return existing
            }

            try {
                // Initialize SQLCipher native libraries
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context)

                val supportFactory = SupportFactory(passphrase.clone())
                val db =
                    Room.databaseBuilder(
                        context,
                        AppDatabase::class.java,
                        databaseName
                    ).openHelperFactory(supportFactory)
                        .fallbackToDestructiveMigration()
                        .build()

                // Trigger a test read to verify key correctness immediately
                db.openHelper.writableDatabase

                activeDatabase = db
                activePassphrase = passphrase.clone()
                return db
            } catch (e: SQLiteException) {
                throw DatabaseEncryptionException(
                    "Failed to open encrypted database with provided key",
                    e
                )
            } catch (e: IllegalStateException) {
                throw DatabaseEncryptionException(
                    "Database configuration or state error",
                    e
                )
            }
        }
    }

    override fun closeAndLock() {
        synchronized(this) {
            try {
                activeDatabase?.close()
            } catch (_: IOException) {
            } catch (_: SQLiteException) {
            } finally {
                activeDatabase = null
                activePassphrase?.let {
                    Arrays.fill(it, 0.toByte())
                }
                activePassphrase = null
            }
        }
    }

    override fun deleteDatabaseAndReset() {
        closeAndLock()
        val dbFile = context.getDatabasePath(databaseName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
        val shmFile = File(dbFile.parentFile, "$databaseName-shm")
        if (shmFile.exists()) shmFile.delete()
        val walFile = File(dbFile.parentFile, "$databaseName-wal")
        if (walFile.exists()) walFile.delete()
    }

    fun getDatabaseFile(): File = context.getDatabasePath(databaseName)
}
