package com.jarvis.os.app.data.model

/**
 * PR4: what a ChatProvider (Sprint-8) or Agent (Sprint 11) can be
 * routed work for. Deliberately a flat enum, not a free-text tag set --
 * every routing decision in AiRouter and MultiAiCoordinator needs a
 * closed vocabulary it can exhaustively reason over (see AiRouter's
 * routeFor scoring), the same reasoning ConnectionStatus and
 * ApprovalOutcome already apply to their own state machines.
 */
enum class AiCapability {
    GENERAL_CHAT,
    CODE_GENERATION,
    REASONING,
    LONG_CONTEXT,
    TOOL_USE,
    VISION,
}
