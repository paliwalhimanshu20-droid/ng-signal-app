package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.data.model.ToolHealthStatus
import com.jarvis.os.app.data.repository.AuditRepository
import com.jarvis.os.app.data.repository.ToolRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostics: answers "run diagnostics", "system health", "tool health", "any errors", "audit
 * log" directly from [ToolRepository.health] and [AuditRepository.entries] -- the same
 * append-only audit trail JarvisCore's own init-block collector is the sole writer of (see that
 * class's docstring), so this handler only ever reads real, already-recorded facts, never a
 * live probe of its own.
 *
 * KNOWN GAP, stated plainly (matching this codebase's own honesty convention): the per-connector
 * StatusProviders (GitHubStatusProvider, GoogleWorkspaceStatusProvider, NgSignalProStatusProvider,
 * StreamlitStatusProvider) are not wired into this handler yet -- each already has a
 * corresponding *StatusTool bound into ToolRepository (see ToolModule), so their health already
 * surfaces here indirectly via [ToolRepository.health] without a direct dependency on all four
 * provider classes. A future pass that wants provider-level diagnostic detail (not just
 * tool-level) is additive from here, not a redesign.
 */
@Singleton
class DiagnosticsLocalIntentHandler @Inject constructor(
    private val tools: ToolRepository,
    private val audit: AuditRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.DIAGNOSTICS

    override suspend fun tryHandle(text: String): LocalIntentAnswer? = answer(text)?.let { LocalIntentAnswer(it) }

    private suspend fun answer(text: String): String? {
        val lower = text.lowercase()
        return when {
            AUDIT_KEYWORDS.any { it in lower } -> handleAuditLog()
            HEALTH_KEYWORDS.any { it in lower } -> handleHealth()
            else -> null
        }
    }

    private fun handleHealth(): String {
        val statuses = tools.health.value
        if (statuses.isEmpty()) return "No tool health data recorded yet."
        val degraded = statuses.filterValues { it != ToolHealthStatus.HEALTHY }
        return if (degraded.isEmpty()) {
            "All ${statuses.size} tool(s) report HEALTHY."
        } else {
            "${degraded.size} of ${statuses.size} tool(s) are not healthy: " +
                degraded.entries.joinToString("; ") { (toolId, status) -> "$toolId: $status" } + "."
        }
    }

    private fun handleAuditLog(): String {
        val recent = audit.entries.value.takeLast(5)
        if (recent.isEmpty()) return "No audit entries recorded yet."
        return "Last ${recent.size} audit entries: " +
            recent.joinToString("; ") { e -> "[${e.category}] ${e.summary}" } + "."
    }

    companion object {
        private val HEALTH_KEYWORDS = setOf("run diagnostics", "system health", "tool health", "any errors", "health check")
        private val AUDIT_KEYWORDS = setOf("audit log", "audit trail", "recent activity", "what happened recently")
    }
}
