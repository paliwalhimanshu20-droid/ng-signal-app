package com.jarvis.os.app.core.intelligence.selfawareness

import com.jarvis.os.app.BuildConfig
import com.jarvis.os.app.data.model.CapabilityStatus
import com.jarvis.os.app.data.model.ExecutiveReport
import com.jarvis.os.app.data.model.SystemCapabilityRecord
import com.jarvis.tidb.database.TradingIntelligenceDatabase
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 2, Section 3 -- Executive Report Engine.
 *
 * "Replace generic AI summaries. Generate reports only from Capability Inventory, ... Repository
 * state." This class calls no [com.jarvis.os.app.core.chat.ChatProvider] and performs no model
 * completion of any kind -- every field of [ExecutiveReport] is deterministically assembled from
 * [SelfAwarenessEngine] / [CapabilityInventory], the exact same real state that engine's own
 * Mission Status answers read, so a report and a chat answer about the same subsystem can never
 * disagree with each other.
 *
 * Deliberately distinct from [com.jarvis.os.app.core.intelligence.ExecutiveBriefingEngine]: that
 * class answers "what should the owner know this morning" from personal/owner-facing state
 * (ProjectOS, Watch Tower, notifications, approvals). This class answers "what state is the
 * JARVIS build itself in" from engineering/architecture state (capability completion, repository
 * health, Trust Layer). Both are deterministic-composition Engines in the same house style; they
 * are not a redesign of each other and this slice does not touch [ExecutiveBriefingEngine].
 */
@Singleton
class ExecutiveReportEngine @Inject constructor(
    private val selfAwareness: SelfAwarenessEngine,
) {
    suspend fun generate(): ExecutiveReport {
        val capabilities = selfAwareness.capabilities()
        val complete = capabilities.filter { it.status == CapabilityStatus.COMPLETE }
        val partial = capabilities.filter { it.status == CapabilityStatus.PARTIAL }
        val missing = capabilities.filter { it.status == CapabilityStatus.MISSING }

        val risks = capabilities.mapNotNull { record -> record.risk?.let { "${record.name}: $it" } }
        val recommendations = buildRecommendations(partial, missing)
        val nextMilestone = capabilities.firstOrNull { it.name == "Live Trading" }?.nextMilestone
            ?: "No next milestone recorded."

        return ExecutiveReport(
            generatedAtEpochMillis = Instant.now().toEpochMilli(),
            currentBuild = "JARVIS OS ${BuildConfig.VERSION_NAME} -- Phase 4B Slice 2",
            currentMilestone = "Self-Awareness Engine + Executive Report Engine (this slice)",
            completedWork = complete.map { it.name },
            partialWork = partial.map { "${it.name} (${it.completionPercent}%)" },
            missingWork = missing.map { it.name },
            currentRisks = risks,
            recommendations = recommendations,
            nextMilestone = nextMilestone,
            repositoryHealth = selfAwareness.repositoryStatus(),
            trustLayerSummary = capabilities.firstOrNull { it.name.startsWith("Trust Layer") }
                ?.let { "${it.status}, ${it.completionPercent}% -- ${it.verificationState}" }
                ?: "Trust Layer capability not found in inventory.",
        )
    }

    /**
     * Section 3: "Recommendations." One line per non-COMPLETE capability that has a real
     * [com.jarvis.os.app.data.model.SystemCapabilityRecord.nextMilestone] recorded -- never a
     * generic "keep working on it," always the specific next concrete step that capability's own
     * row already names, in the same dependency order [CapabilityInventory.snapshot] declares.
     */
    private fun buildRecommendations(
        partial: List<SystemCapabilityRecord>,
        missing: List<SystemCapabilityRecord>,
    ): List<String> = (missing + partial)
        .mapNotNull { record -> record.nextMilestone?.let { "${record.name}: $it" } }
        .distinct()

    /** Fixed-template rendering per Section 3/8 ("No generic AI text") -- plain text, chat-ready. */
    fun render(report: ExecutiveReport): String = buildString {
        appendLine("EXECUTIVE REPORT -- ${report.currentBuild}")
        appendLine("Current Milestone: ${report.currentMilestone}")
        appendLine()
        appendLine("Repository Health: ${report.repositoryHealth}")
        appendLine("Trust Layer: ${report.trustLayerSummary}")
        appendLine()
        appendLine("Completed (${report.completedWork.size}): ${report.completedWork.joinToString(", ").ifEmpty { "none" }}")
        appendLine("Partial (${report.partialWork.size}): ${report.partialWork.joinToString(", ").ifEmpty { "none" }}")
        appendLine("Missing (${report.missingWork.size}): ${report.missingWork.joinToString(", ").ifEmpty { "none" }}")
        appendLine()
        if (report.currentRisks.isNotEmpty()) {
            appendLine("Current Risks:")
            report.currentRisks.forEach { appendLine("- $it") }
            appendLine()
        }
        if (report.recommendations.isNotEmpty()) {
            appendLine("Recommendations:")
            report.recommendations.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("Next Milestone: ${report.nextMilestone}")
    }.trim()
}
