package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.data.model.Plan
import com.jarvis.os.app.data.model.PlanStep
import com.jarvis.os.app.data.repository.ToolRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 10 "Better planning" / "Multi-step reasoning" -- mirrors the
 * Python backend's Sprint-4 IntelligenceEngine goal-analysis pattern
 * (deterministic decomposition, not an LLM call) on the Android side,
 * per this codebase's stdlib-first / no-speculative-ML-dependency
 * preference. Keyword-rule based rather than free-form NLU: a goal
 * containing "and"/commas is split into ordered clauses, each clause
 * is checked against registered ToolRepository tool names for a
 * plausible tool match, and a clause matching neither becomes a plain
 * reasoning step. This is intentionally simple and fully auditable --
 * every Plan this produces can be traced back to which rule fired,
 * unlike an opaque model call this sandbox has no way to verify
 * end-to-end anyway (no live AI provider is wired in -- see
 * ContextManager's docstring for the same honesty note).
 */
@Singleton
class PlanningEngine @Inject constructor(
    private val tools: ToolRepository,
) {
    fun plan(goal: String): Plan {
        val clauses = goal.split(Regex("(?i)\\band\\b|,"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(goal.trim()) }

        val toolNames = tools.discover().associateBy { it.name.lowercase() }

        val steps = clauses.map { clause ->
            val matchedTool = toolNames.entries.firstOrNull { (name, _) -> clause.lowercase().contains(name) }?.value
            PlanStep(
                stepId = UUID.randomUUID().toString(),
                description = clause,
                requiresTool = matchedTool?.toolId,
            )
        }

        return Plan(goal, steps)
    }
}
