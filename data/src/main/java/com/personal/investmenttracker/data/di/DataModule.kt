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
    fun provideZerodhaPortfolioProvider(
        @ApplicationContext context: Context,
        credentialsRepo: KiteCredentialsRepository,
    ): ZerodhaPortfolioProvider {
        val isDemoMode = context.packageName.endsWith(".demo")
        return DelegatingZerodhaPortfolioProvider(credentialsRepo, isDemoMode = isDemoMode)
    }

    @Provides
    @Singleton
    fun provideKotakPortfolioProvider(
        @ApplicationContext context: Context,
        credentialsRepo: KotakCredentialsRepository,
    ): KotakPortfolioProvider {
        val isDemoMode = context.packageName.endsWith(".demo")
        return DelegatingKotakPortfolioProvider(
            context = context,
            credentialsRepo = credentialsRepo,
            isDemoMode = isDemoMode,
        )
    }

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
            .databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
    }
}
