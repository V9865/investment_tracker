package com.personal.investmenttracker.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personal.investmenttracker.data.broker.kotak.MockKotakProvider
import com.personal.investmenttracker.data.broker.zerodha.MockZerodhaProvider
import com.personal.investmenttracker.data.broker.zerodha.ZerodhaPortfolioProvider
import com.personal.investmenttracker.data.broker.zerodha.ZerodhaSessionBannerState
import com.personal.investmenttracker.data.local.AppDatabase
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class SyncOrchestratorTest {
    private lateinit var database: AppDatabase
    private lateinit var syncRepository: SyncRepository
    private lateinit var snapshotManager: EodSnapshotManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        syncRepository = SyncRepository(database)
        snapshotManager = EodSnapshotManager(database, syncRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private class FailingZerodhaProvider : ZerodhaPortfolioProvider {
        override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> =
            Result.failure(IOException("Zerodha Gateway Timeout 504"))

        override suspend fun exchangeToken(requestToken: String): Result<Unit> = Result.success(Unit)

        override fun getSessionBannerState(): ZerodhaSessionBannerState = ZerodhaSessionBannerState(false, "")

        override fun isSessionExpired(): Boolean = false

        override fun hasCredentials(): Boolean = true
    }

    @Test
    fun `both brokers sync successfully`() =
        runTest {
            val mockZerodha = MockZerodhaProvider()
            val mockKotak = MockKotakProvider()

            val orchestrator =
                SyncOrchestrator(
                    zerodhaProvider = mockZerodha,
                    kotakProvider = mockKotak,
                    syncRepository = syncRepository,
                    eodSnapshotManager = snapshotManager,
                )

            val results = orchestrator.syncAll(triggerEodIfAfterClose = false)
            assertTrue(results[Broker.ZERODHA]?.status is SyncStatus.Success)
            assertTrue(results[Broker.KOTAK_NEO]?.status is SyncStatus.Success)

            val holdings = syncRepository.getCurrentHoldingsWithQuotesSync()
            // MockZerodha has 5 holdings, MockKotak has 3 holdings -> 8 total
            assertEquals(8, holdings.size)
        }

    @Test
    fun `one broker failing does not break the other and keeps last good data`() =
        runTest {
            val mockZerodha = MockZerodhaProvider()
            val mockKotak = MockKotakProvider()

            val initialOrchestrator =
                SyncOrchestrator(
                    zerodhaProvider = mockZerodha,
                    kotakProvider = mockKotak,
                    syncRepository = syncRepository,
                    eodSnapshotManager = snapshotManager,
                )

            // First initial successful sync
            initialOrchestrator.syncAll(triggerEodIfAfterClose = false)
            val initialHoldings = syncRepository.getCurrentHoldingsWithQuotesSync()
            assertEquals(8, initialHoldings.size)

            // Now Zerodha fails, but Kotak succeeds
            val failingZerodha = FailingZerodhaProvider()
            val secondOrchestrator =
                SyncOrchestrator(
                    zerodhaProvider = failingZerodha,
                    kotakProvider = mockKotak,
                    syncRepository = syncRepository,
                    eodSnapshotManager = snapshotManager,
                )

            val results = secondOrchestrator.syncAll(triggerEodIfAfterClose = false)

            // Zerodha failed, Kotak succeeded
            assertTrue(results[Broker.ZERODHA]?.status is SyncStatus.Failure)
            assertTrue(results[Broker.KOTAK_NEO]?.status is SyncStatus.Success)

            // Zerodha connection is marked STALE
            val zerodhaConn = syncRepository.getBrokerConnectionStatus(Broker.ZERODHA)
            assertEquals("STALE", zerodhaConn?.status)

            // Kotak connection remains CONNECTED
            val kotakConn = syncRepository.getBrokerConnectionStatus(Broker.KOTAK_NEO)
            assertEquals("CONNECTED", kotakConn?.status)

            // Last good data for Zerodha is retained in local database!
            val holdingsAfter = syncRepository.getCurrentHoldingsWithQuotesSync()
            val zerodhaHoldings = holdingsAfter.filter { it.broker == Broker.ZERODHA }
            assertEquals(5, zerodhaHoldings.size)
        }

    @Test
    fun `importKotakFile updates Kotak holdings via CSV or XLSX fallback`() =
        runTest {
            val mockZerodha = MockZerodhaProvider()
            val mockKotak = MockKotakProvider()

            val orchestrator =
                SyncOrchestrator(
                    zerodhaProvider = mockZerodha,
                    kotakProvider = mockKotak,
                    syncRepository = syncRepository,
                    eodSnapshotManager = snapshotManager,
                )

            val csvContent =
                """
                ISIN,Symbol,Exchange,Quantity,Average Price,LTP,Close Price
                INE002A01018,RELIANCE,NSE,15,2450.00,2500.00,2480.00
                """.trimIndent()
            val inputStream = ByteArrayInputStream(csvContent.toByteArray())

            val importResult = orchestrator.importKotakFile(inputStream, triggerEodIfAfterClose = false)
            assertTrue(importResult.status is SyncStatus.Success)

            val holdings = syncRepository.getCurrentHoldingsWithQuotesSync()
            val kotakReliance = holdings.find { it.broker == Broker.KOTAK_NEO && it.symbol == "RELIANCE" }
            assertEquals(Quantity(BigDecimal("15")), kotakReliance?.quantity)
        }
}
