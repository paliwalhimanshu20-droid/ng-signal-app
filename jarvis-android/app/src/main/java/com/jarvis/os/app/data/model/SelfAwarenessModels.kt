package com.jarvis.os.app.data.model

/**
 * Phase 4B Slice 2, Section 2 -- Capability Inventory.
 *
 * One row per JARVIS subsystem, built by [com.jarvis.os.app.core.intelligence.selfawareness
 * .CapabilityInventory] from real repository/service state -- never a hand-typed guess about
 * what "feels" complete. Deliberately a *different* model from [AiCapability] (see that enum's
 * own docstring): [AiCapability] is a closed vocabulary of what an AI provider/agent can be
 * routed work for; [SystemCapabilityRecord] is a self-description of what JARVIS itself, as a
 * system, has actually built -- the two answer different questions and neither can substitute
 * for the other, per this codebase's "extend by addition, don't overload an existing type"
 * convention (see AiCapability's own docstring for that same reasoning applied there).
 */
enum class CapabilityStatus { COMPLETE, PARTIAL, MISSING }

/**
 * [completionPercent] is always derived from a concrete, cited signal (a repository count, a
 * status enum, or a class's own presence in this compiled binary) -- see each capability builder
 * function in [com.jarvis.os.app.core.intelligence.selfawareness.CapabilityInventory] for exactly
 * which signal backs which row. [verificationState] states in one sentence what evidence was
 * actually checked, so "Never hallucinate" (Section 1) is auditable rather than just promised.
 */
data class SystemCapabilityRecord(
    val name: String,
    val description: String,
    val status: CapabilityStatus,
    val dependency: String?,
    val completionPercent: Int,
    val nextMilestone: String?,
    val risk: String?,
    val verificationState: String,
)

/**
 * Phase 4B Slice 2, Section 3 -- Executive Report Engine output. Every field is assembled
 * verbatim from [SystemCapabilityRecord]s and other real, already-governed state -- see
 * [com.jarvis.os.app.core.intelligence.selfawareness.ExecutiveReportEngine] for composition.
 * "No generic AI text" (Section 3): this is a deterministic data class rendered to a fixed
 * template, never a model completion.
 */
data class ExecutiveReport(
    val generatedAtEpochMillis: Long,
    val currentBuild: String,
    val currentMilestone: String,
    val completedWork: List<String>,
    val partialWork: List<String>,
    val missingWork: List<String>,
    val currentRisks: List<String>,
    val recommendations: List<String>,
    val nextMilestone: String,
    val repositoryHealth: String,
    val trustLayerSummary: String,
    /**
     * Phase 4B Slice 3, Step 6 addition: "Completed Backtests, Optimization Jobs, Winning
     * Strategy, Best Metrics, Evidence Summary." Deliberately a single rendered string, not five
     * new typed fields -- see [ExecutiveReportEngine.backtestOptimizationSummary]'s own doc for
     * why one composed summary, matching every other field in this data class, is the right shape
     * here rather than a parallel structured model this report's [ExecutiveReportEngine.render]
     * would need bespoke handling for.
     */
    val backtestOptimizationSummary: String,
)
