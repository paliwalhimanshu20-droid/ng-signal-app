package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.analytics.repository.PerformanceRepository
import com.jarvis.tidb.analytics.repository.PortfolioRepository
import com.jarvis.tidb.analytics.repository.TradeRepository
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trading Analytics & Learning: answers "open trades", "portfolio", and "performance/P&L"
 * questions directly from [TradeRepository], [PortfolioRepository], and [PerformanceRepository].
 * Scope is deliberately read-only reporting of recorded numbers -- it never explains WHY
 * performance looks a certain way (that's the kind of open-ended reasoning this whole router
 * exists to route TO an AI provider, not around one).
 */
@Singleton
class AnalyticsLocalIntentHandler @Inject constructor(
    private val trades: TradeRepository,
    private val portfolios: PortfolioRepository,
    private val performance: PerformanceRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.ANALYTICS

    override suspend fun tryHandle(text: String): LocalIntentAnswer? = answer(text)?.let { LocalIntentAnswer(it) }

    private suspend fun answer(text: String): String? {
        val lower = text.lowercase()
        return when {
            OPEN_TRADE_KEYWORDS.any { it in lower } -> handleOpenTrades()
            PORTFOLIO_KEYWORDS.any { it in lower } -> handlePortfolios()
            PERFORMANCE_KEYWORDS.any { it in lower } -> handlePerformance()
            else -> null
        }
    }

    private suspend fun handleOpenTrades(): String {
        val open = trades.observeOpenTrades().first()
        if (open.isEmpty()) return "No open trades right now."
        return "${open.size} open trade(s): " +
            open.joinToString("; ") { t -> "${t.direction} ${t.totalQuantity}@${t.plannedEntryPrice} (status ${t.status})" } + "."
    }

    private suspend fun handlePortfolios(): String {
        val all = portfolios.observePortfolios().first()
        if (all.isEmpty()) return "No portfolios recorded yet."
        return all.joinToString(" ") { p ->
            "${p.name}: cash ${p.cashBalance} ${p.baseCurrency}, exposure ${p.totalExposure}, unrealized P&L ${p.unrealizedPnl}, realized P&L ${p.realizedPnl}."
        }
    }

    private suspend fun handlePerformance(): String {
        val currentMonth = YearMonth.now().toString()
        val monthly = performance.getMonthlyPerformance(currentMonth)
        return if (monthly != null) {
            "This month (${monthly.yearMonth}): ${monthly.totalTrades} trade(s), ${monthly.winningTrades} winning, " +
                "${monthly.losingTrades} losing, win rate ${"%.1f".format(monthly.winRate * 100)}%, net profit ${monthly.netProfit}."
        } else {
            "No performance snapshot recorded for $currentMonth yet."
        }
    }

    companion object {
        private val OPEN_TRADE_KEYWORDS = setOf("open trade", "open trades", "open position", "open positions")
        private val PORTFOLIO_KEYWORDS = setOf("portfolio", "portfolios", "cash balance", "exposure")
        private val PERFORMANCE_KEYWORDS = setOf("performance", "p&l", "pnl", "profit and loss", "win rate", "how am i doing")
    }
}
