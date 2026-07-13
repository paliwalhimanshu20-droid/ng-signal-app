package com.jarvis.os.app.core.memory

/**
 * Sprint 8.1: interfaces only, per this sprint's explicit "do not
 * implement long-term memory, architecture only" scope. Nothing
 * implements these yet, and JarvisCore does not depend on them yet --
 * adding that dependency, and a real implementation of each, is future
 * work once there is a concrete consumer to build against, matching
 * this codebase's established preference against wiring speculative
 * producers with nothing real to verify against.
 *
 * Deliberately separate from MemoryRepository/MemoryTier (Part 7,
 * already real and working since Sprint-7) rather than folded into it
 * -- these four represent a different, broader concept the product
 * brief named explicitly, not a replacement for what already works.
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
