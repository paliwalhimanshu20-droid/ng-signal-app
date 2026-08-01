package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.ChatSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 8.1: a real, internal session manager, per this sprint's
 * explicit "a hidden internal session manager is acceptable, do not
 * redesign the UI" instruction. ChatRepository reads activeSessionId
 * from here now instead of hardcoding ChatSession.DEFAULT_SESSION_ID
 * -- switching sessions genuinely changes what future messages get
 * scoped to. No screen calls createSession or switchSession yet; a
 * session-switcher UI is a real product decision for a later sprint,
 * once this manager has been exercised and there is a concrete design
 * to build against.
 */
@Singleton
class ChatSessionManager @Inject constructor() {
    private val _sessions = MutableStateFlow(
        listOf(ChatSession(ChatSession.DEFAULT_SESSION_ID, "New chat", Instant.now())),
    )
    val sessions: StateFlow<List<ChatSession>> = _sessions

    private val _activeSessionId = MutableStateFlow(ChatSession.DEFAULT_SESSION_ID)
    val activeSessionId: StateFlow<String> = _activeSessionId

    fun createSession(title: String = "New chat"): ChatSession {
        val session = ChatSession(UUID.randomUUID().toString(), title, Instant.now())
        _sessions.update { it + session }
        return session
    }

    /** Returns false and leaves the active session unchanged if sessionId isn't a known session. */
    fun switchSession(sessionId: String): Boolean {
        val exists = _sessions.value.any { it.sessionId == sessionId }
        if (exists) _activeSessionId.value = sessionId
        return exists
    }
}
