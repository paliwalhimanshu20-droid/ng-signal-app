package com.jarvis.os.app.data.model

// --- Sprint 10: AI Intelligence (Context + Planning) ---------------------------------

/**
 * Sprint 10 "Context understanding" / "Knowledge retrieval" deliverable
 * -- everything ContextManager assembled for one chat turn, in one
 * immutable snapshot a ChatProvider or a future real AI backend can be
 * handed as its actual context window, rather than each caller
 * re-querying PersonalMemory/ConversationMemory/ProjectRepository
 * separately and reassembling this by hand every time.
 */
data class ContextBundle(
    val sessionId: String,
    val recentConversation: List<String>,
    val relevantPersonalMemory: List<String>,
    val activeProjectSummary: String?,
)

/** Sprint 10 "Better planning" / "Multi-step reasoning" deliverable. A PlanStep never carries an execution result -- that's WorkflowEngine's job (Sprint 11); PlanningEngine only decides WHAT the steps are and in what order. */
data class PlanStep(
    val stepId: String,
    val description: String,
    val requiresTool: String? = null,
)

data class Plan(
    val goal: String,
    val steps: List<PlanStep>,
)
