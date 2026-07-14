package com.jarvis.os.app.core.agents

import com.jarvis.os.app.data.model.AgentResult
import com.jarvis.os.app.data.model.AgentTask
import com.jarvis.os.app.data.model.AiCapability

/**
 * Sprint 11: the seam a specialist agent implements -- same swap-point
 * pattern as ChatProvider (PR4) and Tool (Sprint 10): callers depend
 * only on this interface, AgentRegistry discovers bound instances via
 * Hilt multibinding, a real agent later is one class plus one @Binds
 * @IntoSet line.
 */
interface Agent {
    val agentId: String
    val name: String
    val specialty: String
    val capabilities: Set<AiCapability>

    suspend fun run(task: AgentTask): AgentResult
}
