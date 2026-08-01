package com.jarvis.tidb.historical.ingestion.datasource

import com.jarvis.tidb.core.entity.Timeframe
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** "Phase 4A, Section 1 -- Data Source Framework." Uses real temp files via `java.io.File` -- not an Android stub, so this behaves identically here and on a real device (see this class's own docstring on why that distinction matters in this project). */
class CsvDataSourceProviderTest {

    private fun tempCsv(content: String): File {
        val file = File.createTempFile("candles", ".csv")
        file.writeText(content)
        file.deleteOnExit()
        return file
    }

    @Test
    fun `parses a well-formed CSV with a header row`() = runTest {
        val file = tempCsv(
            """
            timestamp,open,high,low,close,volume
            1000,100.0,101.0,99.0,100.5,5000
            2000,100.5,102.0,100.0,101.5,6000
            """.trimIndent(),
        )
        val provider = CsvDataSourceProvider(CsvPathResolver { _, _ -> file.absolutePath })

        val result = provider.fetchCandles(1L, Timeframe.M1, 0L, 10_000L)

        assertEquals(2, result.size)
        assertEquals(1000L, result[0].timestamp)
        assertEquals(100.0, result[0].open, 0.0001)
        assertEquals(6000L, result[1].volume)
    }

    @Test
    fun `parses a CSV with no header row`() = runTest {
        val file = tempCsv("1000,100.0,101.0,99.0,100.5,5000")
        val provider = CsvDataSourceProvider(CsvPathResolver { _, _ -> file.absolutePath })

        val result = provider.fetchCandles(1L, Timeframe.M1, 0L, 10_000L)

        assertEquals(1, result.size)
        assertEquals(1000L, result.first().timestamp)
    }

    @Test
    fun `rows outside the requested range are filtered out`() = runTest {
        val file = tempCsv(
            """
            1000,100.0,101.0,99.0,100.5,5000
            50000,100.0,101.0,99.0,100.5,5000
            """.trimIndent(),
        )
        val provider = CsvDataSourceProvider(CsvPathResolver { _, _ -> file.absolutePath })

        val result = provider.fetchCandles(1L, Timeframe.M1, from = 0L, to = 10_000L)

        assertEquals(1, result.size)
        assertEquals(1000L, result.first().timestamp)
    }

    @Test
    fun `malformed lines are silently skipped, not thrown`() = runTest {
        val file = tempCsv(
            """
            1000,100.0,101.0,99.0,100.5,5000
            not,a,valid,csv,line,here
            2000,100.5,102.0,100.0,101.5,6000
            """.trimIndent(),
        )
        val provider = CsvDataSourceProvider(CsvPathResolver { _, _ -> file.absolutePath })

        val result = provider.fetchCandles(1L, Timeframe.M1, 0L, 10_000L)

        assertEquals(2, result.size)
    }

    @Test
    fun `a missing file throws FileNotFoundException rather than returning an empty list silently`() = runTest {
        val provider = CsvDataSourceProvider(CsvPathResolver { _, _ -> "/definitely/not/a/real/path.csv" })

        try {
            provider.fetchCandles(1L, Timeframe.M1, 0L, 10_000L)
            org.junit.Assert.fail("expected FileNotFoundException")
        } catch (e: java.io.FileNotFoundException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }
}
