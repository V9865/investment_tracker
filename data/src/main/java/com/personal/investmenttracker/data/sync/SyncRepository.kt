package com.personal.investmenttracker.data.sync

import com.personal.investmenttracker.data.local.AppDatabase
import com.personal.investmenttracker.data.local.entity.BrokerConnectionEntity
import com.personal.investmenttracker.data.local.entity.HoldingEntity
import com.personal.investmenttracker.data.local.entity.ListingEntity
import com.personal.investmenttracker.data.local.entity.QuoteCacheEntity
import com.personal.investmenttracker.data.local.entity.SyncRunEntity
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository
    @Inject
    constructor(
        private val database: AppDatabase,
    ) {
        private val listingDao = database.listingDao()
        private val holdingDao = database.holdingDao()
        private val quoteCacheDao = database.quoteCacheDao()
        private val brokerConnectionDao = database.brokerConnectionDao()
        private val syncRunDao = database.syncRunDao()

        fun saveBrokerHoldings(
            broker: Broker,
            holdingsWithQuotes: List<Pair<Holding, Quote>>,
            startedAt: Long,
        ): Long {
            val syncRunId =
                syncRunDao.insert(
                    SyncRunEntity(
                        broker = broker.name,
                        startedAt = startedAt,
                        status = "RUNNING",
                        errorCode = null,
                    ),
                )

            val newHoldingEntities = mutableListOf<HoldingEntity>()

            for ((holding, quote) in holdingsWithQuotes) {
                // Identity = (ISIN, exchange), never ticker alone
                val listingId =
                    resolveOrCreateListing(
                        isin = holding.isin,
                        exchange = holding.exchange,
                        symbol = holding.symbol,
                    )

                // Cache quote
                quoteCacheDao.upsert(
                    QuoteCacheEntity(
                        listingId = listingId,
                        lastPrice = quote.price.amount.toPlainString(),
                        prevClose = quote.prevClose.amount.toPlainString(),
                        closePrice = quote.closePrice?.amount?.toPlainString(),
                        source = quote.source.name,
                        sourceTs = quote.sourceTimestamp,
                        fetchedAt = System.currentTimeMillis(),
                        freshness = quote.freshness.name,
                    ),
                )

                newHoldingEntities.add(
                    HoldingEntity(
                        broker = broker.name,
                        listingId = listingId,
                        isin = holding.isin,
                        quantity = holding.quantity.value.toPlainString(),
                        avgPriceReported = holding.avgPriceReported?.amount?.toPlainString(),
                        syncRunId = syncRunId,
                        asOf = System.currentTimeMillis(),
                        isCurrent = true,
                    ),
                )
            }

            // Atomically replace current holdings for this broker
            holdingDao.replaceCurrentHoldings(broker.name, newHoldingEntities)

            // Mark broker as CONNECTED with last success timestamp
            brokerConnectionDao.upsert(
                BrokerConnectionEntity(
                    broker = broker.name,
                    status = "CONNECTED",
                    lastSuccessSyncAt = System.currentTimeMillis(),
                ),
            )

            // Mark sync run as successful
            syncRunDao.updateStatus(syncRunId, "SUCCESS", null)

            return syncRunId
        }

        fun recordBrokerFailure(
            broker: Broker,
            startedAt: Long,
            errorMessage: String,
        ) {
            val syncRunId =
                syncRunDao.insert(
                    SyncRunEntity(
                        broker = broker.name,
                        startedAt = startedAt,
                        status = "FAILED",
                        errorCode = errorMessage,
                    ),
                )

            // Retain last good data in holdingDao; mark broker connection as STALE
            val existing = brokerConnectionDao.getByBroker(broker.name)
            brokerConnectionDao.upsert(
                BrokerConnectionEntity(
                    broker = broker.name,
                    status = "STALE",
                    lastSuccessSyncAt = existing?.lastSuccessSyncAt,
                ),
            )

            syncRunDao.updateStatus(syncRunId, "FAILED", errorMessage)
        }

        fun getCurrentHoldingsWithQuotes(): Flow<List<Holding>> =
            holdingDao.getCurrentHoldings().map { entities ->
                mapHoldingEntitiesToDomain(entities)
            }

        fun getCurrentHoldingsWithQuotesSync(): List<Holding> {
            val entities = holdingDao.getCurrentHoldingsSync()
            return mapHoldingEntitiesToDomain(entities)
        }

        fun getBrokerConnectionStatus(broker: Broker): BrokerConnectionEntity? {
            val brokerName = broker.name
            return brokerConnectionDao.getByBroker(brokerName)
        }

        private fun mapHoldingEntitiesToDomain(entities: List<HoldingEntity>): List<Holding> =
            entities.mapNotNull { entity ->
                val listing = listingDao.getById(entity.listingId) ?: return@mapNotNull null
                val quoteCache = quoteCacheDao.getByListingId(entity.listingId)

                val quote =
                    quoteCache?.let { qc ->
                        Quote(
                            price = Money(BigDecimal(qc.lastPrice)),
                            prevClose = Money(BigDecimal(qc.prevClose)),
                            closePrice = qc.closePrice?.let { Money(BigDecimal(it)) },
                            source = QuoteSource.valueOf(qc.source),
                            sourceTimestamp = qc.sourceTs,
                            freshness = Freshness.valueOf(qc.freshness),
                        )
                    }

                Holding(
                    broker = Broker.valueOf(entity.broker),
                    isin = entity.isin,
                    exchange = Exchange.valueOf(listing.exchange),
                    symbol = listing.symbol,
                    quantity = Quantity(BigDecimal(entity.quantity)),
                    avgPriceReported = entity.avgPriceReported?.let { Money(BigDecimal(it)) },
                    quote = quote,
                )
            }

        fun resolveOrCreateListing(
            isin: String,
            exchange: Exchange,
            symbol: String,
        ): Long {
            val existingByIsinExchange = listingDao.getByIsinAndExchange(isin, exchange.name)
            val existingByExchangeSymbol = listingDao.getByExchangeAndSymbol(exchange.name, symbol)
            val existingId = existingByIsinExchange?.id ?: existingByExchangeSymbol?.id

            return existingId ?: listingDao.insert(
                ListingEntity(
                    isin = isin,
                    exchange = exchange.name,
                    symbol = symbol,
                    scripCode = null,
                    brokerRef = null,
                ),
            )
        }
    }
