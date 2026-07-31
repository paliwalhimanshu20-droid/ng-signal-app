package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.ContractRepository
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.core.repository.LiveMarketSnapshotRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trading Intelligence Database (raw market data): answers questions about an instrument's own
 * recorded facts -- latest price, latest candle, nearest active contract -- directly from Module 1
 * (Core Market Foundation) repositories. Deliberately NOT the same territory as
 * [com.jarvis.os.app.core.trading.TradingIntelligenceOrchestrator], which JarvisCore already
 * consults first (see its own sendChatMessage priority chain) for "should I buy/sell" style
 * recommendation questions -- that path runs the 13-stage Decision Lifecycle and is real
 * reasoning over evidence, not a plain data lookup, so it stays a distinct, higher-priority
 * branch rather than being folded into this handler. This handler only ever reports what is
 * already recorded; it never scores, recommends, or infers.
 *
 * Instrument resolution is data-driven, not a hardcoded symbol map: every seeded instrument's
 * symbol/displayName is checked against the message, so this handler covers whatever is actually
 * in the database today (currently the single September milestone instrument) without needing a
 * code change when more are seeded later -- the same "auditable against real data, not a guess"
 * reasoning JarvisCore.matchTradingInstrumentSymbol's own docstring calls out as the eventual
 * replacement for its current best-guess keyword.
 */
@Singleton
class TidbLocalIntentHandler @Inject constructor(
    private val instruments: InstrumentRepository,
    private val contracts: ContractRepository,
    private val candles: HistoricalCandleRepository,
    private val liveSnapshots: LiveMarketSnapshotRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.TIDB

    override suspend fun tryHandle(text: String): String? {
        val lower = text.lowercase()
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

    companion object {
        private val PRICE_KEYWORDS = setOf("price", "quote", "trading at", "last traded")
        private val CANDLE_KEYWORDS = setOf("candle", "ohlc", "open high low close")
        private val CONTRACT_KEYWORDS = setOf("contract", "expiry", "expire", "roll date")
        private val DATA_KEYWORDS = PRICE_KEYWORDS + CANDLE_KEYWORDS + CONTRACT_KEYWORDS + setOf("instrument data", "market data for", "tell me about")
    }
}
