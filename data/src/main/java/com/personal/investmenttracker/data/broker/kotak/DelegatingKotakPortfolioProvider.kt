package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quote
import java.io.InputStream

class DelegatingKotakPortfolioProvider(
    private val credentialsRepo: KotakCredentialsRepository,
    private val mockProvider: MockKotakProvider = MockKotakProvider(),
) : KotakPortfolioProvider {
    private val liveProvider: LiveKotakProvider by lazy {
        val apiService = KotakNetworkClientFactory.createApiService()
        LiveKotakProvider(apiService, credentialsRepo)
    }

    override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> =
        if (credentialsRepo.hasCredentials()) {
            liveProvider.fetchHoldings()
        } else {
            mockProvider.fetchHoldings()
        }

    override fun importHoldings(inputStream: InputStream): Result<List<Pair<Holding, Quote>>> {
        val imported = liveProvider.importHoldings(inputStream)
        return imported
    }

    override fun getSessionBannerState(): KotakSessionBannerState =
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
