package com.personal.investmenttracker.data.sync

import com.personal.investmenttracker.data.local.AppDatabase
import com.personal.investmenttracker.data.local.entity.DailyPnlEntity
import com.personal.investmenttracker.data.local.entity.EodSnapshotEntity
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.DailyPnl
import com.personal.investmenttracker.domain.model.EodSnapshot
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.PnlCompleteness
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.SnapshotItem
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EodSnapshotManager
    @Inject
    constructor(
        private val database: AppDatabase,
        private val syncRepository: SyncRepository,
    ) {
        private val eodSnapshotDao = database.eodSnapshotDao()
        private val dailyPnlDao = database.dailyPnlDao()

        companion object {
            val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
            const val DEFAULT_MARKET_CLOSE_HOUR = 15
            const val DEFAULT_MARKET_CLOSE_MINUTE = 45
        }

        fun isAfterMarketClose(
            instant: Instant = Instant.now(),
            closeHour: Int = DEFAULT_MARKET_CLOSE_HOUR,
            closeMinute: Int = DEFAULT_MARKET_CLOSE_MINUTE,
        ): Boolean {
            val zonedDateTime = instant.atZone(IST_ZONE)
            val closeTime = LocalTime.of(closeHour, closeMinute)
            return !zonedDateTime.toLocalTime().isBefore(closeTime)
        }

        fun getCurrentTradingDate(instant: Instant = Instant.now()): String {
            val zonedDateTime = instant.atZone(IST_ZONE)
            return DateTimeFormatter.ISO_LOCAL_DATE.format(zonedDateTime.toLocalDate())
        }

        /**
         * Captures EOD snapshot and computes daily P&L.
         * Idempotent: if snapshot already exists for date and force is false, returns existing snapshot.
         */
        fun captureEodSnapshot(
            customDate: String? = null,
            force: Boolean = false,
            now: Instant = Instant.now(),
        ): Result<EodSnapshot> =
            runCatching {
                val targetDate = customDate ?: getCurrentTradingDate(now)

                val existingEntities = eodSnapshotDao.getByDate(targetDate)
                if (existingEntities.isNotEmpty() && !force) {
                    return@runCatching EodSnapshot(targetDate, mapEntitiesToSnapshotItems(existingEntities))
                }

                val currentHoldings =
                    syncRepository
                        .getCurrentHoldingsWithQuotesSync()
                        .filter { !it.isZeroQty && !it.isNegativeQty }

                val snapshotEntities = buildSnapshotEntities(currentHoldings, targetDate, now)
                eodSnapshotDao.insertAll(snapshotEntities)

                val yesterdayItems = fetchPreviousDayItems(targetDate)
                recordDailyPnl(targetDate, currentHoldings, yesterdayItems)

                val items = buildSnapshotItems(snapshotEntities, currentHoldings)
                EodSnapshot(targetDate, items)
            }

        private fun buildSnapshotEntities(
            holdings: List<Holding>,
            date: String,
            now: Instant,
        ): List<EodSnapshotEntity> =
            holdings.map { holding ->
                val quote = holding.quote
                val closePrice = quote?.let { it.closePrice ?: it.price } ?: Money.ZERO
                val value = holding.quantity * closePrice
                val sourceTs = quote?.sourceTimestamp ?: now.toEpochMilli()

                EodSnapshotEntity(
                    date = date,
                    broker = holding.broker.name,
                    isin = holding.isin,
                    exchange = holding.exchange.name,
                    quantity = holding.quantity.value.toPlainString(),
                    closePrice = closePrice.amount.toPlainString(),
                    value = value.amount.toPlainString(),
                    sourceTs = sourceTs,
                )
            }

        private fun fetchPreviousDayItems(targetDate: String): List<SnapshotItem> {
            val distinctDates = eodSnapshotDao.getDistinctDates()
            val previousDate = distinctDates.firstOrNull { it < targetDate }
            return if (previousDate != null) {
                mapEntitiesToSnapshotItems(eodSnapshotDao.getByDate(previousDate))
            } else {
                emptyList()
            }
        }

        private fun recordDailyPnl(
            targetDate: String,
            currentHoldings: List<Holding>,
            yesterdayItems: List<SnapshotItem>,
        ) {
            val dailyPnl =
                DailyPnlCalculator.calculateDailyPnl(
                    DailyPnlCalculator.CalculationInput(
                        date = targetDate,
                        currentHoldings = currentHoldings,
                        yesterdaySnapshotItems = yesterdayItems,
                    ),
                )

            dailyPnlDao.upsert(
                DailyPnlEntity(
                    date = targetDate,
                    totalValue = dailyPnl.totalValue.amount.toPlainString(),
                    netTradeCashFlow = dailyPnl.netTradeCashFlow.amount.toPlainString(),
                    pnl = dailyPnl.pnlAmount.amount.toPlainString(),
                    completeness = dailyPnl.completeness.name,
                ),
            )
        }

        private fun buildSnapshotItems(
            entities: List<EodSnapshotEntity>,
            holdings: List<Holding>,
        ): List<SnapshotItem> =
            entities.map { entity ->
                val symbol =
                    holdings
                        .find {
                            it.isin == entity.isin && it.exchange.name == entity.exchange
                        }?.symbol
                        .orEmpty()

                SnapshotItem(
                    isin = entity.isin,
                    exchange = Exchange.valueOf(entity.exchange),
                    symbol = symbol,
                    broker = Broker.valueOf(entity.broker),
                    quantity = Quantity(BigDecimal(entity.quantity)),
                    closePrice = Money(BigDecimal(entity.closePrice)),
                    value = Money(BigDecimal(entity.value)),
                    sourceTimestamp = entity.sourceTs,
                )
            }

        private fun mapEntitiesToSnapshotItems(entities: List<EodSnapshotEntity>): List<SnapshotItem> =
            entities.map { entity ->
                SnapshotItem(
                    isin = entity.isin,
                    exchange = Exchange.valueOf(entity.exchange),
                    symbol = "",
                    broker = Broker.valueOf(entity.broker),
                    quantity = Quantity(BigDecimal(entity.quantity)),
                    closePrice = Money(BigDecimal(entity.closePrice)),
                    value = Money(BigDecimal(entity.value)),
                    sourceTimestamp = entity.sourceTs,
                )
            }

        fun getDailyPnlForDate(date: String): DailyPnl? {
            val entity = dailyPnlDao.getByDate(date) ?: return null
            return DailyPnl(
                date = entity.date,
                totalValue = Money(BigDecimal(entity.totalValue)),
                pnlAmount = Money(BigDecimal(entity.pnl)),
                netTradeCashFlow = Money(BigDecimal(entity.netTradeCashFlow)),
                completeness = PnlCompleteness.valueOf(entity.completeness),
            )
        }
    }
