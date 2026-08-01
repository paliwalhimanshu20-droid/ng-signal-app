package com.jarvis.os.app.core.memory

import com.jarvis.os.app.data.model.MemoryTier
import com.jarvis.os.app.data.repository.MockMemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 10: proves each memory view only sees its own scoped tier(s), backed by the same real MockMemoryRepository the app injects. */
class MemoryViewsTest {

    @Test
    fun `conversation memory is scoped by sessionId and tier`() = runTest {
        val repo = MockMemoryRepository()
        repo.remember(MemoryTier.CONVERSATION, "Talked about routing", relatedId = "session-1")
        repo.remember(MemoryTier.CONVERSATION, "Talked about memory", relatedId = "session-2")
        repo.remember(MemoryTier.WORKING, "Unrelated working note", relatedId = "session-1")

        val results = ConversationMemoryImpl(repo).recall("session-1", "")
        assertTrue(results.size == 1)
        assertTrue(results.first().contains("routing"))
    }

    @Test
    fun `project memory includes decisions for that project`() = runTest {
        val repo = MockMemoryRepository()
        repo.recordDecision("Use topological sort", "handles workflow dependencies cleanly", relatedId = "proj-1")
        repo.remember(MemoryTier.PROJECT, "Milestone 1 shipped", relatedId = "proj-1")

        val results = ProjectMemoryImpl(repo).recall("proj-1", "")
        assertTrue(results.size == 2)
    }

    @Test
    fun `personal memory excludes conversation and project tiers`() = runTest {
        val repo = MockMemoryRepository()
        repo.remember(MemoryTier.PREFERENCE, "Prefers concise replies")
        repo.remember(MemoryTier.CONVERSATION, "Should not appear", relatedId = "s1")

        val results = PersonalMemoryImpl(repo).recall("")
        assertTrue(results.any { it.contains("concise") })
        assertTrue(results.none { it.contains("Should not appear") })
    }

    @Test
    fun `search matches tags as well as summary text`() = runTest {
        val repo = MockMemoryRepository()
        val found = repo.search("pr4")
        assertTrue(found.isNotEmpty())
    }
}
