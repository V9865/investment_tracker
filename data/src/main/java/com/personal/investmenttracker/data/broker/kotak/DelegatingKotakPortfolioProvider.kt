package com.personal.investmenttracker.data.broker.kotak

import android.content.Context
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Quote
import java.io.File
import java.io.InputStream

class DelegatingKotakPortfolioProvider(
    private val context: Context,
    private val credentialsRepo: KotakCredentialsRepository,
    private val mockProvider: MockKotakProvider = MockKotakProvider(),
    private val isDemoMode: Boolean = false,
) : KotakPortfolioProvider {
    private val liveProvider: LiveKotakProvider by lazy {
        val apiService = KotakNetworkClientFactory.createApiService()
        LiveKotakProvider(apiService, credentialsRepo)
    }

    override suspend fun fetchHoldings(): Result<List<Pair<Holding, Quote>>> =
        if (isDemoMode) {
            mockProvider.fetchHoldings()
        } else {
            val stream = getKotakCsvInputStream()
            if (stream != null) {
                stream.use { importHoldings(it) }
            } else {
                Result.failure(
                    IllegalStateException("Kotak holdings not imported. Select your Kotak CSV file to import."),
                )
            }
        }

    private fun getKotakCsvInputStream(): InputStream? {
        val local = File(context.filesDir, "kotak_holdings.csv")
        val ext = File(context.getExternalFilesDir(null), "kotak_holdings.csv")
        val target =
            if (local.exists()) {
                local
            } else if (ext.exists()) {
                ext
            } else {
                null
            }
        return target?.inputStream() ?: runCatching { context.assets.open("kotak_holdings.csv") }.getOrNull()
    }

    override fun importHoldings(inputStream: InputStream): Result<List<Pair<Holding, Quote>>> =
        liveProvider.importHoldings(inputStream)

    override fun getSessionBannerState(): KotakSessionBannerState =
        KotakSessionBannerState(isVisible = false, message = "")

    override fun isSessionExpired(): Boolean = false

    override fun hasCredentials(): Boolean = isDemoMode || hasImportedFileOrAsset()

    private fun hasImportedFileOrAsset(): Boolean =
        File(context.filesDir, "kotak_holdings.csv").exists() ||
            File(context.getExternalFilesDir(null), "kotak_holdings.csv").exists() ||
            runCatching { context.assets.open("kotak_holdings.csv").close() }.isSuccess
}
