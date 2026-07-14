package com.jarvis.os.app.core.memory

import com.jarvis.os.app.data.model.MemoryEntry
import com.jarvis.os.app.data.model.MemoryTier
import com.jarvis.os.app.data.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 8.1 shipped these four as interfaces only ("architecture
 * only, not yet implemented"). Sprint 10 gives each a real,
 * MemoryRepository-backed implementation -- the "concrete consumer to
 * build against" Sprint 8.1 said was the blocker.
 *
 * ConversationMemory, ProjectMemory and AgentMemory all declare
 * `suspend fun recall(String, String): List<String>` -- identical
 * parameter types mean identical JVM signatures, so a single class
 * cannot implement more than one of these three with distinct bodies
 * (the JVM has no way to dispatch on a parameter's *meaning*, only its
 * type). That is why Sprint 10 gives each its own small
 * MemoryRepository-backed class below instead of one MemoryEngine
 * implementing all four -- not a stylistic choice, a real constraint
 * this sprint's implementation surfaced that the original interface
 * design left latent since nothing implemented them yet.
 */
interface ConversationMemory {
    suspend fun recall(sessionId: String, query: String): List<String>
}

interface ProjectMemory {
    suspend fun recall(projectId: String, query: String): List<String>
}

interface PersonalMemory {
    suspend fun recall(query: String): List<String>
}

interface AgentMemory {
    suspend fun recall(agentId: String, query: String): List<String>
}

/** Sprint 10: sessionId-scoped recall over MemoryTier.CONVERSATION entries. */
@Singleton
class ConversationMemoryImpl @Inject constructor(
    private val memory: MemoryRepository,
) : ConversationMemory {
    override suspend fun recall(sessionId: String, query: String): List<String> =
        memory.search(query, sessionId).filter { it.tier == MemoryTier.CONVERSATION }.map(MemoryEntry::summary)
}

/** Sprint 10: projectId-scoped recall over MemoryTier.PROJECT and MemoryTier.DECISION entries -- decisions are included because "why did we do X on this project" is exactly ProjectMemory's purpose per the sprint brief. */
@Singleton
class ProjectMemoryImpl @Inject constructor(
    private val memory: MemoryRepository,
) : ProjectMemory {
    override suspend fun recall(projectId: String, query: String): List<String> =
        memory.search(query, projectId)
            .filter { it.tier == MemoryTier.PROJECT || it.tier == MemoryTier.DECISION }
            .map(MemoryEntry::summary)
}

/** Sprint 10: unscoped recall over the owner's durable, cross-project memory (PREFERENCE, LONG_TERM, KNOWLEDGE). */
@Singleton
class PersonalMemoryImpl @Inject constructor(
    private val memory: MemoryRepository,
) : PersonalMemory {
    override suspend fun recall(query: String): List<String> =
        memory.search(query)
            .filter { it.tier == MemoryTier.PREFERENCE || it.tier == MemoryTier.LONG_TERM || it.tier == MemoryTier.KNOWLEDGE }
            .map(MemoryEntry::summary)
}

/** Sprint 10: agentId-scoped recall, for a Sprint 11 Agent to remember its own prior task outcomes across runs (see core/agents). */
@Singleton
class AgentMemoryImpl @Inject constructor(
    private val memory: MemoryRepository,
) : AgentMemory {
    override suspend fun recall(agentId: String, query: String): List<String> =
        memory.search(query, agentId).map(MemoryEntry::summary)
}
