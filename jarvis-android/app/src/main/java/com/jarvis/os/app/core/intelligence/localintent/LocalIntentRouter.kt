package com.jarvis.os.app.core.intelligence.localintent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * "JARVIS is an operating system first, an AI chatbot second."
 *
 * This is the OS-first gate JarvisCore.sendChatMessage now consults BEFORE any of its existing
 * AI-bound branches (briefing/orchestration/tool-backed/conversational -- see that method's
 * docstring for the full priority chain this slots into). Every one of JARVIS's own subsystems --
 * the Trading Intelligence Database, Signals, Analytics, Mission Control, Connected Systems,
 * Diagnostics, Settings -- already has a real, queryable repository/service sitting behind it.
 * Before today, a question like "how many active signals do I have" or "is Google Workspace
 * connected" still went all the way to a real AI provider to be answered in prose, even though
 * the true answer was one repository call away and completely deterministic. That is backwards
 * for a system whose own house style (see IntentRouter, JarvisDecisionEngine) already treats
 * "deterministic, auditable, no model call" as the default and an LLM call as the narrow
 * exception -- this class generalizes that same discipline from "which tool to run" to "does this
 * question even need a model at all."
 *
 * DECISION (per product direction, "OS First"): if [resolve] returns a non-null [LocalIntentResult],
 * JarvisCore answers from that result directly and NEVER calls an AI provider for this turn --
 * see JarvisCore.sendChatMessage and ChatRepository.sendLocalMessage, the new bypass path that
 * skips AiRouter/ChatProvider entirely. An AI provider is only reached when [resolve] returns
 * null (no local service can answer this) or when the message is itself a request for AI
 * reasoning, summarization, or outside/general knowledge (JarvisDecisionEngine/IntentRouter's
 * existing tool-backed and conversational branches still own that territory unchanged).
 *
 * Deliberately the same "list of narrow classifiers, first real match wins" shape as
 * [com.jarvis.os.app.core.intelligence.IntentRouter] and [ToolRepository]'s own tool discovery --
 * a [LocalIntentHandler] per domain, contributed via Hilt `@IntoSet` (see LocalIntentHandlerModule),
 * so a new local capability is a new handler class plus one `@Binds` line, never a change to this
 * class or to JarvisCore. Handlers are tried in [LocalServiceDomain] declaration order for the
 * same reason IntentRouter iterates ToolRepository.discover() order: a fixed, auditable order
 * beats an unordered Hilt Set wherever more than one handler could plausibly match the same
 * message.
 */
enum class LocalServiceDomain {
    TIDB,
    SIGNALS,
    ANALYTICS,
    MISSION_CONTROL,
    CONNECTED_SYSTEMS,
    DIAGNOSTICS,
    SETTINGS,
}

data class LocalIntentResult(
    val domain: LocalServiceDomain,
    /** Final, user-facing prose -- rendered to the owner exactly as returned, with no AI provider pass over it. Each handler owns its own phrasing precisely because there is no model call afterward to smooth over a rough or robotic response. */
    val response: String,
)

/**
 * One classifier + answerer for a single local subsystem. [tryHandle] returns null the instant it
 * determines the message isn't shaped for its domain (a cheap keyword gate, mirroring
 * IntentRouter's own triggerKeywords check) -- only a real match proceeds to touch a repository.
 * Never throws for "no match"; a thrown exception from an actual repository call during a real
 * match is allowed to propagate so [DefaultLocalIntentRouter] can fail this turn back to the
 * existing AI-bound path rather than silently swallowing a real local-service error into a fake
 * answer (this codebase's "no fake success" rule, applied here).
 */
interface LocalIntentHandler {
    val domain: LocalServiceDomain
    suspend fun tryHandle(text: String): String?
}

interface LocalIntentRouter {
    /** Null means no local service can answer this -- JarvisCore falls through to its existing AI-bound branches. Non-null means this turn is fully answered; no ChatProvider is ever consulted. */
    suspend fun resolve(text: String): LocalIntentResult?
}

@Singleton
class DefaultLocalIntentRouter @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards LocalIntentHandler>,
) : LocalIntentRouter {

    /** Stable, declared order every turn -- Hilt Set iteration order is unspecified, and this codebase's classifiers are deliberately auditable (see class docstring), so ordering is never left to chance the way AiRouter's pre-fix provider fallback was. */
    private val orderedHandlers: List<LocalIntentHandler> by lazy {
        handlers.sortedBy { it.domain.ordinal }
    }

    override suspend fun resolve(text: String): LocalIntentResult? {
        if (text.isBlank()) return null
        for (handler in orderedHandlers) {
            val response = handler.tryHandle(text) ?: continue
            return LocalIntentResult(handler.domain, response)
        }
        return null
    }
}
