package com.personal.investmenttracker.data.broker.zerodha

import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quote
import java.io.IOException

class LiveZerodhaProvider(
    private val apiService: KiteApiService,
    private val credentialsRepo: KiteCredentialsRepository,
) : ZerodhaPortfolioProvider {
    override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> {
        val precheckFailure = checkPreconditions()
        if (precheckFailure != null) {
            return Result.failure(precheckFailure)
        }

        return executeHoldingsCall()
    }

    private fun checkPreconditions(): Throwable? =
        when {
            !credentialsRepo.hasCredentials() ->
                IllegalStateException("Zerodha credentials (API Key and Secret) are not configured.")
            !credentialsRepo.isSessionValid() ->
                KiteSessionExpiredException("Zerodha session is expired. Daily re-login required.")
            else -> null
        }

    private suspend fun executeHoldingsCall(): Result<List<Pair<Holding, Quote>>> =
        try {
            val response = apiService.getHoldings()
            val domainHoldings = ZerodhaHoldingsMapper.mapAllToDomain(response.data)
            Result.success(domainHoldings)
        } catch (e: KiteSessionExpiredException) {
            credentialsRepo.clearSession()
            Result.failure(e)
        } catch (e: KiteRateLimitException) {
            Result.failure(e)
        } catch (e: KiteNetworkTimeoutException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(KiteGenericApiException(0, null, e.message ?: "Network error during Kite holdings call"))
        }

    override suspend fun exchangeToken(requestToken: String): Result<Unit> {
        val apiKey = credentialsRepo.getApiKey()
        val apiSecret = credentialsRepo.getApiSecret()
        val cleanedToken = KiteAuthHelper.extractRequestToken(requestToken)

        if (apiKey.isNullOrBlank() || apiSecret.isNullOrBlank() || cleanedToken == null) {
            return Result.failure(
                IllegalArgumentException("Invalid credentials or request token format."),
            )
        }

        return executeTokenExchange(apiKey, cleanedToken, apiSecret)
    }

    private suspend fun executeTokenExchange(
        apiKey: String,
        cleanedToken: String,
        apiSecret: String,
    ): Result<Unit> =
        try {
            val checksum = KiteAuthHelper.computeChecksum(apiKey, cleanedToken, apiSecret)
            val response =
                apiService.exchangeToken(
                    apiKey = apiKey,
                    requestToken = cleanedToken,
                    checksum = checksum,
                )
            credentialsRepo.saveSession(
                accessToken = response.data.accessToken,
                userId = response.data.userId,
            )
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }

    override fun getSessionBannerState(): ZerodhaSessionBannerState {
        val hasCreds = credentialsRepo.hasCredentials()
        val isExpired = !credentialsRepo.isSessionValid()
        val apiKey = credentialsRepo.getApiKey()

        val shouldShowBanner = hasCreds && isExpired
        val loginUrl =
            if (shouldShowBanner && !apiKey.isNullOrBlank()) {
                KiteAuthHelper.buildLoginUrl(apiKey)
            } else {
                null
            }

        return ZerodhaSessionBannerState(
            isVisible = shouldShowBanner,
            message = if (shouldShowBanner) "Zerodha session expired. Daily re-login required." else "",
            loginUrl = loginUrl,
        )
    }

    override fun isSessionExpired(): Boolean = !credentialsRepo.isSessionValid()

    override fun hasCredentials(): Boolean = credentialsRepo.hasCredentials()
}
