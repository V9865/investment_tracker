package com.personal.investmenttracker.data.di

import android.content.Context
import com.personal.investmenttracker.data.security.EncryptedDatabaseManager
import com.personal.investmenttracker.data.security.MasterKeyManager
import com.personal.investmenttracker.data.security.SessionLockManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
    ): MasterKeyManager = MasterKeyManager(context)

    @Provides
    @Singleton
    fun provideEncryptedDatabaseManager(
        @ApplicationContext context: Context,
    ): EncryptedDatabaseManager = EncryptedDatabaseManager(context)

    @Provides
    @Singleton
    fun provideSessionLockManager(encryptedDatabaseManager: EncryptedDatabaseManager): SessionLockManager =
        SessionLockManager(encryptedDatabaseManager)
}
