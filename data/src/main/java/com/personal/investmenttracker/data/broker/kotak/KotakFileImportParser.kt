@file:Suppress("TooManyFunctions")

package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import com.personal.investmenttracker.domain.model.Freshness
import com.personal.investmenttracker.domain.model.Holding
import com.personal.investmenttracker.domain.model.Money
import com.personal.investmenttracker.domain.model.Quantity
import com.personal.investmenttracker.domain.model.Quote
import com.personal.investmenttracker.domain.model.QuoteSource
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal

object KotakFileImportParser {
    fun parseHoldingsCsv(
        inputStream: InputStream,
        timestampMs: Long = System.currentTimeMillis(),
    ): List<Pair<Holding, Quote>> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val lines =
            reader
                .readLines()
                .map { it.trim().removePrefix("\uFEFF") }
                .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            throw KotakFileImportException("Exported file is empty.")
        }

        val headerRowIndex = findHeaderRowIndex(lines)
        if (headerRowIndex == -1) {
            throw KotakFileImportException(
                "Could not locate valid header row in Kotak export file. Expected Symbol/ISIN and Quantity.",
            )
        }

        val delimiter = if (lines[headerRowIndex].contains("\t")) '\t' else ','
        val headers = splitRow(lines[headerRowIndex], delimiter)
        val indices = resolveColumnIndices(headers)

        val results = mutableListOf<Pair<Holding, Quote>>()
        for (i in (headerRowIndex + 1) until lines.size) {
            val row = splitRow(lines[i], delimiter)
            val parsed = parseRow(row, indices, timestampMs)
            if (parsed != null) {
                results.add(parsed)
            }
        }

        return results
    }
}

private data class KotakColumnIndices(
    val isinIndex: Int,
    val symbolIndex: Int,
    val exchangeIndex: Int,
    val qtyIndex: Int,
    val avgPriceIndex: Int,
    val ltpIndex: Int,
    val closePriceIndex: Int,
    val todayPnlPctIndex: Int = -1,
    val todayPnlIndex: Int = -1,
)

private fun findHeaderRowIndex(lines: List<String>): Int {
    for (i in lines.indices) {
        val lower = lines[i].lowercase()
        val hasSymbolOrIsin =
            lower.contains("symbol") ||
                lower.contains("isin") ||
                lower.contains("scrip") ||
                lower.contains("name") ||
                lower.contains("instrument") ||
                lower.contains("stock")
        val hasQty = lower.contains("qty") || lower.contains("quantity") || lower.contains("holding")
        if (hasSymbolOrIsin && hasQty) {
            return i
        }
    }
    return -1
}

private fun splitRow(
    line: String,
    delimiter: Char,
): List<String> {
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    var insideQuotes = false

    for (ch in line) {
        when {
            ch == '"' -> insideQuotes = !insideQuotes
            ch == delimiter && !insideQuotes -> {
                tokens.add(sb.toString().trim().removeSurrounding("\""))
                sb.setLength(0)
            }
            else -> sb.append(ch)
        }
    }
    tokens.add(sb.toString().trim().removeSurrounding("\""))
    return tokens
}

private fun matchHeaderIndex(
    headers: List<String>,
    keywords: List<String>,
): Int {
    for ((idx, h) in headers.withIndex()) {
        val clean = h.lowercase().replace(" ", "").replace("_", "")
        if (keywords.any { clean.contains(it) }) return idx
    }
    return -1
}

private fun resolveColumnIndices(headers: List<String>): KotakColumnIndices {
    val isinIdx = matchHeaderIndex(headers, listOf("isin"))
    val symbolIdx = matchHeaderIndex(headers, listOf("symbol", "scrip", "name", "instrument", "stock"))
    val exchangeIdx = matchHeaderIndex(headers, listOf("exchange"))
    val qtyIdx = matchHeaderIndex(headers, listOf("qty", "quantity", "holding"))
    val avgPriceIdx = matchHeaderIndex(headers, listOf("avg", "average", "buyprice", "cost"))
    val ltpIdx = matchHeaderIndex(headers, listOf("ltp", "lastprice", "currentprice"))
    val closePriceIdx = matchHeaderIndex(headers, listOf("close", "prevclose"))
    val todayPnlPctIdx =
        matchHeaderIndex(
            headers,
            listOf("todaysprofit/loss%", "todaypnl%", "todaypnlpct", "daychange%"),
        )
    val todayPnlIdx = matchHeaderIndex(headers, listOf("todaysprofit/loss", "todaypnl", "daychange"))

    if (symbolIdx == -1 && isinIdx == -1) {
        throw KotakFileImportException("Missing symbol and ISIN column in exported file.")
    }
    if (qtyIdx == -1) {
        throw KotakFileImportException("Missing quantity column in exported file.")
    }

    return KotakColumnIndices(
        isinIndex = isinIdx,
        symbolIndex = symbolIdx,
        exchangeIndex = exchangeIdx,
        qtyIndex = qtyIdx,
        avgPriceIndex = avgPriceIdx,
        ltpIndex = ltpIdx,
        closePriceIndex = closePriceIdx,
        todayPnlPctIndex = todayPnlPctIdx,
        todayPnlIndex = todayPnlIdx,
    )
}

private fun extractQuantity(
    row: List<String>,
    qtyIndex: Int,
): BigDecimal? {
    if (qtyIndex !in row.indices) return null
    return try {
        BigDecimal(cleanNumeric(row[qtyIndex]))
    } catch (_: NumberFormatException) {
        null
    }
}

private fun extractSymbolAndIsin(
    row: List<String>,
    indices: KotakColumnIndices,
): Pair<String, String>? {
    val rawSymbol = if (indices.symbolIndex in row.indices) row[indices.symbolIndex].trim() else ""
    val rawIsin = if (indices.isinIndex in row.indices) row[indices.isinIndex].trim() else ""
    val isInvalid =
        (rawSymbol.isBlank() && rawIsin.isBlank()) ||
            rawSymbol.equals("total", ignoreCase = true) ||
            rawSymbol.equals("-", ignoreCase = true)
    if (isInvalid) return null
    val effectiveIsin = if (rawIsin.isNotBlank()) rawIsin else rawSymbol
    val effectiveSymbol = if (rawSymbol.isNotBlank()) rawSymbol else rawIsin
    return Pair(effectiveSymbol, effectiveIsin)
}

private fun computeClosePrice(
    row: List<String>,
    indices: KotakColumnIndices,
    ltpMoney: Money,
): Money {
    val directClose = parseMoneyOrNull(row, indices.closePriceIndex)
    if (directClose != null) return directClose

    val pctVal =
        if (indices.todayPnlPctIndex in row.indices) {
            parsePercentValue(row[indices.todayPnlPctIndex])
        } else {
            null
        }

    return if (ltpMoney.amount > BigDecimal.ZERO && pctVal != null) {
        val factor = BigDecimal.ONE.add(pctVal.divide(BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP))
        if (factor.compareTo(BigDecimal.ZERO) > 0) {
            Money(ltpMoney.amount.divide(factor, 2, java.math.RoundingMode.HALF_UP))
        } else {
            ltpMoney
        }
    } else {
        ltpMoney
    }
}

private fun parseRow(
    row: List<String>,
    indices: KotakColumnIndices,
    timestampMs: Long,
): Pair<Holding, Quote>? {
    val qtyBigDecimal = extractQuantity(row, indices.qtyIndex)
    val identity = extractSymbolAndIsin(row, indices)
    if (qtyBigDecimal == null || identity == null) {
        return null
    }

    val (symbol, isin) = identity
    val isBse = indices.exchangeIndex in row.indices && row[indices.exchangeIndex].contains("BSE", ignoreCase = true)
    val exchange = if (isBse) Exchange.BSE else Exchange.NSE
    val ltpMoney = parseMoneyOrNull(row, indices.ltpIndex) ?: Money.ZERO
    val closePriceMoney = computeClosePrice(row, indices, ltpMoney)

    val quote =
        Quote(
            price = ltpMoney,
            prevClose = closePriceMoney,
            closePrice = closePriceMoney,
            source = QuoteSource.BROKER_CACHE,
            sourceTimestamp = timestampMs,
            freshness = Freshness.DELAYED,
        )
    val avgPriceMoney = parseMoneyOrNull(row, indices.avgPriceIndex)

    val holding =
        Holding(
            broker = Broker.KOTAK_NEO,
            isin = isin,
            exchange = exchange,
            symbol = symbol,
            quantity = Quantity(qtyBigDecimal),
            avgPriceReported = avgPriceMoney,
            quote = quote,
        )
    return Pair(holding, quote)
}

private fun parseMoneyOrNull(
    row: List<String>,
    index: Int,
): Money? {
    if (index == -1 || index >= row.size) return null
    val cleaned = cleanNumeric(row[index])
    return try {
        val amount = BigDecimal(cleaned)
        if (amount.compareTo(BigDecimal.ZERO) > 0) Money(amount) else null
    } catch (_: NumberFormatException) {
        null
    }
}

private fun parsePercentValue(value: String): BigDecimal? =
    try {
        val clean = cleanNumeric(value)
        if (clean.isBlank()) null else BigDecimal(clean)
    } catch (_: NumberFormatException) {
        null
    }

private fun cleanNumeric(value: String): String =
    value
        .replace(",", "")
        .replace("₹", "")
        .replace("%", "")
        .replace("Rs.", "")
        .replace("Rs", "")
        .trim()
