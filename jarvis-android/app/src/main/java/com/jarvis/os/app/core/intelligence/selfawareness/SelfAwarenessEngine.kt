package com.jarvis.os.app.core.intelligence.selfawareness

import com.jarvis.os.app.BuildConfig
import com.jarvis.os.app.data.model.CapabilityStatus
import com.jarvis.os.app.data.model.SystemCapabilityRecord
import com.jarvis.os.app.data.repository.GitHubFetchResult
import com.jarvis.os.app.data.repository.GitHubStatusProvider
import com.jarvis.tidb.database.TradingIntelligenceDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 2, Sections 1, 4 and 5 -- Self-Awareness Engine.
 *
 * Everything this class answers is read from [CapabilityInventory] (itself built from real
 * repository/service state -- see that class's own docstring) plus two more compiled, real
 * facts: [BuildConfig.VERSION_NAME] and [TradingIntelligenceDatabase]'s schema constants. This
 * class adds no new data of its own -- it is purely a query surface over
 * [CapabilityInventory.snapshot], shaped for the Mission Status questions Section 4 names
 * explicitly ("Where are we?", "What's missing?", "What's next?", "What's blocking live
 * trading?").
 *
 * Section 5 (Owner Transparency) is implemented structurally, not as a special code path: every
 * [SystemCapabilityRecord] this class reads already carries [SystemCapabilityRecord.risk] and
 * [SystemCapabilityRecord.nextMilestone] for anything not [CapabilityStatus.COMPLETE], so
 * "explain why, which dependency is missing, which milestone builds it" falls out of the same
 * data the capability inventory already produces rather than a second, parallel explanation
 * system that could drift out of sync with it.
 */
@Singleton
class SelfAwarenessEngine @Inject constructor(
    private val capabilityInventory: CapabilityInventory,
    private val gitHub: GitHubStatusProvider,
) {
    suspend fun capabilities(): List<SystemCapabilityRecord> = capabilityInventory.snapshot()

    /** Section 1: repository status / build status / CI status / schema version in one honest sentence. */
    suspend fun repositoryStatus(): String {
        val ciLine = ciStatusLine()
        return "Build ${BuildConfig.VERSION_NAME}. TIDB schema v${TradingIntelligenceDatabase.SCHEMA_VERSION}, " +
            "${TradingIntelligenceDatabase.ENTITY_COUNT} Room entities. $ciLine"
    }

    private fun ciStatusLine(): String {
        val result = gitHub.status.value
        return when (result) {
            is GitHubFetchResult.Success -> {
                val latest = result.status.recentWorkflowRuns.firstOrNull()
                if (latest == null) {
                    "GitHub connected (${result.status.repoFullName}); no workflow runs recorded yet."
                } else {
                    "GitHub connected (${result.status.repoFullName}); latest CI run: ${latest.workflowName} -- " +
                        "${latest.conclusion ?: latest.status}."
                }
            }
            is GitHubFetchResult.Failure -> "GitHub not connected (${result.message}) -- CI status can't be verified from here."
            null -> "GitHub has never been fetched in this session -- CI status can't be verified from here."
        }
    }

    /** Section 4: "Where are we?" */
    suspend fun whereAreWe(): String {
        val caps = capabilities()
        val complete = caps.count { it.status == CapabilityStatus.COMPLETE }
        return "${repositoryStatus()} $complete of ${caps.size} tracked capabilities are COMPLETE. " +
            "Overall build percent: ${overallCompletionPercent(caps)}%."
    }

    /** Section 4: "What is complete?" */
    suspend fun whatIsComplete(): List<SystemCapabilityRecord> =
        capabilities().filter { it.status == CapabilityStatus.COMPLETE }

    /** Section 4: "What is missing?" */
    suspend fun whatIsMissing(): List<SystemCapabilityRecord> =
        capabilities().filter { it.status == CapabilityStatus.MISSING }

    /** Section 4: "What is partial?" (not explicitly named in Section 4 but required by Section 3's report). */
    suspend fun whatIsPartial(): List<SystemCapabilityRecord> =
        capabilities().filter { it.status == CapabilityStatus.PARTIAL }

    /**
     * Section 4: "What is next?" -- every non-COMPLETE capability's own [SystemCapabilityRecord
     * .nextMilestone], deduplicated, in the order [CapabilityInventory.snapshot] declares them
     * (its own dependency-ordered sequence -- see that class's declaration order), never a
     * separately-maintained roadmap that could drift from the inventory it's summarizing.
     */
    suspend fun whatIsNext(): List<String> =
        capabilities().mapNotNull { it.nextMilestone }.distinct()

    /** Section 4: "What can't you do?" / "What is blocking live trading?" */
    suspend fun whatIsBlockingLiveTrading(): String {
        val liveTrading = capabilities().firstOrNull { it.name == "Live Trading" }
            ?: return "Live Trading capability not found in the inventory -- this itself is a Repository Reality gap."
        val blockers = capabilities().filter {
            it.name != "Live Trading" && it.status != CapabilityStatus.COMPLETE &&
                liveTrading.dependency?.contains(it.name) == true
        }
        if (blockers.isEmpty()) return liveTrading.risk ?: "No specific blocker recorded."
        return "Blocking live trading: " + blockers.joinToString("; ") { "${it.name} (${it.status}, ${it.completionPercent}%)" } +
            ". " + (liveTrading.risk ?: "")
    }

    /** Section 4: "What can you do?" -- names of every COMPLETE capability. */
    suspend fun whatCanYouDo(): List<String> = whatIsComplete().map { it.name }

    /** Section 4: "What can't you do?" -- names of every MISSING or PARTIAL capability with its reason. */
    suspend fun whatCantYouDo(): List<String> =
        capabilities().filter { it.status != CapabilityStatus.COMPLETE }
            .map { "${it.name} (${it.status}, ${it.completionPercent}%)${it.risk?.let { r -> " -- $r" } ?: ""}" }

    /** Simple average across tracked capabilities -- the same shape [ExecutiveReportEngine] uses for its overview line. */
    fun overallCompletionPercent(caps: List<SystemCapabilityRecord>): Int =
        if (caps.isEmpty()) 0 else caps.sumOf { it.completionPercent } / caps.size
}
