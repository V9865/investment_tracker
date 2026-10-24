package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quote
import retrofit2.HttpException
import java.io.IOException
import java.io.InputStream

class LiveKotakProvider(
    private val apiService: KotakApiService,
    private val credentialsRepo: KotakCredentialsRepository,
) : KotakPortfolioProvider {
    @Volatile
    private var isSessionExpiredState: Boolean = false

    override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> {
        val token = credentialsRepo.getAccessToken()
        if (token.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Kotak Access Token not configured. Obtain token from trade.kotakneo.com/trade-api.",
                ),
            )
        }
        return executeHoldingsCall(token)
    }

    private suspend fun executeHoldingsCall(token: String): Result<List<Pair<Holding, Quote>>> =
        try {
            val sid = credentialsRepo.getSid()?.trim()?.ifEmpty { null }
            val cleanToken = token.trim().removePrefix("Bearer ").trim()
            val authHeader = "Bearer $cleanToken"

            val response =
                try {
                    apiService.getHoldings(
                        authorization = authHeader,
                        auth = cleanToken,
                        sid = sid,
                    )
                } catch (e: HttpException) {
                    if (e.code() == 404) {
                        apiService.getHoldingsV1(
                            authorization = authHeader,
                            auth = cleanToken,
                            sid = sid,
                        )
                    } else {
                        throw e
                    }
                }
            isSessionExpiredState = false
            val domainHoldings = KotakHoldingsMapper.mapAllToDomain(response.data)
            Result.success(domainHoldings)
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: IOException) {
            Result.failure(KotakGenericApiException(0, e.message ?: "Network error during Kotak holdings call"))
        }

    private fun handleHttpException(e: HttpException): Result<List<Pair<Holding, Quote>>> {
        val code = e.code()
        return if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) {
            isSessionExpiredState = true
            Result.failure(KotakSessionExpiredException())
        } else {
            Result.failure(KotakGenericApiException(code, "Kotak API error HTTP $code"))
        }
    }

    override fun importHoldings(inputStream: InputStream): Result<List<Pair<Holding, Quote>>> =
        try {
            val holdings = KotakFileImportParser.parseHoldingsCsv(inputStream)
            Result.success(holdings)
        } catch (e: KotakFileImportException) {
            Result.failure(e)
        }

    override fun getSessionBannerState(): KotakSessionBannerState {
        val expired = isSessionExpired()
        val visible = !hasCredentials() || expired
        val msg = if (visible) "Kotak token expired or missing. Update from trade.kotakneo.com/trade-api." else ""
        return KotakSessionBannerState(
            isVisible = visible,
            message = msg,
        )
    }

    override fun isSessionExpired(): Boolean = isSessionExpiredState

    override fun hasCredentials(): Boolean = credentialsRepo.hasCredentials()

    fun clearSession() {
        isSessionExpiredState = true
        credentialsRepo.clearCredentials()
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }
}
