package com.personal.investmenttracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.personal.investmenttracker.data.local.dao.BrokerConnectionDao
import com.personal.investmenttracker.data.local.dao.DailyPnlDao
import com.personal.investmenttracker.data.local.dao.EodSnapshotDao
import com.personal.investmenttracker.data.local.dao.HoldingDao
import com.personal.investmenttracker.data.local.dao.ListingDao
import com.personal.investmenttracker.data.local.dao.QuoteCacheDao
import com.personal.investmenttracker.data.local.dao.SyncRunDao
import com.personal.investmenttracker.data.local.entity.BrokerConnectionEntity
import com.personal.investmenttracker.data.local.entity.DailyPnlEntity
import com.personal.investmenttracker.data.local.entity.EodSnapshotEntity
import com.personal.investmenttracker.data.local.entity.HoldingEntity
import com.personal.investmenttracker.data.local.entity.ListingEntity
import com.personal.investmenttracker.data.local.entity.QuoteCacheEntity
import com.personal.investmenttracker.data.local.entity.SyncRunEntity

@Database(
    entities = [
        BrokerConnectionEntity::class,
        ListingEntity::class,
        HoldingEntity::class,
        QuoteCacheEntity::class,
        EodSnapshotEntity::class,
        DailyPnlEntity::class,
        SyncRunEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun brokerConnectionDao(): BrokerConnectionDao

    abstract fun listingDao(): ListingDao

    abstract fun holdingDao(): HoldingDao

    abstract fun quoteCacheDao(): QuoteCacheDao

    abstract fun eodSnapshotDao(): EodSnapshotDao

    abstract fun dailyPnlDao(): DailyPnlDao

    abstract fun syncRunDao(): SyncRunDao

    companion object {
        const val DATABASE_NAME = "investment_tracker.db"
    }
}
