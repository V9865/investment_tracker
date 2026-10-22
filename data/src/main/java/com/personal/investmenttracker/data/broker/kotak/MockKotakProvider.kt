package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import java.io.InputStream
import java.math.BigDecimal

enum class MockKotakMode {
    SUCCESS,
    EXPIRED,
    EMPTY,
}

class MockKotakProvider(
    var mode: MockKotakMode = MockKotakMode.SUCCESS,
    private var isExpiredManual: Boolean = false,
) : KotakPortfolioProvider {
    override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> =
        when (mode) {
            MockKotakMode.SUCCESS -> Result.success(createIllustrativeHoldings())
            MockKotakMode.EMPTY -> Result.success(emptyList())
            MockKotakMode.EXPIRED -> {
                isExpiredManual = true
                Result.failure(KotakSessionExpiredException())
            }
        }

    override fun importHoldings(inputStream: InputStream): Result<List<Pair<Holding, Quote>>> =
        try {
            val holdings = KotakFileImportParser.parseHoldingsCsv(inputStream)
            Result.success(holdings)
        } catch (e: KotakFileImportException) {
            Result.failure(e)
        }

    override fun getSessionBannerState(): KotakSessionBannerState {
        val expired = isSessionExpired()
        return KotakSessionBannerState(
            isVisible = expired,
            message = if (expired) "Kotak token expired. Update from trade.kotakneo.com/trade-api." else "",
        )
    }

    override fun isSessionExpired(): Boolean = isExpiredManual || mode == MockKotakMode.EXPIRED

    override fun hasCredentials(): Boolean = true

    fun setSimulatedExpired(expired: Boolean) {
        isExpiredManual = expired
    }

    private data class HoldingFixture(
        val symbol: String,
        val isin: String,
        val exchange: Exchange,
        val qty: Long,
        val avgPrice: String,
        val lastPrice: String,
        val closePrice: String,
    )

    companion object {
        fun createIllustrativeHoldings(timestampMs: Long = System.currentTimeMillis()): List<Pair<Holding, Quote>> =
            listOf(
                // Worked example 4 in Section 5: Kotak 5 @ 110, price 120
                HoldingFixture("SBIN", "INE062A01020", Exchange.BSE, 5L, "110.00", "120.00", "120.00"),
                HoldingFixture("HDFCBANK", "INE040A01034", Exchange.NSE, 25L, "1600.00", "1670.00", "1650.00"),
                HoldingFixture("ITC", "INE154A01025", Exchange.NSE, 100L, "420.00", "440.00", "435.00"),
            ).map { createHoldingPair(it, timestampMs) }

        private fun createHoldingPair(
            fixture: HoldingFixture,
            timestampMs: Long,
        ): Pair<Holding, Quote> {
            val lastMoney = Money.of(fixture.lastPrice)
            val closeMoney = Money.of(fixture.closePrice)
            val quote =
                Quote(
                    price = lastMoney,
                    prevClose = closeMoney,
                    closePrice = closeMoney,
                    source = QuoteSource.BROKER_LIVE,
                    sourceTimestamp = timestampMs,
                    freshness = Freshness.LIVE,
                )
            val holding =
                Holding(
                    broker = Broker.KOTAK_NEO,
                    isin = fixture.isin,
                    exchange = fixture.exchange,
                    symbol = fixture.symbol,
                    quantity = Quantity(BigDecimal.valueOf(fixture.qty)),
                    avgPriceReported = Money.of(fixture.avgPrice),
                    quote = quote,
                )
            return Pair(holding, quote)
        }
    }
}
