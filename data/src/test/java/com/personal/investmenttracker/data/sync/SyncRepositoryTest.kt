package com.personal.investmenttracker.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personal.investmenttracker.data.local.AppDatabase
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class SyncRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SyncRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = SyncRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleHolding(
        symbol: String = "RELIANCE",
        qty: String = "10",
        price: String = "2500.00",
        isin: String = "INE002A01018",
        broker: Broker = Broker.ZERODHA,
    ): Pair<Holding, Quote> {
        val quote =
            Quote(
                price = Money(BigDecimal(price)),
                prevClose = Money(BigDecimal("2480.00")),
                closePrice = Money(BigDecimal(price)),
                source = QuoteSource.BROKER_LIVE,
                sourceTimestamp = 1700000000000L,
                freshness = Freshness.LIVE,
            )
        val holding =
            Holding(
                broker = broker,
                isin = isin,
                exchange = Exchange.NSE,
                symbol = symbol,
                quantity = Quantity(BigDecimal(qty)),
                avgPriceReported = Money(BigDecimal("2400.00")),
                quote = quote,
            )
        return holding to quote
    }

    @Test
    fun `canonical identity resolves by ISIN and exchange`() {
        val nseListingId = repository.resolveOrCreateListing("INE002A01018", Exchange.NSE, "RELIANCE")
        val bseListingId = repository.resolveOrCreateListing("INE002A01018", Exchange.BSE, "RELIANCE")
        val secondBrokerNseListingId = repository.resolveOrCreateListing("INE002A01018", Exchange.NSE, "RELIANCE")

        // Same ISIN on different exchanges must be distinct listings
        assertTrue(nseListingId != bseListingId)
        // Same ISIN and exchange from another broker must resolve to identical listing
        assertEquals(nseListingId, secondBrokerNseListingId)
    }

    @Test
    fun `idempotent re-sync replaces previous current holdings without duplicates`() {
        val holding1 = sampleHolding(symbol = "RELIANCE", qty = "10")
        val holding2 = sampleHolding(symbol = "INFY", isin = "INE009A01021", qty = "20")

        repository.saveBrokerHoldings(Broker.ZERODHA, listOf(holding1, holding2), 1000L)
        var currentHoldings = repository.getCurrentHoldingsWithQuotesSync()
        assertEquals(2, currentHoldings.size)

        // Sync again with updated quantity for INFY
        val updatedHolding2 = sampleHolding(symbol = "INFY", isin = "INE009A01021", qty = "25")
        repository.saveBrokerHoldings(Broker.ZERODHA, listOf(holding1, updatedHolding2), 2000L)

        currentHoldings = repository.getCurrentHoldingsWithQuotesSync()
        // Must still be 2, not 4
        assertEquals(2, currentHoldings.size)
        val infy = currentHoldings.first { it.symbol == "INFY" }
        assertEquals(Quantity(BigDecimal("25")), infy.quantity)
    }

    @Test
    fun `partial broker failure retains last good data and marks connection STALE`() {
        val holding = sampleHolding(broker = Broker.ZERODHA)
        repository.saveBrokerHoldings(Broker.ZERODHA, listOf(holding), 1000L)

        val connectionBefore = repository.getBrokerConnectionStatus(Broker.ZERODHA)
        assertEquals("CONNECTED", connectionBefore?.status)

        // Record subsequent failure
        repository.recordBrokerFailure(Broker.ZERODHA, 2000L, "Network timeout 504")

        // Last good data in current holdings is preserved!
        val holdingsAfter = repository.getCurrentHoldingsWithQuotesSync()
        assertEquals(1, holdingsAfter.size)
        assertEquals(Quantity(BigDecimal("10")), holdingsAfter[0].quantity)

        val connectionAfter = repository.getBrokerConnectionStatus(Broker.ZERODHA)
        assertEquals("STALE", connectionAfter?.status)
    }

    @Test
    fun `quote caching accurately stores and reconstructs quotes`() {
        val (holding, quote) = sampleHolding()
        repository.saveBrokerHoldings(Broker.ZERODHA, listOf(holding to quote), 1000L)

        val loadedHoldings = repository.getCurrentHoldingsWithQuotesSync()
        assertEquals(1, loadedHoldings.size)

        val loadedQuote = loadedHoldings[0].quote
        assertNotNull(loadedQuote)
        assertEquals(Money(BigDecimal("2500.00")), loadedQuote?.price)
        assertEquals(Money(BigDecimal("2480.00")), loadedQuote?.prevClose)
        assertEquals(Freshness.LIVE, loadedQuote?.freshness)
        assertEquals(QuoteSource.BROKER_LIVE, loadedQuote?.source)
    }
}
