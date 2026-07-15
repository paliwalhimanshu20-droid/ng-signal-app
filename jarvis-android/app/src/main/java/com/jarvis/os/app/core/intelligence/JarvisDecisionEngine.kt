package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.core.agents.AgentRegistry
import com.jarvis.os.app.data.model.AgentDescriptor
import com.jarvis.os.app.data.model.ToolDefinition
import com.jarvis.os.app.data.repository.ToolRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 12 "JARVIS Decision Engine" (item 4): the thing JarvisCore
 * consults BEFORE calling ChatRepository.sendMessage, so a reply is
 * informed by what the message actually needs rather than every
 * message getting the exact same treatment regardless of content.
 * Same "deterministic decomposition, not an LLM call" philosophy
 * PlanningEngine's docstring states and this codebase has followed
 * since Sprint 10 -- every JarvisDecision this produces traces back to
 * one keyword/name-match rule, auditable without a live provider to
 * verify an opaque model call against (still doesn't exist -- see
 * ContextManager's docstring).
 *
 * Deliberately narrow for Sprint 12 PR1: decides WHETHER project
 * context is relevant, WHICH tool (if any) the message names, and
 * WHICH agent (if any) the message names. It does not decide whether
 * memory is relevant -- JarvisCore always asks ContextManager for
 * conversation + personal memory on every turn, the same way a human
 * assistant doesn't first ask themselves "should I remember anything"
 * before recalling context. It does not itself decide whether approval
 * is required -- ToolDefinition.requiresApproval (existing, Sprint 10)
 * already answers that, and JarvisCore is the one place that acts on a
 * JarvisDecision, so re-deciding approval here would be a second,
 * potentially-drifting copy of that rule. It does not itself run a
 * tool or assign an agent -- JarvisCore.runTool and AgentRegistry.assign
 * already own those, gated exactly as before; this class only says
 * "the message named X", never "run X".
 */
data class JarvisDecision(
    val needsProjectContext: Boolean,
    val matchedTool: ToolDefinition?,
    val matchedAgent: AgentDescriptor?,
    /** Sprint 12 Phase 3: the message is asking for a status roundup ("good morning", "brief me", "what's going on") rather than a specific question -- routes to ExecutiveBriefingEngine instead of the generic context hint. */
    val needsBriefing: Boolean = false,
    /** Sprint 12 Phase 2: the message is asking for a multi-specialist review ("convene watch tower", "full review", "audit this") -- routes to WatchTowerOrchestrator.requestConvene, which always requires approval before anything runs (see that class's docstring); this flag only says the message asked for one, it never authorizes running one. */
    val needsOrchestration: Boolean = false,
)

@Singleton
class JarvisDecisionEngine @Inject constructor(
    private val tools: ToolRepository,
    private val agents: AgentRegistry,
) {
    fun decide(text: String): JarvisDecision {
        val lower = text.lowercase()

        val needsProjectContext = PROJECT_KEYWORDS.any { lower.contains(it) }
        val needsBriefing = BRIEFING_KEYWORDS.any { lower.contains(it) }
        val needsOrchestration = ORCHESTRATION_KEYWORDS.any { lower.contains(it) }

        val matchedTool = tools.discover().firstOrNull { lower.contains(it.name.lowercase()) }

        // Derived keyword, e.g. "Research Agent" -> "research", "Code Agent" -> "code"
        // -- short and specific enough for a substring match against a chat
        // message, unlike matching the full `specialty` description (e.g.
        // "Long-context research and synthesis"), which is prose meant for a
        // human reading an agent list, not a keyword meant for matching against.
        val matchedAgent = agents.agents.value.firstOrNull { agent ->
            val keyword = agent.name.lowercase().substringBefore(" agent").trim()
            keyword.isNotEmpty() && lower.contains(keyword)
        }

        return JarvisDecision(needsProjectContext, matchedTool, matchedAgent, needsBriefing, needsOrchestration)
    }

    companion object {
        private val PROJECT_KEYWORDS = setOf(
            "pending", "project", "sprint", "blocked", "status", "progress", "task", "milestone",
        )
        private val BRIEFING_KEYWORDS = setOf(
            "good morning", "brief me", "briefing", "catch me up", "what's going on", "whats going on",
            "status update", "morning update", "daily update",
        )
        private val ORCHESTRATION_KEYWORDS = setOf(
            "watch tower", "convene", "full review", "audit this", "audit everything", "get the team's take",
        )
    }
}
