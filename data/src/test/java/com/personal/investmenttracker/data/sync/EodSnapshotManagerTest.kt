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
import com.personal.investmenttracker.domain.model.PnlCompleteness
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class EodSnapshotManagerTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SyncRepository
    private lateinit var snapshotManager: EodSnapshotManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = SyncRepository(database)
        snapshotManager = EodSnapshotManager(database, repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun populateHoldings(
        qty: String = "10",
        price: String = "100.00",
        prevClose: String = "95.00",
    ) {
        val quote =
            Quote(
                price = Money(BigDecimal(price)),
                prevClose = Money(BigDecimal(prevClose)),
                closePrice = Money(BigDecimal(price)),
                source = QuoteSource.BROKER_LIVE,
                sourceTimestamp = 1000L,
                freshness = Freshness.LIVE,
            )
        val holding =
            Holding(
                broker = Broker.ZERODHA,
                isin = "INE002A01018",
                exchange = Exchange.NSE,
                symbol = "RELIANCE",
                quantity = Quantity(BigDecimal(qty)),
                avgPriceReported = Money(BigDecimal("90.00")),
                quote = quote,
            )
        repository.saveBrokerHoldings(Broker.ZERODHA, listOf(holding to quote), 1000L)
    }

    @Test
    fun `isAfterMarketClose respects default 15 45 IST`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val beforeClose = LocalDateTime.of(2026, 10, 22, 15, 44).atZone(zone).toInstant()
        val atClose = LocalDateTime.of(2026, 10, 22, 15, 45).atZone(zone).toInstant()
        val afterClose = LocalDateTime.of(2026, 10, 22, 16, 0).atZone(zone).toInstant()

        assertFalse(snapshotManager.isAfterMarketClose(beforeClose))
        assertTrue(snapshotManager.isAfterMarketClose(atClose))
        assertTrue(snapshotManager.isAfterMarketClose(afterClose))
    }

    @Test
    fun `snapshot written once per trading day is idempotent`() {
        populateHoldings(qty = "10", price = "100.00")

        val result1 = snapshotManager.captureEodSnapshot(customDate = "2026-10-22")
        assertTrue(result1.isSuccess)
        val snapshot1 = result1.getOrThrow()
        assertEquals("2026-10-22", snapshot1.date)
        assertEquals(1, snapshot1.items.size)

        // Capture again for the same date without force
        val result2 = snapshotManager.captureEodSnapshot(customDate = "2026-10-22")
        assertTrue(result2.isSuccess)
        val snapshot2 = result2.getOrThrow()

        // Verify DB only has 1 snapshot item for this date, not duplicated
        val dbItems = database.eodSnapshotDao().getByDate("2026-10-22")
        assertEquals(1, dbItems.size)
        assertEquals(snapshot1.items[0].value, snapshot2.items[0].value)
    }

    @Test
    fun `sequential trading days compute correct daily PnL from snapshots`() {
        // Day 1
        populateHoldings(qty = "10", price = "100.00", prevClose = "95.00")
        snapshotManager.captureEodSnapshot(customDate = "2026-10-21")

        val day1Pnl = snapshotManager.getDailyPnlForDate("2026-10-21")
        assertNotNull(day1Pnl)
        assertEquals(Money(BigDecimal("1000.00")), day1Pnl?.totalValue)
        assertEquals(PnlCompleteness.ESTIMATED, day1Pnl?.completeness)

        // Day 2: price rises to 105.00
        populateHoldings(qty = "10", price = "105.00", prevClose = "100.00")
        snapshotManager.captureEodSnapshot(customDate = "2026-10-22")

        val day2Pnl = snapshotManager.getDailyPnlForDate("2026-10-22")
        assertNotNull(day2Pnl)
        // Today total value: 10 * 105 = 1050.00
        assertEquals(Money(BigDecimal("1050.00")), day2Pnl?.totalValue)
        // 1050 - 1000 = 50.00
        assertEquals(Money(BigDecimal("50.00")), day2Pnl?.pnlAmount)
        assertEquals(PnlCompleteness.COMPLETE, day2Pnl?.completeness)
    }
}
