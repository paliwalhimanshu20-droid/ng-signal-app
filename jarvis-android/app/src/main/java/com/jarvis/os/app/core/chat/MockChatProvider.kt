package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint-8: the same honest-echo behavior MockChatRepository had in
 * Sprint-7, now expressed through the ChatProvider interface instead
 * of being hardcoded inside the repository — this is a relocation, not
 * new fabricated behavior.
 *
 * Sprint 12 "Real AI Conversation": no longer the only ChatProvider —
 * OpenAiCompatibleChatProvider is a real one now. This one remains
 * bound as JARVIS's offline fallback (see AiRouter — routeFor always
 * has a valid candidate even with no API key configured, and
 * switchProvider lets the Owner pick this one deliberately). Its reply
 * text was rewritten this sprint to stop saying "no real AI call
 * happens yet" now that a real one exists elsewhere — see this
 * sprint's natural-language rule (section 10): the Owner should be
 * told plainly that JARVIS is running offline, not shown implementation
 * vocabulary about which class is or isn't wired up.
 *
 * The reply is written in Markdown (a bold word, an inline code
 * reference, a short list) specifically so MessageContentKind.MARKDOWN
 * — dormant since Sprint-7, never rendered — has something real to
 * exercise end to end, not just a theoretical code path nobody can see
 * on screen.
 */
@Singleton
class MockChatProvider @Inject constructor() : ChatProvider {
    override val id: String = "mock"
    override val displayName: String = "JARVIS (offline)"

    /** Always-available fallback -- deliberately just GENERAL_CHAT so routeFor never has to treat "no provider matched" as a real failure mode when this one is bound. */
    override val capabilities: Set<AiCapability> = setOf(AiCapability.GENERAL_CHAT)

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val reply = buildString {
            append("I heard you say: \"$text\".\n\n")
            append("I'm currently running **offline** — no AI provider is connected right now. ")
            append("Add an API key under Settings → AI Provider to have a real conversation with me.\n\n")
            append("In the meantime, here's what I can still show you:\n\n")
            append("- Markdown rendering, like this `inline code` and this list\n")
            append("- Session id for this message: `$sessionId`")
        }
        // Word-by-word, not one giant Token: makes the streaming
        // architecture genuinely observable on screen rather than a
        // code path nobody can actually see exercised. The 30ms pace
        // is deliberate pacing for this mock, not simulated network
        // latency — a real provider drives its own pacing.
        val words = reply.split(" ")
        val builder = StringBuilder()
        for ((index, word) in words.withIndex()) {
            if (index > 0) builder.append(" ")
            builder.append(word)
            emit(ChatChunk.Token(builder.toString()))
            delay(30)
        }
        emit(ChatChunk.Complete(reply))
    }
}
