package com.personal.investmenttracker.data.broker.zerodha

import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quote

class DelegatingZerodhaPortfolioProvider(
    private val credentialsRepo: KiteCredentialsRepository,
    private val mockProvider: MockZerodhaProvider = MockZerodhaProvider(),
    private val isDemoMode: Boolean = false,
) : ZerodhaPortfolioProvider {
    private val liveProvider: LiveZerodhaProvider by lazy {
        val apiService = KiteNetworkClientFactory.createApiService(tokenProvider = credentialsRepo)
        LiveZerodhaProvider(apiService, credentialsRepo)
    }

    override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.fetchHoldings()
        } else if (isDemoMode) {
            mockProvider.fetchHoldings()
        } else {
            Result.failure(
                IllegalStateException("Zerodha credentials (API Key and Secret) are not configured."),
            )
        }

    override suspend fun exchangeToken(requestToken: String): Result<Unit> =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.exchangeToken(requestToken)
        } else if (isDemoMode) {
            mockProvider.exchangeToken(requestToken)
        } else {
            Result.failure(
                IllegalStateException("Zerodha credentials (API Key and Secret) are not configured."),
            )
        }

    override fun getSessionBannerState(): ZerodhaSessionBannerState =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.getSessionBannerState()
        } else if (isDemoMode) {
            mockProvider.getSessionBannerState()
        } else {
            ZerodhaSessionBannerState(isVisible = false, message = "", loginUrl = null)
        }

    override fun isSessionExpired(): Boolean =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.isSessionExpired()
        } else if (isDemoMode) {
            mockProvider.isSessionExpired()
        } else {
            false
        }

    override fun hasCredentials(): Boolean = credentialsRepo.hasCredentials() || isDemoMode
}
