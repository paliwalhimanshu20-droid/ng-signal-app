package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.historical.ingestion.status.DatasetStatus
import com.jarvis.tidb.historical.ingestion.status.DatasetStatusEngine
import com.jarvis.tidb.historical.ingestion.status.ValidationStatus
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Phase 4A Increment 2, Sections 2+6+7 -- Import Reporting, JARVIS Data Awareness, Source
 * Verification Guard." Every response here is built from a fresh
 * [DatasetStatusEngine.statusFor] call -- never a cached or remembered value -- which IS the
 * Source Verification Guard: before this class can say "we have historical data," it has already
 * queried the real provider/dataset/record-count/date-range/validation-status behind that claim,
 * because there is no other way for [DatasetStatusEngine] to have produced the numbers this
 * class renders. There is no code path in this handler that asserts data exists without having
 * just checked.
 *
 * Answers WITHOUT the AI provider -- see [LocalIntentOutcome.LOCAL_ONLY]'s own contract -- because
 * Section 6 is explicit: "WITHOUT using AI guesses... always query real pipeline state."
 */
@Singleton
class DatasetStatusLocalIntentHandler @Inject constructor(
    private val instruments: InstrumentRepository,
    private val datasetStatusEngine: DatasetStatusEngine,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.DATASET_STATUS

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase()
        if (TRIGGER_PHRASES.none { it in lower }) return null

        val instrument = instruments.observeAll().first().firstOrNull { inst ->
            lower.contains(inst.displayName.lowercase()) || lower.contains(inst.symbol.lowercase())
        } ?: return LocalIntentAnswer(
            "I can check that, but you'll need to name an instrument -- I don't have a default one to assume for a dataset-status question.",
        )

        // "Multi-timeframe support" means a real dataset question needs a real timeframe; D1 is
        // the most common default for "do we have historical data" style questions and is
        // documented here as an assumption, not silently guessed -- a caller who means a
        // different timeframe should name it (a natural follow-up for this handler's future).
        val timeframe = Timeframe.D1
        val status = datasetStatusEngine.statusFor(instrument.instrumentId, timeframe)

        return LocalIntentAnswer(render(instrument.displayName, timeframe, status))
    }

    private fun render(instrumentName: String, timeframe: Timeframe, status: DatasetStatus): String {
        if (status.importedCandleCount == 0) {
            // "Source Verification Guard": never claim data exists unless verified -- this is the honest, explicit negative case.
            return "I checked the Trading Intelligence Database directly -- there is no historical data stored for $instrumentName at ${timeframe.value} yet. " +
                "Nothing has been imported for this instrument/timeframe."
        }

        val percentLabel = "${"%.0f".format(status.indicatorCompletionPercent)}%"
        return buildString {
            append("$instrumentName (${timeframe.value}) -- verified against the live database, not cached: ")
            append("${status.importedCandleCount} candle(s) stored, from ${formatTimestamp(status.earliestCandle)} to ${formatTimestamp(status.latestCandle)}. ")
            append("Validation: ${status.validationStatus} (${status.duplicateCount} duplicate(s), ${status.missingCount} missing). ")
            append("Indicators: $percentLabel complete (${status.indicatorTypesCompleted}/26 types have stored values). ")
            append("Import status: ${status.importStatus}, last import ${formatTimestamp(status.lastImportTime)}. ")
            append("Optimization: ${status.optimizationStatus}. Backtest: ${status.backtestStatus}. Evidence: ${status.evidenceStatus}. ")
            append(
                if (status.readyForOptimization) {
                    "This dataset IS ready for optimization -- data exists, validation passed, and all 26 indicators have values."
                } else {
                    "This dataset is NOT yet ready for optimization -- " + notReadyReason(status) + "."
                },
            )
        }
    }

    private fun notReadyReason(status: DatasetStatus): String {
        val reasons = mutableListOf<String>()
        if (status.importedCandleCount == 0) reasons += "no candles are stored"
        if (status.validationStatus != ValidationStatus.PASS) reasons += "validation is ${status.validationStatus.name.lowercase()}"
        if (status.indicatorCompletionPercent < 100.0) reasons += "indicators are only ${status.indicatorTypesCompleted}/26 complete"
        return reasons.joinToString("; ")
    }

    private fun formatTimestamp(epochMillis: Long?): String =
        epochMillis?.let { DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(it)) } ?: "unknown"

    companion object {
        private val TRIGGER_PHRASES = setOf(
            "how many historical candles", "how many candles exist", "what date range",
            "ready for optimization", "how many indicators have been generated",
            "indicators have been generated", "is validation complete", "validation status",
            "when was the last import", "last import", "dataset status", "dataset ready",
        )
    }
}
