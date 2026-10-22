package com.personal.investmenttracker.data.broker.zerodha

import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quote

class DelegatingZerodhaPortfolioProvider(
    private val credentialsRepo: KiteCredentialsRepository,
    private val mockProvider: MockZerodhaProvider = MockZerodhaProvider(),
) : ZerodhaPortfolioProvider {
    private val liveProvider: LiveZerodhaProvider by lazy {
        val apiService = KiteNetworkClientFactory.createApiService(tokenProvider = credentialsRepo)
        LiveZerodhaProvider(apiService, credentialsRepo)
    }

    override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.fetchHoldings()
        } else {
            mockProvider.fetchHoldings()
        }

    override suspend fun exchangeToken(requestToken: String): Result<Unit> =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.exchangeToken(requestToken)
        } else {
            mockProvider.exchangeToken(requestToken)
        }

    override fun getSessionBannerState(): ZerodhaSessionBannerState =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.getSessionBannerState()
        } else {
            mockProvider.getSessionBannerState()
        }

    override fun isSessionExpired(): Boolean =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.isSessionExpired()
        } else {
            mockProvider.isSessionExpired()
        }

    override fun hasCredentials(): Boolean = credentialsRepo.hasCredentials()
}
