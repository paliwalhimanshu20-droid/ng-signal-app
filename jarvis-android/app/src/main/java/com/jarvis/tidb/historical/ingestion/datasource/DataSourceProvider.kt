package com.jarvis.tidb.historical.ingestion.datasource

import com.jarvis.tidb.core.entity.Timeframe
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Phase 4A, Section 1 -- Data Source Framework." A raw, unvalidated candle as read directly
 * from a provider -- deliberately NOT [com.jarvis.tidb.core.entity.HistoricalCandleEntity]. That
 * distinction is the point: a provider's job is only to read and hand back rows exactly as it
 * found them; [com.jarvis.tidb.historical.ingestion.validation.CandleValidator] (not this class,
 * not any [DataSourceProvider] implementation) is the only place raw data becomes something
 * trusted enough to store.
 */
data class RawCandle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)

/**
 * One implementation per historical data source -- CSV, Parquet, SQLite, REST/broker APIs, and
 * whatever comes after. "No provider-specific logic inside the pipeline" is the literal contract
 * this interface exists to enforce: [com.jarvis.tidb.historical.ingestion.pipeline.
 * HistoricalDataImportPipeline] only ever calls [fetchCandles] and never branches on
 * [providerCode] or on which concrete provider it was given -- adding a Parquet or REST provider
 * later is (1) implement this interface, (2) register it, same swap-point pattern as
 * [com.jarvis.tidb.historical.indicator.calc.IndicatorCalculator] /
 * [com.jarvis.tidb.optimization.searchspace.SearchSpaceProvider] elsewhere in this codebase.
 */
interface DataSourceProvider {
    /** Matches [com.jarvis.tidb.historical.ingestion.entity.DataProviderEntity.providerCode] -- the same real provider-registry row this fetch's [com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity] is tracked under. */
    val providerCode: String

    /** Returns whatever rows exist for [instrumentId]/[timeframe] in `[from, to]`, in whatever order the source happens to produce them -- ordering, deduplication, and validation are deliberately NOT this method's job; see this file's own class docs. */
    suspend fun fetchCandles(instrumentId: Long, timeframe: Timeframe, from: Long, to: Long): List<RawCandle>
}

/** Resolves where a given instrument/timeframe's CSV file lives -- kept as its own small interface (rather than a hardcoded path convention inside [CsvDataSourceProvider]) so different deployments (a bundled-assets convention, a user-downloads-folder convention, a per-broker-export convention) can supply their own without this provider caring. */
fun interface CsvPathResolver {
    fun resolve(instrumentId: Long, timeframe: Timeframe): String
}

/**
 * A real, working CSV provider -- not a stub. Expects one candle per line, comma-separated:
 * `timestamp,open,high,low,close,volume` (timestamp in epoch millis), with an optional header row
 * (tolerated by skipping the first line if it doesn't start with a digit). [pathResolver] is the
 * one piece of "where do CSVs live" this class doesn't hardcode -- callers supply their own
 * per-instrument/per-timeframe file-naming convention.
 *
 * Uses plain `java.io.File`, not any Android-specific storage API -- deliberately, so this class
 * behaves identically on a real device and in a plain JVM unit test, unlike `android.util.Log` or
 * `org.json.JSONObject` earlier in this project's history (see those fixes' own notes for why
 * that distinction matters in this codebase's test environment).
 */
@Singleton
class CsvDataSourceProvider @Inject constructor(
    private val pathResolver: CsvPathResolver,
) : DataSourceProvider {
    override val providerCode: String = "CSV"

    override suspend fun fetchCandles(instrumentId: Long, timeframe: Timeframe, from: Long, to: Long): List<RawCandle> {
        val path = pathResolver.resolve(instrumentId, timeframe)
        val file = File(path)
        if (!file.exists()) throw FileNotFoundException("No CSV file found for instrument $instrumentId at '$path'.")

        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val dataLines = if (lines.first().firstOrNull()?.isDigit() == true) lines else lines.drop(1)

        return dataLines.mapNotNull(::parseLine).filter { it.timestamp in from..to }
    }

    private fun parseLine(line: String): RawCandle? {
        val parts = line.split(",").map { it.trim() }
        if (parts.size < 6) return null
        return try {
            RawCandle(
                timestamp = parts[0].toLong(),
                open = parts[1].toDouble(),
                high = parts[2].toDouble(),
                low = parts[3].toDouble(),
                close = parts[4].toDouble(),
                volume = parts[5].toDouble().toLong(),
            )
        } catch (e: NumberFormatException) {
            null
        }
    }
}
