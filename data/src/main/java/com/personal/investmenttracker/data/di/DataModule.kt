package com.personal.investmenttracker.data.di

import android.content.Context
import androidx.room.Room
import com.personal.investmenttracker.data.broker.kotak.DelegatingKotakPortfolioProvider
import com.personal.investmenttracker.data.broker.kotak.KotakCredentialsRepository
import com.personal.investmenttracker.data.broker.kotak.KotakPortfolioProvider
import com.personal.investmenttracker.data.broker.zerodha.DelegatingZerodhaPortfolioProvider
import com.personal.investmenttracker.data.broker.zerodha.KiteCredentialsRepository
import com.personal.investmenttracker.data.broker.zerodha.ZerodhaPortfolioProvider
import com.personal.investmenttracker.data.local.AppDatabase
import com.personal.investmenttracker.data.security.EncryptedDatabaseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideKiteCredentialsRepository(
        @ApplicationContext context: Context,
    ): KiteCredentialsRepository = KiteCredentialsRepository(context)

    @Provides
    @Singleton
    fun provideKotakCredentialsRepository(
        @ApplicationContext context: Context,
    ): KotakCredentialsRepository = KotakCredentialsRepository(context)

    @Provides
    @Singleton
    fun provideZerodhaPortfolioProvider(credentialsRepo: KiteCredentialsRepository): ZerodhaPortfolioProvider =
        DelegatingZerodhaPortfolioProvider(credentialsRepo)

    @Provides
    @Singleton
    fun provideKotakPortfolioProvider(credentialsRepo: KotakCredentialsRepository): KotakPortfolioProvider =
        DelegatingKotakPortfolioProvider(credentialsRepo)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        encryptedDatabaseManager: EncryptedDatabaseManager,
    ): AppDatabase {
        val existing = encryptedDatabaseManager.getDatabase()
        if (existing != null && existing.isOpen) {
            return existing
        }
        return Room
            .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
