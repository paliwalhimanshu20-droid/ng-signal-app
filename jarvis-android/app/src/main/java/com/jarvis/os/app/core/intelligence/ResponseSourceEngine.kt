package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.core.intelligence.localintent.LocalServiceDomain
import com.jarvis.os.app.data.model.ChatMessage

/**
 * "Phase 3C, Section 3+4 -- Response Source Engine + Confidence Engine." Deliberately a pure,
 * additive classifier over [ChatMessage]'s EXISTING fields (`sourceLocalDomain`, `sourceToolIds`)
 * rather than new persisted fields -- per this phase's "do NOT duplicate entities... search,
 * reuse, extend" rule, [ChatMessage] already carries everything needed to know where an answer
 * came from; what was missing was naming and ranking those sources formally, not a schema change.
 * `ChatMessage` isn't Room-backed (see [com.jarvis.os.app.data.repository.MockChatRepository] --
 * an in-memory `StateFlow`), so this is genuinely zero-risk: nothing about message storage or the
 * UI layer needs to change for [classify] to be real and useful today.
 */
enum class ResponseSource {
    LOCAL_KNOWLEDGE,
    TRADING_INTELLIGENCE_DATABASE,
    HISTORICAL_DATA,
    INDICATOR_WAREHOUSE,
    OPTIMIZATION_ENGINE,
    BACKTEST_ENGINE,
    EVIDENCE_ENGINE,
    DEVICE_ACTION,
    AI_PROVIDER,
}

/** "Low-confidence responses must never be presented as facts": this is the formal value the Hallucination Guard and any future UI treatment (e.g. a visible "AI reasoning, not verified" badge) can key off of -- see [ResponseSourceEngine.classify]'s own doc for exactly which domains map to which confidence and why. */
enum class ResponseConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class ResponseProvenance(val source: ResponseSource, val confidence: ResponseConfidence)

object ResponseSourceEngine {

    /**
     * [message.sourceLocalDomain] is null exactly when a real AI provider composed the reply
     * (see that field's own docstring on [ChatMessage]) -- every non-null value came from a real
     * [com.jarvis.os.app.core.intelligence.localintent.LocalIntentHandler], which is itself
     * either a deterministic template (GREETING/HELP/CONVERSATION_SUMMARY/KNOWLEDGE_BASE --
     * HIGH, since the words are fixed and never invented) or a real repository-backed answer
     * (TIDB/EVIDENCE/DEVICE_ACTION -- HIGH, since the numbers/state came from a real query, not a
     * guess). An AI-composed reply is LOW confidence by default, EXCEPT when
     * [message.sourceToolIds] is non-empty -- real tool output was fed into that reply (see
     * [ChatMessage.sourceToolIds]'s own docstring), which the "verified local knowledge" tier
     * fits better than raw AI reasoning, hence MEDIUM rather than LOW.
     */
    fun classify(message: ChatMessage): ResponseProvenance {
        val domain = message.sourceLocalDomain
        return when {
            domain == LocalServiceDomain.DEVICE_ACTION.name -> ResponseProvenance(ResponseSource.DEVICE_ACTION, ResponseConfidence.HIGH)
            domain == LocalServiceDomain.EVIDENCE.name -> ResponseProvenance(ResponseSource.EVIDENCE_ENGINE, ResponseConfidence.HIGH)
            domain == LocalServiceDomain.TIDB.name || domain == LocalServiceDomain.SYSTEM_STATUS.name -> ResponseProvenance(ResponseSource.TRADING_INTELLIGENCE_DATABASE, ResponseConfidence.HIGH)
            // Runtime Integration milestone: JarvisCore's own real Decision Lifecycle short-circuit
            // (see its "TRADING_REPLY_DOMAIN" constant) -- not a LocalServiceDomain since it
            // predates LocalIntentRouter, but the same real, repository-backed, HIGH-confidence
            // provenance TIDB/SYSTEM_STATUS already get, not the generic catch-all below.
            domain == "TRADING_INTELLIGENCE" -> ResponseProvenance(ResponseSource.TRADING_INTELLIGENCE_DATABASE, ResponseConfidence.HIGH)
            domain == LocalServiceDomain.CONVERSATION_SUMMARY.name -> ResponseProvenance(ResponseSource.LOCAL_KNOWLEDGE, ResponseConfidence.HIGH)
            domain == LocalServiceDomain.GREETING.name || domain == LocalServiceDomain.HELP.name -> ResponseProvenance(ResponseSource.LOCAL_KNOWLEDGE, ResponseConfidence.HIGH)
            domain == LocalServiceDomain.KNOWLEDGE_BASE.name -> ResponseProvenance(ResponseSource.LOCAL_KNOWLEDGE, ResponseConfidence.MEDIUM)
            /** Matches [com.jarvis.os.app.core.JarvisCore]'s own private `AI_UNAVAILABLE_DOMAIN` constant by its literal string value -- that constant is `private`, not exported, so this is the only way to recognize it from outside that file without changing its visibility for a classifier's convenience. */
            domain == "AI_UNAVAILABLE" -> ResponseProvenance(ResponseSource.LOCAL_KNOWLEDGE, ResponseConfidence.MEDIUM)
            domain != null -> ResponseProvenance(ResponseSource.LOCAL_KNOWLEDGE, ResponseConfidence.HIGH)
            message.sourceToolIds.isNotEmpty() -> ResponseProvenance(ResponseSource.AI_PROVIDER, ResponseConfidence.MEDIUM)
            else -> ResponseProvenance(ResponseSource.AI_PROVIDER, ResponseConfidence.LOW)
        }
    }
}
