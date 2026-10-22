package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quote
import java.io.InputStream

data class KotakSessionBannerState(
    val isVisible: Boolean,
    val message: String,
)

interface KotakPortfolioProvider {
    suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>>

    fun importHoldings(inputStream: InputStream): Result<List<Pair<Holding, Quote>>>

    fun getSessionBannerState(): KotakSessionBannerState

    fun isSessionExpired(): Boolean

    fun hasCredentials(): Boolean
}
