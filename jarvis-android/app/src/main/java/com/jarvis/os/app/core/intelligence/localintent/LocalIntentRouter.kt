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
 * "LocalIntentRouter Offline Completion" extends the original OS-first scope (TIDB/Signals/
 * Analytics/Mission Control/Connected Systems/Diagnostics/Settings) with three more purely local
 * capabilities -- greetings, built-in "who are you / what can you do" questions, and a small
 * bundled knowledge base (trading terms like EMA/RSI/ATR) -- and, separately, makes JARVIS
 * degrade gracefully rather than fail loudly whenever NOTHING local matches and no AI provider is
 * configured. Those are two different concerns living in two different places: this router only
 * ever decides "can a local repository/service answer this," never "is an AI provider
 * available" -- that second question belongs to [com.jarvis.os.app.core.chat.ChatProvider
 * .isConfigured] / [com.jarvis.os.app.data.repository.ChatRepository.isAiProviderReady], and is
 * applied by JarvisCore only AFTER this router has already said no local answer exists (or said
 * "local answer plus AI enrichment would help"). Keeping the two separate means a new local
 * capability never has to know or care whether an API key is configured, and the "no provider
 * configured" fallback logic lives in exactly one place regardless of which branch reached it.
 *
 * [resolve] always returns a real [LocalIntentResult] -- never null -- carrying one of three
 * outcomes:
 *  - [LocalIntentOutcome.LOCAL_ONLY]: a handler fully answered the message. JarvisCore renders
 *    [LocalIntentResult.response] verbatim via `ChatRepository.sendLocalMessage` and NEVER calls
 *    an AI provider for this turn, full stop -- not even if one is configured.
 *  - [LocalIntentOutcome.LOCAL_PLUS_AI]: a handler found real local context worth surfacing, but
 *    the message is asking for more than that context alone provides (reasoning, "why", "should
 *    I..."). [LocalIntentResult.response] becomes the contextHint an AI provider is asked to
 *    reason over, IF one is configured; if none is, JarvisCore shows that same local context
 *    directly rather than discarding it, only falling back to the fully generic "AI not
 *    configured" message when there's truly nothing local to show either. No handler currently
 *    returns this outcome (deliberately -- every handler in this milestone answers deterministic,
 *    complete-in-themselves questions per the Offline Completion brief's "do NOT call any AI
 *    provider for these"), but the type exists now so a future handler (e.g. a trading
 *    recommendation rationale) can opt into it without another router redesign.
 *  - [LocalIntentOutcome.NO_MATCH]: no local handler recognized the message at all. JarvisCore
 *    falls through to its full existing AI-bound chain (trading reply / briefing / orchestration
 *    / tool-backed / conversational), still subject to the same "is AI actually configured" gate
 *    before any provider is ever called.
 *
 * DEVICE_ACTION is explicitly out of scope for this milestone (per "BUILD UNTIL GREEN --
 * LocalIntentRouter Offline Completion": "Do not implement DEVICE_ACTION yet") -- no outcome,
 * handler, or domain for it exists here; adding one later is additive, not a redesign of this
 * enum or of JarvisCore's branching on it.
 *
 * Deliberately the same "list of narrow classifiers, first real match wins" shape as
 * [com.jarvis.os.app.core.intelligence.IntentRouter] and [ToolRepository]'s own tool discovery --
 * a [LocalIntentHandler] per domain, contributed via Hilt `@IntoSet` (see LocalIntentHandlerModule),
 * so a new local capability is a new handler class plus one `@Binds` line, never a change to this
 * class or to JarvisCore. Handlers are tried in [LocalServiceDomain] declaration order for the
 * same reason IntentRouter iterates ToolRepository.discover() order: a fixed, auditable order
 * beats an unordered Hilt Set wherever more than one handler could plausibly match the same
 * message. GREETING and HELP are declared first (cheap, exact-phrase matches with the lowest
 * false-positive risk); KNOWLEDGE_BASE is declared last since its "what is X" / "explain X"
 * phrasing is the broadest and most likely to coincidentally overlap with a more specific
 * domain's own keywords.
 */
enum class LocalServiceDomain {
    GREETING,
    HELP,
    CONVERSATION_SUMMARY,
    TIDB,
    SIGNALS,
    ANALYTICS,
    MISSION_CONTROL,
    CONNECTED_SYSTEMS,
    DIAGNOSTICS,
    SETTINGS,
    KNOWLEDGE_BASE,
}

enum class LocalIntentOutcome {
    LOCAL_ONLY,
    LOCAL_PLUS_AI,
    NO_MATCH,
}

/** What a single [LocalIntentHandler] hands back on a real match -- see [LocalIntentOutcome] for what each outcome means downstream. Defaults to LOCAL_ONLY since every handler in this milestone is a complete, deterministic answer in itself. */
data class LocalIntentAnswer(
    val response: String,
    val outcome: LocalIntentOutcome = LocalIntentOutcome.LOCAL_ONLY,
)

/**
 * What [LocalIntentRouter.resolve] always returns -- non-null, unlike the handler-level
 * [LocalIntentAnswer]?, so "nothing matched" is a real, named value ([LocalIntentOutcome.NO_MATCH])
 * a caller can switch on, not an absence a caller has to remember to check for. [domain] and
 * [response] are both null exactly when [outcome] is NO_MATCH; non-null for LOCAL_ONLY and
 * LOCAL_PLUS_AI.
 */
data class LocalIntentResult(
    val outcome: LocalIntentOutcome,
    val domain: LocalServiceDomain? = null,
    /** Final, user-facing prose for LOCAL_ONLY; local context/data to reason over for LOCAL_PLUS_AI; null for NO_MATCH. Rendered to the owner exactly as returned for LOCAL_ONLY, with no AI provider pass over it -- each handler owns its own phrasing precisely because there is no model call afterward to smooth over a rough or robotic response. */
    val response: String? = null,
) {
    companion object {
        val NO_MATCH = LocalIntentResult(LocalIntentOutcome.NO_MATCH)
    }
}

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
    suspend fun tryHandle(text: String): LocalIntentAnswer?
}

interface LocalIntentRouter {
    /** Always returns a real result -- see this file's class docstring for what each [LocalIntentOutcome] means and what JarvisCore does with it. */
    suspend fun resolve(text: String): LocalIntentResult
}

@Singleton
class DefaultLocalIntentRouter @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards LocalIntentHandler>,
) : LocalIntentRouter {

    /** Stable, declared order every turn -- Hilt Set iteration order is unspecified, and this codebase's classifiers are deliberately auditable (see class docstring), so ordering is never left to chance the way AiRouter's pre-fix provider fallback was. */
    private val orderedHandlers: List<LocalIntentHandler> by lazy {
        handlers.sortedBy { it.domain.ordinal }
    }

    override suspend fun resolve(text: String): LocalIntentResult {
        if (text.isBlank()) return LocalIntentResult.NO_MATCH
        for (handler in orderedHandlers) {
            val answer = handler.tryHandle(text) ?: continue
            return LocalIntentResult(answer.outcome, handler.domain, answer.response)
        }
        return LocalIntentResult.NO_MATCH
    }
}
