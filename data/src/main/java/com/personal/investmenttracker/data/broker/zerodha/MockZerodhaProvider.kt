package com.personal.investmenttracker.data.broker.zerodha

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import java.math.BigDecimal

enum class MockProviderMode {
    SUCCESS,
    EXPIRED_403,
    RATE_LIMIT_429,
    TIMEOUT,
    EMPTY,
}

class MockZerodhaProvider(
    var mode: MockProviderMode = MockProviderMode.SUCCESS,
    private var isExpiredManual: Boolean = false,
) : ZerodhaPortfolioProvider {
    override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> =
        when (mode) {
            MockProviderMode.SUCCESS -> Result.success(createIllustrativeHoldings())
            MockProviderMode.EMPTY -> Result.success(emptyList())
            MockProviderMode.EXPIRED_403 -> {
                isExpiredManual = true
                Result.failure(KiteSessionExpiredException("Mock: Zerodha session has expired (HTTP 403)"))
            }
            MockProviderMode.RATE_LIMIT_429 ->
                Result.failure(
                    KiteRateLimitException("Mock: Zerodha rate limit exceeded (HTTP 429)"),
                )
            MockProviderMode.TIMEOUT ->
                Result.failure(
                    KiteNetworkTimeoutException("Mock: Zerodha API timed out"),
                )
        }

    override suspend fun exchangeToken(requestToken: String): Result<Unit> =
        if (requestToken.isNotBlank()) {
            isExpiredManual = false
            mode = MockProviderMode.SUCCESS
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("Request token cannot be empty"))
        }

    override fun getSessionBannerState(): ZerodhaSessionBannerState {
        val expired = isSessionExpired()
        return ZerodhaSessionBannerState(
            isVisible = expired,
            message = if (expired) "Zerodha session expired. Daily re-login required." else "",
            loginUrl = if (expired) KiteAuthHelper.buildLoginUrl("mock_api_key") else null,
        )
    }

    override fun isSessionExpired(): Boolean = isExpiredManual || mode == MockProviderMode.EXPIRED_403

    override fun hasCredentials(): Boolean = true

    fun setSimulatedExpired(expired: Boolean) {
        isExpiredManual = expired
    }

    private data class HoldingFixtureParam(
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
                HoldingFixtureParam("INFY", "INE009A01021", Exchange.NSE, 50L, "1450.00", "1520.00", "1500.00"),
                HoldingFixtureParam("TCS", "INE467B01029", Exchange.NSE, 20L, "3800.00", "3950.00", "3900.00"),
                HoldingFixtureParam("RELIANCE", "INE002A01018", Exchange.NSE, 30L, "2400.00", "2480.00", "2450.00"),
                HoldingFixtureParam("SBIN", "INE062A01020", Exchange.BSE, 100L, "780.00", "815.00", "800.00"),
                HoldingFixtureParam("TATAMOTORS", "INE155A01022", Exchange.NSE, 40L, "920.00", "960.00", "940.00"),
            ).map { createHoldingQuote(it, timestampMs) }

        private fun createHoldingQuote(
            param: HoldingFixtureParam,
            timestampMs: Long,
        ): Pair<Holding, Quote> {
            val lastMoney = Money.of(param.lastPrice)
            val prevCloseMoney = Money.of(param.closePrice)
            val quote =
                Quote(
                    price = lastMoney,
                    prevClose = prevCloseMoney,
                    closePrice = prevCloseMoney,
                    source = QuoteSource.BROKER_LIVE,
                    sourceTimestamp = timestampMs,
                    freshness = Freshness.LIVE,
                )
            val holding =
                Holding(
                    broker = Broker.ZERODHA,
                    isin = param.isin,
                    exchange = param.exchange,
                    symbol = param.symbol,
                    quantity = Quantity(BigDecimal.valueOf(param.qty)),
                    avgPriceReported = Money.of(param.avgPrice),
                    quote = quote,
                )
            return Pair(holding, quote)
        }
    }
}
