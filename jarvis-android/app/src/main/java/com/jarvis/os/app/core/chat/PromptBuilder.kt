package com.jarvis.os.app.core.chat

/**
 * "Conversation Replay Bug Fix": the root cause of JARVIS answering a fresh question with
 * "we recently touched on..." was structural, not a wording problem. Before this file existed,
 * [com.jarvis.os.app.data.repository.MockChatRepository.sendMessage] built the ONLY thing a
 * ChatProvider ever saw with one line: `"$contextHint\n\n$text"` -- a flat string that put
 * recalled conversation history and the Owner's real, current question inside the exact same
 * blob, with no label, no boundary, and (for three of four real providers -- see below) no system
 * prompt at all to tell the model which part was background and which part needed answering. A
 * model given "we recently touched on: X; Y; Z\n\nWhat do you have of natural gas?" as its entire
 * input has no way to know the first line isn't part of what it's being asked to respond to.
 *
 * [ChatPrompt] fixes this by construction: [ChatPrompt.userMessage] is ALWAYS exactly and only
 * the Owner's current message -- nothing upstream of this file ever concatenates anything onto
 * it, and nothing downstream (see every ChatProvider implementation) is allowed to send it to a
 * real API wrapped in anything but the "user" role turn. [memory] and [recentChat] are carried as
 * separate, optional fields specifically so they can be rendered into a clearly labeled
 * BACKGROUND block -- sent as its own system-role turn where every real provider supports one, or
 * prefixed with an unmistakable label where it doesn't -- that explicitly instructs the model not
 * to restate or lead with it. This is what Requirement 6's "SYSTEM: / MEMORY: / RECENT CHAT: /
 * USER:" shape actually buys: not prettier logging, but a guarantee that a provider's *response*
 * generation is anchored on `userMessage` alone, with memory/history demoted to context it may
 * consult but is told never to echo.
 *
 * [recentChat] is deliberately "only for AI providers" (per this class's own docstring
 * requirement): [MockChatProvider] and every "not yet connected" placeholder
 * ([MockClaudeProvider], [MockGptProvider]) never look at it, since neither generates real
 * language from it -- they just template-echo [userMessage], so handing them recalled history
 * would only recreate the exact bug this class exists to prevent, for no benefit (see each of
 * those classes' own [ChatProvider.sendMessage] override).
 */
data class ChatPrompt(
    /** The Owner's current message, verbatim -- never prefixed, suffixed, or blended with anything else. This is what every ChatProvider must treat as the actual question to answer. */
    val userMessage: String,
    /** Durable, cross-session facts/preferences relevant to this turn (see PersonalMemory) -- context only. Null when there is none this turn. */
    val memory: String? = null,
    /** A few recent messages from this session, for AI providers capable of reasoning over them as background -- never a directive to summarize or restate them. Null when there is none, or when the caller (e.g. a LOCAL_ONLY reply) never reaches a real provider at all. */
    val recentChat: String? = null,
)

object PromptBuilder {

    /**
     * Builds a [ChatPrompt] from the pieces [com.jarvis.os.app.core.JarvisCore] computed
     * separately this turn.
     *
     * [contextHint] is real, CURRENT-turn grounding -- tool output, project status, a named
     * tool/agent -- computed fresh from this exact message, never from earlier ones. It is folded
     * directly onto [userMessage] (as it always safely could be) because it is answer-supporting
     * data about right now, not a summary of the past that could be mistaken for something to lead
     * a reply with -- that distinction is the entire fix; see [ChatPrompt]'s own docstring. Blank
     * by default so an ordinary conversational turn with nothing to ground folds in nothing.
     *
     * [memoryHint] (durable personal-memory facts) and [recentChatHint] (a few recent messages)
     * are NEVER folded into [userMessage] -- both become [ChatPrompt.memory] /
     * [ChatPrompt.recentChat] instead, kept separate specifically so no provider can confuse them
     * for the current question. Blank hints become `null`, not empty strings.
     */
    fun build(userMessage: String, contextHint: String = "", memoryHint: String = "", recentChatHint: String = ""): ChatPrompt =
        ChatPrompt(
            userMessage = if (contextHint.isBlank()) userMessage else "$contextHint\n\n$userMessage",
            memory = memoryHint.trim().takeIf { it.isNotEmpty() },
            recentChat = recentChatHint.trim().takeIf { it.isNotEmpty() },
        )

    /**
     * Renders [ChatPrompt.memory] / [ChatPrompt.recentChat] as ONE background instruction block,
     * for real providers that accept a system-role message alongside [JarvisPersona.systemPrompt]
     * (every provider below sends this as its own separate "system" turn, appended after the
     * persona prompt -- never merged into the "user" turn). Returns null when there is nothing to
     * say, so every caller can do `background?.let { ... }` and add nothing when this turn has no
     * memory or recent chat at all (e.g. the Owner's very first message in a session).
     */
    fun backgroundContextBlock(prompt: ChatPrompt): String? {
        if (prompt.memory == null && prompt.recentChat == null) return null
        return buildString {
            append(
                "BACKGROUND CONTEXT -- for your own situational awareness only. Do NOT restate, list, quote, or lead " +
                    "with anything below, and do not open by saying what you recall -- only draw on it if it actually helps " +
                    "answer the CURRENT message, or if the Owner explicitly asks what you remember or what you've discussed.",
            )
            prompt.memory?.let { append("\n\nMEMORY:\n").append(it) }
            prompt.recentChat?.let { append("\n\nRECENT CHAT:\n").append(it) }
        }
    }
}
