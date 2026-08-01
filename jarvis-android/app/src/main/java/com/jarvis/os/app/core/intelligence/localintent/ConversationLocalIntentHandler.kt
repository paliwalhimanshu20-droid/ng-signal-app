package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Conversation Replay Bug Fix", Requirement 4/5/9: the ONLY place in this codebase recalled
 * conversation history is allowed to become part of a JARVIS reply. Before this class existed,
 * [com.jarvis.os.app.core.JarvisCore.buildBaseContextParts] injected a summary of recent messages
 * into EVERY AI-bound turn unconditionally, which is what let "we recently touched on..." leak
 * into replies to completely unrelated questions -- see that method's own docstring for the full
 * removal. That method no longer touches recentConversation at all; this handler is what replaces
 * it, but on fundamentally different terms:
 *  - LOCAL_ONLY, not a hint fed to an AI provider: this handler answers directly, deterministically,
 *    with zero model call -- the same "no fake success, no ambient AI dependency" discipline this
 *    router's other handlers already follow (see [LocalIntentRouter]'s own class docstring).
 *  - Explicit trigger only: matched ONLY when the Owner's message is actually, unambiguously
 *    asking to recall the conversation ("what did we discuss", "summarize our conversation", "what
 *    do you remember") -- never as a side effect of an ordinary question. This is the literal
 *    mechanism behind "Memory Rules" Requirement 4: "Never echo memory unless the user explicitly
 *    asks."
 *  - Answers using ONLY this session's live transcript ([ChatRepository.messages], scoped to
 *    [ChatRepository.activeSessionId]) -- the exact same source
 *    [com.jarvis.os.app.core.intelligence.ContextManager.buildContext] already reads, so "what we
 *    discussed" here means the same thing it always meant, just surfaced honestly and on request
 *    instead of ambiently.
 *
 * Declared right after HELP (see [LocalServiceDomain]'s declaration order docstring): a low
 * false-positive-risk phrase match, tried before the broader TIDB/KNOWLEDGE_BASE domains so a
 * genuine recap request is never mistaken for a data or glossary question.
 */
@Singleton
class ConversationLocalIntentHandler @Inject constructor(
    private val chat: ChatRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.CONVERSATION_SUMMARY

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase()
        if (RECAP_PHRASES.none { it in lower }) return null

        val sessionMessages = chat.messages.value.filter { it.sessionId == chat.activeSessionId }
        if (sessionMessages.isEmpty()) {
            return LocalIntentAnswer(
                "We haven't discussed anything yet this session -- ask me something and I'll be able to recap it for you later.",
            )
        }

        val recap = sessionMessages.takeLast(MAX_RECAP_MESSAGES).joinToString(" ") { message ->
            val speaker = if (message.author == MessageAuthor.OWNER) "you said" else "I said"
            "$speaker: \"${message.content}\"."
        }
        return LocalIntentAnswer("Here's what we've covered so far this session -- $recap")
    }

    companion object {
        /** Substring match (same style as [KnowledgeBaseLocalIntentHandler]'s TRIGGER_PHRASES), deliberately broad enough to catch "what did we discuss yesterday?" and similar variable endings while still being an explicit, unambiguous ask to recall conversation -- never a plain question about something else. */
        private val RECAP_PHRASES = setOf(
            "what did we discuss", "what did we talk about", "what have we discussed",
            "what have we talked about", "what do we discuss", "what are we discussing",
            "summarize our conversation", "summarize this conversation", "summarize the conversation",
            "recap our conversation", "recap this conversation", "recap the conversation",
            "what do you remember", "what did you remember", "do you remember what we",
        )
        private const val MAX_RECAP_MESSAGES = 10
    }
}
