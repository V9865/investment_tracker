package com.personal.investmenttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.personal.investmenttracker.data.local.entity.BrokerConnectionEntity
import com.personal.investmenttracker.data.local.entity.DailyPnlEntity
import com.personal.investmenttracker.data.local.entity.EodSnapshotEntity
import com.personal.investmenttracker.data.local.entity.HoldingEntity
import com.personal.investmenttracker.data.local.entity.ListingEntity
import com.personal.investmenttracker.data.local.entity.QuoteCacheEntity
import com.personal.investmenttracker.data.local.entity.SyncRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrokerConnectionDao {
    @Query("SELECT * FROM broker_connection")
    fun getAll(): Flow<List<BrokerConnectionEntity>>

    @Query("SELECT * FROM broker_connection WHERE broker = :broker")
    fun getByBroker(broker: String): BrokerConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: BrokerConnectionEntity)
}

@Dao
interface ListingDao {
    @Query("SELECT * FROM listing WHERE exchange = :exchange AND symbol = :symbol")
    fun getByExchangeAndSymbol(
        exchange: String,
        symbol: String,
    ): ListingEntity?

    @Query("SELECT * FROM listing WHERE isin = :isin")
    fun getByIsin(isin: String): List<ListingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: ListingEntity): Long
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holding WHERE is_current = 1")
    fun getCurrentHoldings(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holding WHERE is_current = 1 AND broker = :broker")
    fun getCurrentHoldingsByBroker(broker: String): Flow<List<HoldingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(holdings: List<HoldingEntity>)

    @Query("UPDATE holding SET is_current = 0 WHERE broker = :broker")
    fun markOldHoldingsNonCurrent(broker: String)

    @Transaction
    fun replaceCurrentHoldings(
        broker: String,
        newHoldings: List<HoldingEntity>,
    ) {
        markOldHoldingsNonCurrent(broker)
        insertAll(newHoldings)
    }

    @Query("DELETE FROM holding")
    fun deleteAll()
}

@Dao
interface QuoteCacheDao {
    @Query("SELECT * FROM quote_cache WHERE listing_id = :listingId")
    fun getByListingId(listingId: Long): QuoteCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: QuoteCacheEntity)
}

@Dao
interface EodSnapshotDao {
    @Query("SELECT * FROM eod_snapshot WHERE date = :date")
    fun getByDate(date: String): List<EodSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(snapshots: List<EodSnapshotEntity>)
}

@Dao
interface DailyPnlDao {
    @Query("SELECT * FROM daily_pnl ORDER BY date DESC")
    fun getAll(): Flow<List<DailyPnlEntity>>

    @Query("SELECT * FROM daily_pnl WHERE date = :date")
    fun getByDate(date: String): DailyPnlEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: DailyPnlEntity)
}

@Dao
interface SyncRunDao {
    @Insert
    fun insert(syncRun: SyncRunEntity): Long

    @Query("UPDATE sync_run SET status = :status, error_code = :errorCode WHERE id = :id")
    fun updateStatus(
        id: Long,
        status: String,
        errorCode: String?,
    )
}
