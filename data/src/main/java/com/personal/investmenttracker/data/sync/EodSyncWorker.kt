package com.personal.investmenttracker.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class EodSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EodSyncWorkerEntryPoint {
        fun syncOrchestrator(): SyncOrchestrator
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result =
        try {
            val entryPoint =
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    EodSyncWorkerEntryPoint::class.java,
                )
            val orchestrator = entryPoint.syncOrchestrator()
            orchestrator.syncAll(triggerEodIfAfterClose = true)
            Result.success()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("EodSyncWorker", "Periodic EOD sync failed, retrying", e)
            Result.retry()
        }

    companion object {
        const val WORK_NAME = "eod_sync_worker"

        fun scheduleDailyEodWork(
            context: Context,
            targetHour: Int = 15,
            targetMinute: Int = 45,
        ) {
            val nowIst = ZonedDateTime.now(EodSnapshotManager.IST_ZONE)
            val targetTime = LocalTime.of(targetHour, targetMinute)
            var targetDateTime = nowIst.with(targetTime)

            if (nowIst.isAfter(targetDateTime)) {
                targetDateTime = targetDateTime.plusDays(1)
            }

            val initialDelayMinutes = Duration.between(nowIst, targetDateTime).toMinutes()

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<EodSyncWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
        }
    }
}
