package com.personal.investmenttracker.data.broker.kotak

import com.personal.investmenttracker.domain.model.Broker
import com.personal.investmenttracker.domain.model.Exchange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal

class KotakFileImportParserTest {
    @Test
    fun parseStandardCsv_returnsExpectedHoldings() {
        val csv =
            """
            ISIN,Symbol,Exchange,Quantity,Average Price,LTP,Close Price
            INE062A01020,SBIN,BSE,5,110.00,120.00,120.00
            INE009A01021,INFY,NSE,50,1450.00,1520.00,1500.00
            """.trimIndent()

        val results = KotakFileImportParser.parseHoldingsCsv(ByteArrayInputStream(csv.toByteArray()))

        assertEquals(2, results.size)

        val sbin = results[0].first
        val sbinQuote = results[0].second

        assertEquals(Broker.KOTAK_NEO, sbin.broker)
        assertEquals("INE062A01020", sbin.isin)
        assertEquals("SBIN", sbin.symbol)
        assertEquals(Exchange.BSE, sbin.exchange)
        assertEquals(BigDecimal.valueOf(5), sbin.quantity.value)
        assertEquals("110.00", sbin.avgPriceReported!!.amount.toPlainString())
        assertEquals("120.00", sbinQuote.price.amount.toPlainString())
        assertEquals("120.00", sbinQuote.closePrice!!.amount.toPlainString())

        val infy = results[1].first
        assertEquals(Broker.KOTAK_NEO, infy.broker)
        assertEquals("INFY", infy.symbol)
        assertEquals(Exchange.NSE, infy.exchange)
        assertEquals(BigDecimal.valueOf(50), infy.quantity.value)
    }

    @Test
    fun parseFormattedValuesWithCurrencyAndCommas_parsesCorrectly() {
        val csv =
            """
            ISIN,Symbol,Exchange,Quantity,Average Price,LTP,Close Price
            INE467B01029,TCS,NSE,"1,000","₹3,800.50","₹3,950.00","₹3,900.00"
            """.trimIndent()

        val results = KotakFileImportParser.parseHoldingsCsv(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(1, results.size)

        val tcs = results[0].first
        val quote = results[0].second

        assertEquals(BigDecimal("1000"), tcs.quantity.value)
        assertEquals("3800.50", tcs.avgPriceReported!!.amount.toPlainString())
        assertEquals("3950.00", quote.price.amount.toPlainString())
        assertEquals("3900.00", quote.closePrice!!.amount.toPlainString())
    }

    @Test
    fun parseKotakNeoWebExportFormat_withDisclaimers_parsesCorrectly() {
        val csv =
            """
            Kotak Neo Portfolio Holdings Export
            Generated On: 2026-10-21
            Scrip Name,ISIN,Series,Qty,Buy Avg,LTP,Prev Close
            SBIN,INE062A01020,EQ,5,110.00,120.00,120.00
            HDFCBANK,INE040A01034,EQ,25,1600.00,1670.00,1650.00
            """.trimIndent()

        val results = KotakFileImportParser.parseHoldingsCsv(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(2, results.size)
        assertEquals("SBIN", results[0].first.symbol)
        assertEquals("INE062A01020", results[0].first.isin)
        assertEquals(BigDecimal("5"), results[0].first.quantity.value)
        assertEquals("HDFCBANK", results[1].first.symbol)
        assertEquals(BigDecimal("25"), results[1].first.quantity.value)
    }

    @Test
    fun parseTsvFormat_parsesCorrectly() {
        val tsv = "ISIN\tSymbol\tQty\tAvg Price\tLTP\nINE062A01020\tSBIN\t5\t110.00\t120.00"
        val results = KotakFileImportParser.parseHoldingsCsv(ByteArrayInputStream(tsv.toByteArray()))

        assertEquals(1, results.size)
        assertEquals("SBIN", results[0].first.symbol)
        assertEquals(BigDecimal("5"), results[0].first.quantity.value)
    }

    @Test
    fun parseNullAvgPrice_setsCostToNull() {
        val csv =
            """
            ISIN,Symbol,Qty,Avg Price,LTP
            INE062A01020,SBIN,5,0.00,120.00
            """.trimIndent()

        val results = KotakFileImportParser.parseHoldingsCsv(ByteArrayInputStream(csv.toByteArray()))
        assertEquals(1, results.size)
        assertNull(results[0].first.avgPriceReported)
    }

    @Test
    fun emptyFile_throwsException() {
        val emptyStream = ByteArrayInputStream("".toByteArray())
        assertThrows(KotakFileImportException::class.java) {
            KotakFileImportParser.parseHoldingsCsv(emptyStream)
        }
    }

    @Test
    fun missingHeaders_throwsException() {
        val invalidStream = ByteArrayInputStream("Col1,Col2,Col3\n1,2,3".toByteArray())
        assertThrows(KotakFileImportException::class.java) {
            KotakFileImportParser.parseHoldingsCsv(invalidStream)
        }
    }
}
