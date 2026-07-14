package com.jarvis.os.app.data.model

import java.time.Instant

// --- Sprint 11: Agent Orchestration ---------------------------------------------

enum class AgentStatus { IDLE, RUNNING, SUCCEEDED, FAILED }

/**
 * Sprint 11 "Register specialist agents" -- what AgentRegistry tracks.
 * `capabilities` reuses AiCapability (PR4) rather than a second
 * vocabulary, since "what an agent is good at" and "what an AI
 * provider is good at" are the same closed concept this codebase
 * already models once.
 */
data class AgentDescriptor(
    val agentId: String,
    val name: String,
    val specialty: String,
    val capabilities: Set<AiCapability>,
    val status: AgentStatus,
)

data class AgentTask(
    val taskId: String,
    val goal: String,
    val requiredCapabilities: Set<AiCapability> = emptySet(),
)

data class AgentResult(
    val taskId: String,
    val agentId: String,
    val success: Boolean,
    val output: String,
    val completedAt: Instant,
)
