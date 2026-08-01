package com.jarvis.os.app.testutil

import com.jarvis.os.app.core.trading.TradingIntelligenceOrchestrator

/**
 * JARVIS-002: the one shared, minimal fake every plain-JVM JarvisCore test needs -- a real
 * [com.jarvis.os.app.core.trading.DefaultTradingIntelligenceOrchestrator] needs a real
 * DecisionLifecycleRunner and full TIDB repository graph, neither of which these tests
 * construct. Always resolves to "no trading match" so every existing test's conversational
 * routing is completely unaffected -- matching [FakeSettingsRepository]'s own "one shared fake
 * rather than near-identical copies scattered per test file" reasoning.
 */
class FakeTradingIntelligenceOrchestrator : TradingIntelligenceOrchestrator {
    override suspend fun askAbout(symbol: String): String? = null
}
