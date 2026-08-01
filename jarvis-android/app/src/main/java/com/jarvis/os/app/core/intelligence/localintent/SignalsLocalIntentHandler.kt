package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.signals.repository.SignalRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signal Intelligence Engine: answers "how many active signals", "latest signal", and
 * "signals for <instrument>" directly from [SignalRepository] -- no AI provider needed, every
 * fact here (status, confidence, entry price) is a real column, not a summarized impression.
 */
@Singleton
class SignalsLocalIntentHandler @Inject constructor(
    private val signals: SignalRepository,
    private val instruments: InstrumentRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.SIGNALS

    override suspend fun tryHandle(text: String): LocalIntentAnswer? = answer(text)?.let { LocalIntentAnswer(it) }

    private suspend fun answer(text: String): String? {
        val lower = text.lowercase()
        if (SIGNAL_KEYWORDS.none { it in lower }) return null

        // An instrument name/symbol mentioned alongside "signal" scopes the answer to that
        // instrument; otherwise this reports across all instruments, same "as specific as the
        // owner's own words, no narrower" rule TidbLocalIntentHandler follows.
        val instrument = instruments.observeAll().first().firstOrNull { inst ->
            lower.contains(inst.symbol.lowercase()) || lower.contains(inst.displayName.lowercase())
        }

        if (COUNT_KEYWORDS.any { it in lower } && LATEST_KEYWORDS.none { it in lower }) {
            val activeSignals = if (instrument != null) {
                signals.observeActiveByInstrument(instrument.instrumentId).first()
            } else {
                signals.observeActiveSignals().first()
            }
            val scope = instrument?.let { " for ${it.displayName}" } ?: ""
            return if (activeSignals.isEmpty()) {
                "No active signals$scope right now."
            } else {
                "${activeSignals.size} active signal(s)$scope: " +
                    activeSignals.joinToString("; ") { s -> "${s.signalType.value} (${s.timeframe}, ${(s.confidenceScore * 100).toInt()}% confidence)" } + "."
            }
        }

        val latest = if (instrument != null) {
            signals.observeLatestForInstrument(instrument.instrumentId).first()
        } else {
            signals.observeLatest().first()
        }
        return latest?.let { s ->
            "Latest signal: ${s.signalType.value}, status ${s.status.value}, entry ${s.entryPrice}, confidence ${(s.confidenceScore * 100).toInt()}%, generated ${Instant.ofEpochMilli(s.generatedAt)}."
        } ?: "No signals recorded${instrument?.let { " for ${it.displayName}" } ?: ""} yet."
    }

    companion object {
        private val COUNT_KEYWORDS = setOf("how many", "active signal", "active signals", "signal count")
        private val LATEST_KEYWORDS = setOf("latest signal", "most recent signal", "last signal")
        private val SIGNAL_KEYWORDS = COUNT_KEYWORDS + LATEST_KEYWORDS + setOf("signal", "signals")
    }
}
