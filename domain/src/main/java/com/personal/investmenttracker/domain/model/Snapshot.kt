package com.personal.investmenttracker.domain.model

data class SnapshotItem(
    val isin: String,
    val exchange: Exchange,
    val symbol: String,
    val broker: Broker,
    val quantity: Quantity,
    val closePrice: Money,
    val value: Money,
    val sourceTimestamp: Long,
)

data class EodSnapshot(
    val date: String,
    val items: List<SnapshotItem>,
) {
    val totalValue: Money
        get() = items.fold(Money.ZERO) { acc, item -> acc + item.value }
}

enum class PnlCompleteness {
    COMPLETE,
    ESTIMATED,
    INCOMPLETE,
}

data class DailyPnl(
    val date: String,
    val totalValue: Money,
    val pnlAmount: Money,
    val netTradeCashFlow: Money,
    val completeness: PnlCompleteness,
)
