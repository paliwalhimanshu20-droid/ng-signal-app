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
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.JarvisLanguage
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
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
 *
 * "JARVIS Personality & Experience Bible": "Every morning, every
 * afternoon, every evening, JARVIS prepares a personalized briefing...
 * conversational. Never a dashboard. Never developer language." Two
 * real fixes for that here: [greeting] now actually reflects the time
 * of day (it was hardcoded to "Good morning." regardless of when the
 * briefing was generated before this) and follows [JarvisLanguage],
 * and every line below was rewritten from a data-dump register
 * ("3 active project(s), 1 blocked") toward the Bible's own example
 * phrasing ("Here's what happened...") wherever the underlying data
 * genuinely supports that framing -- still exactly the same real
 * numbers, never invented ones.
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
    private val settingsRepository: SettingsRepository,
) {
    suspend fun generateMorningBriefing(): ExecutiveBriefing {
        val language = settingsRepository.appearance.first().language
        val lines = mutableListOf<String>()

        lines += ngSignalProLine(language)
        lines += projectsLine(language)
        recentDecisionLine(language)?.let { lines += it }
        lines += watchTowerLines()
        pendingApprovalsLine(language)?.let { lines += it }
        lines += connectionsLine(language)
        unreadNotificationsLine(language)?.let { lines += it }

        return ExecutiveBriefing(greeting = greetingFor(language), lines = lines, generatedAt = Instant.now())
    }

    private fun greetingFor(language: JarvisLanguage): String {
        val hour = LocalTime.now().hour
        return when (language) {
            JarvisLanguage.Hinglish -> when {
                hour < 12 -> "Good Morning."
                hour < 17 -> "Good Afternoon."
                else -> "Good Evening."
            }
            JarvisLanguage.Hindi -> when {
                hour < 12 -> "सुप्रभात।"
                hour < 17 -> "नमस्कार।"
                else -> "शुभ संध्या।"
            }
            JarvisLanguage.English -> when {
                hour < 12 -> "Good morning."
                hour < 17 -> "Good afternoon."
                else -> "Good evening."
            }
        }
    }

    private fun ngSignalProLine(language: JarvisLanguage): String {
        val status = ngSignalPro.status.value
        if (status.lastUpdated == null) {
            return when (language) {
                JarvisLanguage.Hinglish -> "NG Signal Pro abhi tak connect nahi hua hai."
                JarvisLanguage.Hindi -> "NG Signal Pro अभी तक कनेक्ट नहीं हुआ है।"
                JarvisLanguage.English -> "NG Signal Pro isn't connected yet."
            }
        }
        val sync = status.warehouseSynchronized
        val telegram = status.telegramHealthy
        return when (language) {
            JarvisLanguage.Hinglish -> "NG Signal Pro ne aaj ka scan complete kar diya -- ${status.marketBias}, " +
                "${status.confidencePercent}% confidence, ${status.buyCandidateCount} candidate mile." +
                if (sync && telegram) " Sab kuch healthy hai." else ""
            JarvisLanguage.Hindi -> "NG Signal Pro ने आज का scan पूरा कर दिया -- ${status.marketBias}, " +
                "${status.confidencePercent}% confidence, ${status.buyCandidateCount} candidate मिले।"
            JarvisLanguage.English -> "NG Signal Pro completed today's scan: ${status.marketBias}, " +
                "${status.confidencePercent}% confidence, ${status.buyCandidateCount} candidate(s) found." +
                if (sync && telegram) " Everything's healthy there." else ""
        }
    }

    private fun projectsLine(language: JarvisLanguage): String {
        val dashboard = projects.dashboard.value
        return when (language) {
            JarvisLanguage.Hinglish -> "ProjectOS mein ${dashboard.activeProjects} mission active hain" +
                (if (dashboard.blockedProjects > 0) ", ${dashboard.blockedProjects} blocked hai" else "") +
                ", aur ${dashboard.openTaskCount} task open hain."
            JarvisLanguage.Hindi -> "ProjectOS में ${dashboard.activeProjects} मिशन active हैं" +
                (if (dashboard.blockedProjects > 0) ", ${dashboard.blockedProjects} blocked है" else "") +
                ", और ${dashboard.openTaskCount} task open हैं।"
            JarvisLanguage.English -> "ProjectOS has ${dashboard.activeProjects} mission(s) in progress" +
                (if (dashboard.blockedProjects > 0) ", ${dashboard.blockedProjects} blocked" else "") +
                ", with ${dashboard.openTaskCount} open task(s)."
        }
    }

    private fun recentDecisionLine(language: JarvisLanguage): String? {
        val mostRecent = memory.entries.value
            .filter { it.tier == MemoryTier.DECISION }
            .maxByOrNull { it.timestamp }
            ?: return null
        return when (language) {
            JarvisLanguage.Hinglish -> "Waise, humne yeh decide kiya tha: ${mostRecent.summary}"
            JarvisLanguage.Hindi -> "वैसे, हमने यह तय किया था: ${mostRecent.summary}"
            JarvisLanguage.English -> "For context, we recently decided: ${mostRecent.summary}"
        }
    }

    /**
     * "Batman found one architecture recommendation. Flash completed
     * Android optimization." -- the Bible's own example phrasing,
     * applied to whatever specialists have real results (see this
     * file's class docstring for why this never triggers a new
     * convening). agent.output is already honest, natural language
     * (see WatchTowerAgents.kt's own Phase-0 fix) -- this just presents
     * it as one of JARVIS's own briefing lines rather than a labeled
     * status row.
     */
    private fun watchTowerLines(): List<String> =
        agents.results.value
            .groupBy { it.agentId }
            .mapNotNull { (agentId, results) -> results.maxByOrNull { it.completedAt } }
            .map { latest ->
                val agentName = agents.agents.value.firstOrNull { it.agentId == latest.agentId }?.name ?: latest.agentId
                "$agentName: ${latest.output}"
            }

    private fun pendingApprovalsLine(language: JarvisLanguage): String? {
        val pending = approvals.items.value.count { it.outcome == ApprovalOutcome.PENDING }
        if (pending == 0) return null
        return when (language) {
            JarvisLanguage.Hinglish -> if (pending == 1) "Ek approval aapka wait kar raha hai." else "$pending approvals aapka wait kar rahe hain."
            JarvisLanguage.Hindi -> if (pending == 1) "एक approval आपका इंतज़ार कर रहा है।" else "$pending approvals आपका इंतज़ार कर रहे हैं।"
            JarvisLanguage.English -> if (pending == 1) "One approval is waiting on you." else "$pending approvals are waiting on you."
        }
    }

    private fun connectionsLine(language: JarvisLanguage): String {
        val connected = connections.connections.value.count { it.status == ConnectionStatus.CONNECTED }
        val total = connections.connections.value.size
        return when (language) {
            JarvisLanguage.Hinglish -> "$connected of $total connected systems online hain."
            JarvisLanguage.Hindi -> "$connected में से $total connected systems ऑनलाइन हैं।"
            JarvisLanguage.English -> "$connected of $total connected systems are online."
        }
    }

    private fun unreadNotificationsLine(language: JarvisLanguage): String? {
        val unread = notifications.unreadCount.value
        if (unread == 0) return null
        return when (language) {
            JarvisLanguage.Hinglish -> if (unread == 1) "Ek unread notification hai." else "$unread unread notifications hain."
            JarvisLanguage.Hindi -> if (unread == 1) "एक unread notification है।" else "$unread unread notifications हैं।"
            JarvisLanguage.English -> if (unread == 1) "One unread notification." else "$unread unread notifications."
        }
    }
}
