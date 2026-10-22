package com.personal.investmenttracker.data.sync

import com.personal.investmenttracker.domain.model.Broker

sealed class SyncStatus {
    data class Success(
        val holdingsCount: Int,
        val syncedAt: Long,
    ) : SyncStatus()

    data class Failure(
        val error: String,
        val lastGoodDataRetained: Boolean = true,
    ) : SyncStatus()
}

data class BrokerSyncResult(
    val broker: Broker,
    val status: SyncStatus,
)
