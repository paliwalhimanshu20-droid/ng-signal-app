package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.core.agents.AgentRegistry
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.MemoryTier
import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.MemoryRepository
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider
import com.jarvis.os.app.data.repository.NotificationRepository
import com.jarvis.os.app.data.repository.ProjectRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 12 "Executive Briefing Engine" (Phase 3): "Replace static
 * dashboard cards. Generate dynamic briefings." Every line below is
 * assembled from a real, already-governed data source -- ProjectOS
 * (ProjectRepository), NG Signal Pro (NgSignalProStatusProvider, see
 * that file's honesty note), Memory (MemoryRepository, most recent
 * DECISION entry), Approvals (ApprovalRepository, pending count),
 * Connections (ConnectionRepository, connected count), Notifications
 * (NotificationRepository, unread count), and Watch Tower
 * (AgentRegistry.results -- see below for why this reads existing
 * results rather than triggering a new convening).
 *
 * Deterministic composition, not an LLM call -- "LLMs generate
 * language, not governance decisions" (this sprint's own design rule)
 * reads naturally as "a real provider MAY later polish this into more
 * natural prose", but deciding WHAT goes in the briefing and what
 * numbers appear in it stays rule-based and auditable, same as every
 * other *Engine in this package.
 *
 * Watch Tower lines are read from AgentRegistry.results, NOT a fresh
 * WatchTowerOrchestrator.convene() call: convening always requires a
 * new owner approval (see MultiAiCoordinator's docstring), and a
 * briefing that silently created a pending approval every time the
 * owner opened it would be a hidden governance action, not a summary
 * -- exactly what "owner never manually coordinates agents" is about
 * avoiding in the other direction (JARVIS never coordinates them
 * without being asked, either). If Watch Tower hasn't been convened
 * yet, the briefing simply has no Watch Tower line -- no invented
 * "Batman found one issue" text, per this sprint's own "no fake
 * success" limitation already documented in WatchTowerAgents.kt.
 */
data class ExecutiveBriefing(
    val greeting: String,
    val lines: List<String>,
    val generatedAt: Instant,
)

@Singleton
class ExecutiveBriefingEngine @Inject constructor(
    private val projects: ProjectRepository,
    private val approvals: ApprovalRepository,
    private val notifications: NotificationRepository,
    private val connections: ConnectionRepository,
    private val memory: MemoryRepository,
    private val agents: AgentRegistry,
    private val ngSignalPro: NgSignalProStatusProvider,
) {
    fun generateMorningBriefing(): ExecutiveBriefing {
        val lines = mutableListOf<String>()

        lines += ngSignalProLine()
        lines += projectsLine()
        recentDecisionLine()?.let { lines += it }
        lines += watchTowerLines()
        pendingApprovalsLine()?.let { lines += it }
        lines += connectionsLine()
        unreadNotificationsLine()?.let { lines += it }

        return ExecutiveBriefing(greeting = "Good morning.", lines = lines, generatedAt = Instant.now())
    }

    private fun ngSignalProLine(): String {
        val status = ngSignalPro.status.value
        if (status.lastUpdated == null) {
            return "NG Signal Pro: no live connection into this app yet."
        }
        val sync = if (status.warehouseSynchronized) "warehouse synchronized" else "warehouse sync pending"
        val telegram = if (status.telegramHealthy) "Telegram healthy" else "Telegram unreachable"
        return "NG Signal Pro: ${status.marketBias}, confidence ${status.confidencePercent}%, " +
            "${status.buyCandidateCount} BUY candidate(s). $sync. $telegram."
    }

    private fun projectsLine(): String {
        val dashboard = projects.dashboard.value
        return "${dashboard.activeProjects} active project(s), ${dashboard.blockedProjects} blocked, " +
            "${dashboard.openTaskCount} open task(s), ${dashboard.reachedMilestoneCount}/${dashboard.totalMilestoneCount} milestones reached."
    }

    private fun recentDecisionLine(): String? {
        val mostRecent = memory.entries.value
            .filter { it.tier == MemoryTier.DECISION }
            .maxByOrNull { it.timestamp }
            ?: return null
        return "Most recent decision: ${mostRecent.summary}"
    }

    private fun watchTowerLines(): List<String> =
        agents.results.value
            .groupBy { it.agentId }
            .mapNotNull { (agentId, results) -> results.maxByOrNull { it.completedAt } }
            .map { latest ->
                val agentName = agents.agents.value.firstOrNull { it.agentId == latest.agentId }?.name ?: latest.agentId
                "$agentName: ${latest.output}"
            }

    private fun pendingApprovalsLine(): String? {
        val pending = approvals.items.value.count { it.outcome == ApprovalOutcome.PENDING }
        if (pending == 0) return null
        return if (pending == 1) "One approval is waiting for your review." else "$pending approvals are waiting for your review."
    }

    private fun connectionsLine(): String {
        val connected = connections.connections.value.count { it.status == ConnectionStatus.CONNECTED }
        val total = connections.connections.value.size
        return "$connected of $total connection(s) active."
    }

    private fun unreadNotificationsLine(): String? {
        val unread = notifications.unreadCount.value
        if (unread == 0) return null
        return if (unread == 1) "One unread notification." else "$unread unread notifications."
    }
}
