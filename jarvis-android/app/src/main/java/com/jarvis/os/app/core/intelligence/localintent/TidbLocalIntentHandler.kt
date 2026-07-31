package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.ContractRepository
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.core.repository.LiveMarketSnapshotRepository
import com.jarvis.tidb.database.TradingIntelligenceDatabase
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trading Intelligence Database: answers both (a) per-instrument raw market facts -- latest
 * price, latest candle, nearest active contract -- and (b) "Offline Completion" milestone
 * meta-questions about the database itself -- "how many instruments", "how many candles", "do
 * you have data", "what databases/tables/modules exist" -- directly from Module 1 (Core Market
 * Foundation) repositories and this class's own real, verified package/schema layout. Deliberately
 * NOT the same territory as [com.jarvis.os.app.core.trading.TradingIntelligenceOrchestrator],
 * which JarvisCore already consults first (see its own sendChatMessage priority chain) for
 * "should I buy/sell" style recommendation questions -- that path runs the 13-stage Decision
 * Lifecycle and is real reasoning over evidence, not a plain data lookup, so it stays a distinct,
 * higher-priority branch rather than being folded into this handler. This handler only ever
 * reports what is already recorded or already true about the schema; it never scores,
 * recommends, or infers.
 *
 * Instrument resolution (for the per-instrument branch) is data-driven, not a hardcoded symbol
 * map: every seeded instrument's symbol/displayName is checked against the message, so this
 * handler covers whatever is actually in the database today without needing a code change when
 * more are seeded later -- the same "auditable against real data, not a guess" reasoning
 * JarvisCore.matchTradingInstrumentSymbol's own docstring calls out as the eventual replacement
 * for its current best-guess keyword.
 *
 * Meta-questions are checked FIRST and independently of instrument resolution -- "how many
 * instruments are in the database" isn't about any one instrument, so it must never require one
 * to be named or matched.
 */
@Singleton
class TidbLocalIntentHandler @Inject constructor(
    private val instruments: InstrumentRepository,
    private val contracts: ContractRepository,
    private val candles: HistoricalCandleRepository,
    private val liveSnapshots: LiveMarketSnapshotRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.TIDB

    override suspend fun tryHandle(text: String): LocalIntentAnswer? = answer(text)?.let { LocalIntentAnswer(it) }

    private suspend fun answer(text: String): String? {
        val lower = text.lowercase()

        metaAnswer(lower)?.let { return it }

        if (DATA_KEYWORDS.none { it in lower }) return null

        val instrument = instruments.observeAll().first().firstOrNull { inst ->
            lower.contains(inst.symbol.lowercase()) || lower.contains(inst.displayName.lowercase())
        } ?: return "I checked the Trading Intelligence Database, but that doesn't match any instrument seeded there yet -- I don't have raw market data for it."

        val parts = mutableListOf("${instrument.displayName} (${instrument.symbol}), ${instrument.assetClass.value.lowercase()}.")

        if (PRICE_KEYWORDS.any { it in lower }) {
            val snapshot = liveSnapshots.observeByInstrument(instrument.instrumentId).first()
            parts += if (snapshot != null) {
                "Last recorded price: ${snapshot.lastPrice} (market ${snapshot.marketStatus.value.lowercase()})."
            } else {
                "No live price snapshot recorded for this instrument yet."
            }
        }

        if (CANDLE_KEYWORDS.any { it in lower }) {
            val latest = candles.getLatest(instrument.instrumentId, Timeframe.D1, limit = 1).firstOrNull()
            parts += if (latest != null) {
                "Latest daily candle: open ${latest.open}, high ${latest.high}, low ${latest.low}, close ${latest.close}, recorded ${Instant.ofEpochMilli(latest.timestamp)}."
            } else {
                "No historical candle data recorded for this instrument yet."
            }
        }

        if (CONTRACT_KEYWORDS.any { it in lower }) {
            val contract = contracts.getNearestActiveContract(instrument.instrumentId)
            parts += if (contract != null) {
                "Nearest active contract expires ${Instant.ofEpochMilli(contract.expiryDate)}, rolls ${Instant.ofEpochMilli(contract.rollDate)}."
            } else {
                "No active contract found for this instrument."
            }
        }

        // A message could only match an instrument name/symbol with none of the more specific
        // sub-keywords below (e.g. just "tell me about naturalgas") -- still a real, honest
        // answer using what's on record, not a guessed one.
        return parts.joinToString(" ")
    }

    /** "Offline Completion" milestone, requirement 3: schema/inventory questions about TIDB itself, independent of any single instrument. */
    private suspend fun metaAnswer(lower: String): String? = when {
        "how many instrument" in lower -> {
            val count = instruments.observeAll().first().size
            "There ${if (count == 1) "is" else "are"} $count instrument(s) recorded in the Trading Intelligence Database."
        }
        "how many candle" in lower -> {
            val total = countAllCandles()
            "There ${if (total == 1) "is" else "are"} $total historical candle(s) recorded across all instruments and timeframes."
        }
        "do you have data" in lower || "do you have any data" in lower -> {
            val instrumentCount = instruments.observeAll().first().size
            if (instrumentCount == 0) {
                "No trading data has been imported yet -- the Trading Intelligence Database is currently empty."
            } else {
                "Yes -- $instrumentCount instrument(s) and ${countAllCandles()} historical candle(s) are recorded right now."
            }
        }
        "what database" in lower || "what tables" in lower -> {
            "The Trading Intelligence Database is a single Room database " +
                "(schema v${TradingIntelligenceDatabase.SCHEMA_VERSION}, ${TradingIntelligenceDatabase.ENTITY_COUNT} tables) " +
                "spanning market data, signals, trading analytics, decision intelligence, evidence/pattern intelligence, market context, and news."
        }
        "what modules" in lower -> {
            "TIDB is organized into 8 modules: Core Market Foundation, Signal Intelligence, Trading Analytics & Learning, " +
                "Decision Intelligence, Historical Evidence, Market Intelligence (confidence/graph/pattern/regime/research), Market Context, and News."
        }
        else -> null
    }

    /**
     * [HistoricalCandleRepository] exposes no direct "count everything" query (see its own
     * interface -- only observeRange/getLatest, both scoped to one instrument + timeframe), so
     * this sums real recorded rows across every seeded instrument and every [Timeframe], each
     * queried from epoch 0 to now -- a genuine count of what's actually stored, not an estimate,
     * just assembled from the narrower queries the repository actually offers.
     */
    private suspend fun countAllCandles(): Int {
        val allInstruments = instruments.observeAll().first()
        var total = 0
        val now = System.currentTimeMillis()
        for (inst in allInstruments) {
            for (timeframe in Timeframe.entries) {
                total += candles.observeRange(inst.instrumentId, timeframe, 0L, now).first().size
            }
        }
        return total
    }

    companion object {
        private val PRICE_KEYWORDS = setOf("price", "quote", "trading at", "last traded")
        private val CANDLE_KEYWORDS = setOf("candle", "ohlc", "open high low close")
        private val CONTRACT_KEYWORDS = setOf("contract", "expiry", "expire", "roll date")
        private val DATA_KEYWORDS = PRICE_KEYWORDS + CANDLE_KEYWORDS + CONTRACT_KEYWORDS + setOf("instrument data", "market data for", "tell me about")
    }
}
