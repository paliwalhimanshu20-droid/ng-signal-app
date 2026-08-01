package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.data.model.ChatMessage
import com.jarvis.os.app.data.model.ChatSession
import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.model.MessageContentKind
import com.jarvis.os.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * "Conversation Replay Bug Fix" Requirement 4/5/8/9: this handler is the ONLY place recalled
 * conversation history is allowed to become part of a reply -- these tests prove (a) it only
 * fires on an explicit recap request, never an ordinary question, and (b) an ordinary question
 * asked in the same session never triggers it either.
 */
class ConversationLocalIntentHandlerTest {

    private class FakeChatRepository(messages: List<ChatMessage>) : ChatRepository {
        private val _messages = MutableStateFlow(messages)
        override val messages: StateFlow<List<ChatMessage>> = _messages
        override val activeSessionId: String = ChatSession.DEFAULT_SESSION_ID
        override suspend fun sendMessage(text: String, contextHint: String, memoryHint: String, recentChatHint: String, sourceToolIds: List<String>, hadToolFailure: Boolean) = Unit
        override suspend fun sendLocalMessage(text: String, response: String, sourceDomain: String) = Unit
        override fun isAiProviderReady(): Boolean = true
    }

    private fun message(author: MessageAuthor, content: String) = ChatMessage(
        messageId = java.util.UUID.randomUUID().toString(),
        author = author,
        kind = MessageContentKind.TEXT,
        content = content,
        timestamp = Instant.now(),
    )

    @Test
    fun `what did we discuss recaps the real session transcript`() = runTest {
        val history = listOf(
            message(MessageAuthor.OWNER, "What do you have of natural gas?"),
            message(MessageAuthor.JARVIS, "Natural Gas (NATURALGAS), commodity. Last recorded price: 245.5."),
        )
        val handler = ConversationLocalIntentHandler(FakeChatRepository(history))

        val answer = handler.tryHandle("What did we discuss yesterday?")

        assertNotNull(answer)
        assertEquals(LocalIntentOutcome.LOCAL_ONLY, answer!!.outcome)
        assertTrue(answer.response.contains("natural gas", ignoreCase = true))
    }

    @Test
    fun `summarize our conversation matches and recaps`() = runTest {
        val history = listOf(message(MessageAuthor.OWNER, "Hey"))
        val handler = ConversationLocalIntentHandler(FakeChatRepository(history))

        val answer = handler.tryHandle("Can you summarize our conversation so far?")

        assertNotNull(answer)
    }

    @Test
    fun `an empty session gets an honest, non-fabricated answer`() = runTest {
        val handler = ConversationLocalIntentHandler(FakeChatRepository(emptyList()))

        val answer = handler.tryHandle("What do you remember?")

        assertNotNull(answer)
        assertTrue(answer!!.response.contains("haven't discussed anything yet"))
    }

    @Test
    fun `an ordinary question in the same session never triggers a recap`() = runTest {
        val history = listOf(
            message(MessageAuthor.OWNER, "What do you have of natural gas?"),
            message(MessageAuthor.JARVIS, "Natural Gas (NATURALGAS), commodity."),
        )
        val handler = ConversationLocalIntentHandler(FakeChatRepository(history))

        // Requirement 4: "Never echo memory unless the user explicitly asks" -- an unrelated
        // follow-up question must NOT match this handler, even with real history present.
        val answer = handler.tryHandle("What's the price of crude oil?")

        assertNull(answer)
    }

    @Test
    fun `a plain greeting never triggers a recap`() = runTest {
        val handler = ConversationLocalIntentHandler(FakeChatRepository(listOf(message(MessageAuthor.OWNER, "hi"))))

        val answer = handler.tryHandle("Hey")

        assertNull(answer)
    }
}
