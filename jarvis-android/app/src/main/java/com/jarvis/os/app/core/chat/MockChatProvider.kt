package com.jarvis.os.app.core.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint-8: the same honest-echo behavior MockChatRepository had in
 * Sprint-7, now expressed through the ChatProvider interface instead
 * of being hardcoded inside the repository — this is a relocation, not
 * new fabricated behavior. This is the ONLY ChatProvider this sprint
 * ships. No network call to any real AI service happens here or
 * anywhere in this codebase yet. Wiring GPT/Claude/Gemini/local-model
 * providers is explicitly out of scope for this sprint (see
 * architecture summary's "Risks" and "Next recommended sprint").
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
    override val displayName: String = "JARVIS (offline shell)"

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val reply = buildString {
            append("Received: \"$text\".\n\n")
            append("This reply is rendered through the **new Markdown renderer** introduced in Sprint-8 — ")
            append("inline code like `ChatProvider.sendMessage()` and short lists both work now:\n\n")
            append("- No real AI call happens yet — see `MockChatProvider`\n")
            append("- Swapping in a real provider is one class plus one `@Binds` line\n")
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
