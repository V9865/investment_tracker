package com.personal.investmenttracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "broker_connection")
data class BrokerConnectionEntity(
    @PrimaryKey
    val broker: String,
    val status: String,
    @ColumnInfo(name = "last_success_sync_at")
    val lastSuccessSyncAt: Long?,
)

@Entity(
    tableName = "listing",
    indices = [
        Index(value = ["exchange", "symbol"], unique = true),
        Index(value = ["isin"]),
    ],
)
data class ListingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val isin: String,
    val exchange: String,
    val symbol: String,
    @ColumnInfo(name = "scrip_code")
    val scripCode: String?,
    @ColumnInfo(name = "broker_ref")
    val brokerRef: String?,
)

@Entity(
    tableName = "holding",
    indices = [
        Index(value = ["isin"]),
        Index(value = ["broker", "listing_id"]),
    ],
)
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val broker: String,
    @ColumnInfo(name = "listing_id")
    val listingId: Long,
    val isin: String,
    val quantity: String,
    @ColumnInfo(name = "avg_price_reported")
    val avgPriceReported: String?,
    @ColumnInfo(name = "sync_run_id")
    val syncRunId: Long,
    @ColumnInfo(name = "as_of")
    val asOf: Long,
    @ColumnInfo(name = "is_current")
    val isCurrent: Boolean,
)

@Entity(tableName = "quote_cache")
data class QuoteCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "listing_id")
    val listingId: Long,
    @ColumnInfo(name = "last_price")
    val lastPrice: String,
    @ColumnInfo(name = "prev_close")
    val prevClose: String,
    @ColumnInfo(name = "close_price")
    val closePrice: String?,
    val source: String,
    @ColumnInfo(name = "source_ts")
    val sourceTs: Long,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
    val freshness: String,
)

@Entity(
    tableName = "eod_snapshot",
    indices = [
        Index(value = ["date", "broker", "isin", "exchange"], unique = true),
    ],
)
data class EodSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val broker: String,
    val isin: String,
    val exchange: String,
    val quantity: String,
    @ColumnInfo(name = "close_price")
    val closePrice: String,
    val value: String,
    @ColumnInfo(name = "source_ts")
    val sourceTs: Long,
)

@Entity(tableName = "daily_pnl")
data class DailyPnlEntity(
    @PrimaryKey
    val date: String,
    @ColumnInfo(name = "total_value")
    val totalValue: String,
    @ColumnInfo(name = "net_trade_cash_flow")
    val netTradeCashFlow: String,
    val pnl: String,
    val completeness: String,
)

@Entity(tableName = "sync_run")
data class SyncRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val broker: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    val status: String,
    @ColumnInfo(name = "error_code")
    val errorCode: String?,
)
