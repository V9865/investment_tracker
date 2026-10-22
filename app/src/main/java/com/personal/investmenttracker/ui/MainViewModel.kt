package com.personal.investmenttracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.investmenttracker.data.broker.kotak.KotakCredentialsRepository
import com.personal.investmenttracker.data.broker.zerodha.KiteCredentialsRepository
import com.personal.investmenttracker.data.broker.zerodha.ZerodhaPortfolioProvider
import com.personal.investmenttracker.data.local.AppDatabase
import com.personal.investmenttracker.data.security.LockState
import com.personal.investmenttracker.data.security.MasterKeyManager
import com.personal.investmenttracker.data.security.SessionLockManager
import com.personal.investmenttracker.data.sync.SyncOrchestrator
import com.personal.investmenttracker.data.sync.SyncRepository
import com.personal.investmenttracker.domain.calculator.PortfolioCalculator
import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.DailyPnl
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.PnlCompleteness
import com.personal.investmenttracker.ui.dashboard.BrokerCardUiModel
import com.personal.investmenttracker.ui.dashboard.DashboardUiState
import com.personal.investmenttracker.ui.history.PnlHistoryUiState
import com.personal.investmenttracker.ui.holdings.HoldingsSortOption
import com.personal.investmenttracker.ui.holdings.HoldingsUiState
import com.personal.investmenttracker.ui.holdings.HoldingsViewMode
import com.personal.investmenttracker.ui.settings.SettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
class MainViewModel
    @Inject
    @Suppress("LongParameterList")
    constructor(
        private val syncRepository: SyncRepository,
        private val syncOrchestrator: SyncOrchestrator,
        private val sessionLockManager: SessionLockManager,
        private val masterKeyManager: MasterKeyManager,
        private val kiteCredentialsRepo: KiteCredentialsRepository,
        private val kotakCredentialsRepo: KotakCredentialsRepository,
        private val database: AppDatabase,
        private val zerodhaProvider: ZerodhaPortfolioProvider,
    ) : ViewModel() {
        private val isSyncingState = MutableStateFlow(false)
        private val syncErrorState = MutableStateFlow<String?>(null)
        private val viewModeState = MutableStateFlow(HoldingsViewMode.CONSOLIDATED)
        private val sortOptionState = MutableStateFlow(HoldingsSortOption.VALUE_DESC)
        private val flagSecureState = MutableStateFlow(sessionLockManager.isFlagSecureEnabled)

        val lockState: StateFlow<LockState> = sessionLockManager.lockState

        val dashboardState: StateFlow<DashboardUiState> =
            combine(
                syncRepository.getCurrentHoldingsWithQuotes(),
                isSyncingState,
                syncErrorState,
            ) { holdings, isSyncing, error ->
                val summary = PortfolioCalculator.calculatePortfolio(holdings)
                val brokerCards =
                    listOf(Broker.ZERODHA, Broker.KOTAK_NEO).map { broker ->
                        val conn = syncRepository.getBrokerConnectionStatus(broker)
                        val subtotal = summary.brokerSubtotals[broker]
                        val isStale = conn?.status == "STALE"
                        val msg =
                            if (isStale) {
                                "${broker.displayName} data is stale. Tap to reconnect."
                            } else {
                                ""
                            }
                        BrokerCardUiModel(
                            broker = broker,
                            subtotal = subtotal,
                            lastSyncAt = conn?.lastSuccessSyncAt,
                            status = conn?.status ?: "DISCONNECTED",
                            sessionExpired = isStale,
                            bannerMessage = msg,
                        )
                    }
                DashboardUiState(
                    isLoading = isSyncing,
                    errorMessage = error,
                    portfolioSummary = summary,
                    brokerCards = brokerCards,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DashboardUiState(isLoading = true),
            )

        val holdingsState: StateFlow<HoldingsUiState> =
            combine(
                syncRepository.getCurrentHoldingsWithQuotes(),
                viewModeState,
                sortOptionState,
            ) { holdings, mode, sort ->
                val summary = PortfolioCalculator.calculatePortfolio(holdings)
                val sortedConsolidated =
                    when (sort) {
                        HoldingsSortOption.VALUE_DESC ->
                            summary.consolidatedHoldings.sortedByDescending {
                                it.totalCurrentValue?.amount
                            }
                        HoldingsSortOption.DAY_PNL_DESC ->
                            summary.consolidatedHoldings.sortedByDescending {
                                it.dailyPnl?.amount
                            }
                        HoldingsSortOption.UNREALIZED_DESC ->
                            summary.consolidatedHoldings.sortedByDescending {
                                it.unrealizedPnl?.amount
                            }
                        HoldingsSortOption.SYMBOL_ASC ->
                            summary.consolidatedHoldings.sortedBy { it.symbol }
                    }

                val individualHoldings =
                    holdings.map {
                        PortfolioCalculator.calculateHolding(it)
                    }
                val sortedIndividual =
                    when (sort) {
                        HoldingsSortOption.VALUE_DESC ->
                            individualHoldings.sortedByDescending {
                                it.currentValue?.amount
                            }
                        HoldingsSortOption.DAY_PNL_DESC ->
                            individualHoldings.sortedByDescending {
                                it.dailyPnl?.amount
                            }
                        HoldingsSortOption.UNREALIZED_DESC ->
                            individualHoldings.sortedByDescending {
                                it.unrealizedPnl?.amount
                            }
                        HoldingsSortOption.SYMBOL_ASC ->
                            individualHoldings.sortedBy { it.holding.symbol }
                    }

                HoldingsUiState(
                    isLoading = false,
                    selectedViewMode = mode,
                    selectedSort = sort,
                    consolidatedHoldings = sortedConsolidated,
                    brokerHoldings = sortedIndividual,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                HoldingsUiState(isLoading = true),
            )

        val pnlHistoryState: StateFlow<PnlHistoryUiState> =
            database
                .dailyPnlDao()
                .getAll()
                .map { entities ->
                    PnlHistoryUiState(
                        isLoading = false,
                        historyItems =
                            entities.map { entity ->
                                DailyPnl(
                                    date = entity.date,
                                    totalValue = Money(BigDecimal(entity.totalValue)),
                                    pnlAmount = Money(BigDecimal(entity.pnl)),
                                    netTradeCashFlow = Money(BigDecimal(entity.netTradeCashFlow)),
                                    completeness = PnlCompleteness.valueOf(entity.completeness),
                                )
                            },
                    )
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    PnlHistoryUiState(isLoading = true),
                )

        val settingsState: StateFlow<SettingsUiState> =
            flagSecureState
                .combine(isSyncingState) { flagSec, _ ->
                    SettingsUiState(
                        lockTimeoutMinutes = 5,
                        isFlagSecureEnabled = flagSec,
                        isZerodhaConnected = kiteCredentialsRepo.getApiKey() != null,
                        isKotakConnected = kotakCredentialsRepo.getAccessToken() != null,
                    )
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    SettingsUiState(),
                )

        fun syncAll() {
            viewModelScope.launch {
                isSyncingState.value = true
                syncErrorState.value = null
                try {
                    syncOrchestrator.syncAll(triggerEodIfAfterClose = true)
                } catch (e: java.io.IOException) {
                    syncErrorState.value = e.message ?: "Network error during sync"
                } catch (e: IllegalStateException) {
                    syncErrorState.value = e.message ?: "Invalid state during sync"
                } catch (e: SecurityException) {
                    syncErrorState.value = e.message ?: "Security verification failed"
                } catch (e: IllegalArgumentException) {
                    syncErrorState.value = e.message ?: "Invalid sync argument"
                } finally {
                    isSyncingState.value = false
                }
            }
        }

        fun setViewMode(mode: HoldingsViewMode) {
            viewModeState.value = mode
        }

        fun setSortOption(sort: HoldingsSortOption) {
            sortOptionState.value = sort
        }

        fun lockNow() {
            sessionLockManager.lockNow()
        }

        fun setFlagSecure(enabled: Boolean) {
            sessionLockManager.setFlagSecure(enabled)
            flagSecureState.value = enabled
        }

        fun onUserAuthenticated() {
            sessionLockManager.onUserAuthenticated()
        }

        fun onUserInteraction() {
            sessionLockManager.onUserInteraction()
        }

        fun onAppBackgrounded() {
            sessionLockManager.onAppBackgrounded()
        }

        fun getZerodhaApiKey(): String? = kiteCredentialsRepo.getApiKey()

        fun getZerodhaApiSecret(): String? = kiteCredentialsRepo.getApiSecret()

        fun saveZerodhaCredentials(
            apiKey: String,
            apiSecret: String,
        ) {
            kiteCredentialsRepo.saveCredentials(apiKey, apiSecret)
        }

        fun exchangeZerodhaToken(
            tokenOrUrl: String,
            onResult: (Result<Unit>) -> Unit = {},
        ) {
            viewModelScope.launch {
                val result = zerodhaProvider.exchangeToken(tokenOrUrl)
                if (result.isSuccess) {
                    syncAll()
                }
                onResult(result)
            }
        }

        fun getKotakAccessToken(): String? = kotakCredentialsRepo.getAccessToken()

        fun getKotakSid(): String? = kotakCredentialsRepo.getSid()

        fun saveKotakCredentials(
            accessToken: String,
            sid: String?,
        ) {
            kotakCredentialsRepo.saveCredentials(accessToken = accessToken, sid = sid)
            syncAll()
        }

        fun importKotakFile(
            inputStream: InputStream,
            onResult: (com.personal.investmenttracker.data.sync.BrokerSyncResult) -> Unit = {},
        ) {
            viewModelScope.launch {
                val result = syncOrchestrator.importKotakFile(inputStream)
                onResult(result)
            }
        }

        fun disconnectBroker(broker: Broker) {
            when (broker) {
                Broker.ZERODHA -> kiteCredentialsRepo.clearAll()
                Broker.KOTAK_NEO -> kotakCredentialsRepo.clearCredentials()
            }
        }

        fun eraseAllData() {
            sessionLockManager.resetDataAndResync(masterKeyManager)
            kiteCredentialsRepo.clearAll()
            kotakCredentialsRepo.clearCredentials()
        }
    }
