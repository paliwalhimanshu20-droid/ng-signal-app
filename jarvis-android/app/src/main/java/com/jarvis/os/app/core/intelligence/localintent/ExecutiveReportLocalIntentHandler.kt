package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.intelligence.selfawareness.ExecutiveReportEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 2, Section 3 -- Executive Report Engine, routed locally. "Replace generic AI
 * summaries" (Section 3): this handler answers "give me an executive report" entirely from
 * [ExecutiveReportEngine.generate] -- deterministic composition, zero model calls, same as every
 * other handler in this router (see [LocalIntentRouter]'s own class docstring for why an OS-first
 * question never needs to reach an AI provider at all).
 */
@Singleton
class ExecutiveReportLocalIntentHandler @Inject constructor(
    private val executiveReportEngine: ExecutiveReportEngine,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.EXECUTIVE_REPORT

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase().trimEnd('!', '.', '?', ',')
        if (KEYWORDS.none { it in lower }) return null
        val report = executiveReportEngine.generate()
        return LocalIntentAnswer(executiveReportEngine.render(report))
    }

    companion object {
        private val KEYWORDS = setOf(
            "executive report", "executive trading intelligence report", "trading intelligence report",
            "generate executive report", "give me an executive report", "generate a report", "generate report",
            "project dashboard", "capability inventory", "capability discovery",
        )
    }
}
