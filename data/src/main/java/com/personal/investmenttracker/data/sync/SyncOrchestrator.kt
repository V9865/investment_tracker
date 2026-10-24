package com.personal.investmenttracker.data.sync

import com.personal.investmenttracker.data.broker.kotak.KotakPortfolioProvider
import com.personal.investmenttracker.data.broker.zerodha.ZerodhaPortfolioProvider
import com.personal.investmenttracker.domain.model.Broker
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOrchestrator
    @Inject
    constructor(
        private val zerodhaProvider: ZerodhaPortfolioProvider,
        private val kotakProvider: KotakPortfolioProvider,
        private val syncRepository: SyncRepository,
        private val eodSnapshotManager: EodSnapshotManager,
    ) {
        companion object {
            const val DEFAULT_TIMEOUT_MS = 15_000L
        }

        suspend fun syncAll(
            timeoutMs: Long = DEFAULT_TIMEOUT_MS,
            triggerEodIfAfterClose: Boolean = true,
        ): Map<Broker, BrokerSyncResult> =
            coroutineScope {
                val zerodhaDeferred = async { syncBroker(Broker.ZERODHA, timeoutMs) }
                val kotakDeferred = async { syncBroker(Broker.KOTAK_NEO, timeoutMs) }

                val zerodhaResult = zerodhaDeferred.await()
                val kotakResult = kotakDeferred.await()

                val results =
                    mapOf(
                        Broker.ZERODHA to zerodhaResult,
                        Broker.KOTAK_NEO to kotakResult,
                    )

                if (triggerEodIfAfterClose && eodSnapshotManager.isAfterMarketClose()) {
                    eodSnapshotManager.captureEodSnapshot()
                }

                results
            }

        @Suppress("TooGenericExceptionCaught")
        suspend fun syncBroker(
            broker: Broker,
            timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        ): BrokerSyncResult {
            val startedAt = System.currentTimeMillis()
            return try {
                withTimeout(timeoutMs) {
                    when (broker) {
                        Broker.ZERODHA -> executeZerodhaSync(startedAt)
                        Broker.KOTAK_NEO -> executeKotakSync(startedAt)
                    }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Sync timed out or failed"
                syncRepository.recordBrokerFailure(broker, startedAt, errorMsg)
                BrokerSyncResult(
                    broker = broker,
                    status =
                        SyncStatus.Failure(
                            error = errorMsg,
                            lastGoodDataRetained = true,
                        ),
                )
            }
        }

        suspend fun importKotakFile(
            inputStream: InputStream,
            triggerEodIfAfterClose: Boolean = true,
        ): BrokerSyncResult {
            val startedAt = System.currentTimeMillis()
            val parseResult = kotakProvider.importHoldings(inputStream)
            return parseResult.fold(
                onSuccess = { holdingsWithQuotes ->
                    syncRepository.saveBrokerHoldings(
                        broker = Broker.KOTAK_NEO,
                        holdingsWithQuotes = holdingsWithQuotes,
                        startedAt = startedAt,
                    )
                    if (triggerEodIfAfterClose && eodSnapshotManager.isAfterMarketClose()) {
                        eodSnapshotManager.captureEodSnapshot()
                    }
                    BrokerSyncResult(
                        broker = Broker.KOTAK_NEO,
                        status =
                            SyncStatus.Success(
                                holdingsCount = holdingsWithQuotes.size,
                                syncedAt = System.currentTimeMillis(),
                            ),
                    )
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "File import parsing failed"
                    syncRepository.recordBrokerFailure(Broker.KOTAK_NEO, startedAt, errorMsg)
                    BrokerSyncResult(
                        broker = Broker.KOTAK_NEO,
                        status =
                            SyncStatus.Failure(
                                error = errorMsg,
                                lastGoodDataRetained = true,
                            ),
                    )
                },
            )
        }

        private suspend fun executeZerodhaSync(startedAt: Long): BrokerSyncResult {
            if (!zerodhaProvider.hasCredentials()) {
                return BrokerSyncResult(
                    broker = Broker.ZERODHA,
                    status =
                        SyncStatus.Failure(
                            error = "Zerodha is not configured",
                            lastGoodDataRetained = true,
                        ),
                )
            }
            val result = zerodhaProvider.fetchHoldings()
            return result.fold(
                onSuccess = { holdings ->
                    syncRepository.saveBrokerHoldings(
                        broker = Broker.ZERODHA,
                        holdingsWithQuotes = holdings,
                        startedAt = startedAt,
                    )
                    BrokerSyncResult(
                        broker = Broker.ZERODHA,
                        status =
                            SyncStatus.Success(
                                holdingsCount = holdings.size,
                                syncedAt = System.currentTimeMillis(),
                            ),
                    )
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "Zerodha fetch failed"
                    syncRepository.recordBrokerFailure(Broker.ZERODHA, startedAt, errorMsg)
                    BrokerSyncResult(
                        broker = Broker.ZERODHA,
                        status =
                            SyncStatus.Failure(
                                error = errorMsg,
                                lastGoodDataRetained = true,
                            ),
                    )
                },
            )
        }

        private suspend fun executeKotakSync(startedAt: Long): BrokerSyncResult {
            if (!kotakProvider.hasCredentials()) {
                return BrokerSyncResult(
                    broker = Broker.KOTAK_NEO,
                    status =
                        SyncStatus.Failure(
                            error = "Kotak Neo is not configured",
                            lastGoodDataRetained = true,
                        ),
                )
            }
            val result = kotakProvider.fetchHoldings()
            return result.fold(
                onSuccess = { holdings ->
                    syncRepository.saveBrokerHoldings(
                        broker = Broker.KOTAK_NEO,
                        holdingsWithQuotes = holdings,
                        startedAt = startedAt,
                    )
                    BrokerSyncResult(
                        broker = Broker.KOTAK_NEO,
                        status =
                            SyncStatus.Success(
                                holdingsCount = holdings.size,
                                syncedAt = System.currentTimeMillis(),
                            ),
                    )
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "Kotak fetch failed"
                    syncRepository.recordBrokerFailure(Broker.KOTAK_NEO, startedAt, errorMsg)
                    BrokerSyncResult(
                        broker = Broker.KOTAK_NEO,
                        status =
                            SyncStatus.Failure(
                                error = errorMsg,
                                lastGoodDataRetained = true,
                            ),
                    )
                },
            )
        }
    }
