package com.personal.investmenttracker.data.broker.zerodha

import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quote

data class ZerodhaSessionBannerState(
    val isVisible: Boolean,
    val message: String,
    val loginUrl: String? = null,
)

interface ZerodhaPortfolioProvider {
    suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>>

    suspend fun exchangeToken(requestToken: String): Result<Unit>

    fun getSessionBannerState(): ZerodhaSessionBannerState

    fun isSessionExpired(): Boolean

    fun hasCredentials(): Boolean
}
