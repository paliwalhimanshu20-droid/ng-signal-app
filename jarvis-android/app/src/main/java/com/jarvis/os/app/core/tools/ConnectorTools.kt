package com.jarvis.os.app.core.tools

import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.model.ToolDefinition
import com.jarvis.os.app.data.repository.CalendarFetchResult
import com.jarvis.os.app.data.repository.DriveFetchResult
import com.jarvis.os.app.data.repository.GitHubFetchResult
import com.jarvis.os.app.data.repository.GitHubStatusProvider
import com.jarvis.os.app.data.repository.GmailFetchResult
import com.jarvis.os.app.data.repository.GoogleWorkspaceFetchResult
import com.jarvis.os.app.data.repository.GoogleWorkspaceStatusProvider
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider
import com.jarvis.os.app.data.repository.StreamlitStatusProvider
import com.jarvis.os.app.data.settings.StreamlitDeploymentStore
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 13 "Connected Intelligence Platform": LOW-risk (read-only,
 * never approval-gated -- see ToolDefinition.requiresApproval) tools,
 * bound into the same Tool Framework CalculatorTool uses (Sprint 10).
 * This is deliberately the integration point for "Executive Questions"
 * rather than a separate NLP layer: JARVIS's chat pipeline already
 * discovers and calls Tool instances through ToolRepository, so a
 * connector becoming chat-answerable is "write a Tool, bind it" -- the
 * same swap point every other capability in this codebase uses.
 *
 * Sprint 15 "Executive Intelligence Completion" Phase 4 splits what was
 * one monolithic GoogleWorkspaceTool into four independent capability
 * tools (Calendar, Gmail, Drive, Health) below -- JARVIS now treats
 * "check my calendar" and "check my email" as different capabilities
 * with different toolIds, not one tool re-parsing the same message a
 * second time internally (that re-parsing was Phase 3's duplicate-
 * classification bug). Each tool also now carries its own
 * triggerKeywords (Phase 8) -- IntentRouter has no per-connector
 * table of its own; it just asks every registered tool "do you want
 * this message" via that field.
 *
 * Each tool still does simple keyword matching on the raw input text
 * for its OWN internal sub-questions (e.g. GitHubStatusTool deciding
 * "failed workflows" vs "recent commits" once IT has already been
 * selected) -- deterministic, not an LLM call, matching CalculatorTool's
 * own "no third-party dependency, verify what this sandbox can actually
 * run" preference. That's a different, narrower kind of matching than
 * routing decides which tool to run at all -- see IntentRouter's own
 * docstring for that distinction.
 */
@Singleton
class GitHubStatusTool @Inject constructor(
    private val provider: GitHubStatusProvider,
) : Tool {
    override val definition = ToolDefinition(
        toolId = "github_status",
        name = "GitHub Status",
        description = "Answers questions about the connected GitHub repository: failed workflows, what changed today, open PRs, recent commits.",
        riskLevel = RiskLevel.LOW,
        triggerKeywords = setOf(
            "github", "pull request", "pull requests", "open pr", "workflow run", "workflow status",
            "failed workflow", "ci status", "build status", "github actions", "my repo", "repo status",
            "my commits", "recent commits", "what changed", "my issues", "open issues",
        ),
    )

    override suspend fun execute(input: String): ToolResult {
        provider.refresh()
        val result = provider.status.value
        return when (result) {
            null -> ToolResult.Failure("GitHub status hasn't been fetched yet.")
            is GitHubFetchResult.Failure -> ToolResult.Failure(result.message)
            is GitHubFetchResult.Success -> {
                val status = result.status
                val q = input.lowercase()
                val output = when {
                    "fail" in q && "workflow" in q -> {
                        val failed = status.recentWorkflowRuns.filter { it.conclusion != null && it.conclusion != "success" }
                        if (failed.isEmpty()) "No failed workflows in the last ${status.recentWorkflowRuns.size} runs."
                        else "${failed.size} failed workflow(s): " + failed.joinToString { "${it.workflowName} (${it.conclusion})" }
                    }
                    "commit" in q || "changed today" in q || "changed yesterday" in q -> {
                        val today = LocalDate.now(ZoneId.systemDefault())
                        val cutoff = if ("yesterday" in q) today.minusDays(1) else today
                        val relevant = status.recentCommits.filter { c ->
                            c.committedAt?.atZone(ZoneId.systemDefault())?.toLocalDate() == cutoff
                        }
                        val list = relevant.ifEmpty { status.recentCommits.take(5) }
                        if (list.isEmpty()) "No commits found on ${status.defaultBranch}."
                        else list.joinToString("\n") { "${it.shortSha} ${it.messageHeadline} -- ${it.authorName}" }
                    }
                    "pr" in q || "pull request" in q -> {
                        if (status.openPullRequestCount == 0) "No open pull requests."
                        else "${status.openPullRequestCount} open PR(s): " + status.recentPullRequestTitles.joinToString()
                    }
                    else -> "${status.repoFullName}: ${status.openPullRequestCount} open PR(s), ${status.openIssueCount} open issue(s), " +
                        "${status.recentWorkflowRuns.count { it.conclusion != null && it.conclusion != "success" }} recently failed workflow(s)."
                }
                ToolResult.Success(output)
            }
        }
    }
}

@Singleton
class NgSignalProStatusTool @Inject constructor(
    private val provider: NgSignalProStatusProvider,
) : Tool {
    override val definition = ToolDefinition(
        toolId = "ng_signal_pro_status",
        name = "NG Signal Pro Status",
        description = "Answers questions about NG Signal Pro's scanner, warehouse, and alert pipeline health via GitHub Actions run status.",
        riskLevel = RiskLevel.LOW,
        triggerKeywords = setOf(
            "ng signal", "signal pro", "scanner healthy", "scanner status", "scanner running",
            "warehouse sync", "warehouse status", "warehouse updated", "live trades", "any trades",
            "trading signal", "trading signals",
        ),
    )

    override suspend fun execute(input: String): ToolResult {
        provider.refresh()
        val status = provider.status.value
        val q = input.lowercase()
        val output = when {
            "trade" in q ->
                // HONEST LIMIT (matches this provider's own class docstring):
                // GitHub Actions run status cannot tell us how many live
                // trades are open or whether one fired today -- that data
                // lives in Parquet/DuckDB files this app has no reader for.
                // Reporting a number here would be exactly the fabricated
                // data this sprint's brief forbids.
                "I can't tell you live trade counts from here -- that data lives in NG Signal Pro's own database, which this app doesn't have a reader for yet. Open the Streamlit dashboard to check."
            "scanner" in q && "healthy" in q ->
                if (status.scannerHealthy) "Scanner is healthy. ${status.scannerStatusSummary}" else "Scanner is not healthy. ${status.scannerStatusSummary}"
            "warehouse" in q ->
                if (status.warehouseSynchronized) "Warehouse sync is up to date." else "Warehouse sync last run did not complete successfully."
            else -> "${status.scannerStatusSummary} Warehouse ${if (status.warehouseSynchronized) "synced" else "not synced"}, alert pipeline ${if (status.alertPipelineHealthy) "healthy" else "unhealthy"}."
        }
        return ToolResult.Success(output)
    }
}

@Singleton
class StreamlitStatusTool @Inject constructor(
    private val provider: StreamlitStatusProvider,
    private val deploymentStore: StreamlitDeploymentStore,
) : Tool {
    override val definition = ToolDefinition(
        toolId = "streamlit_status",
        name = "Streamlit Status",
        description = "Checks whether the deployed Streamlit app (NG Signal Pro's dashboard) is reachable and how it's responding.",
        riskLevel = RiskLevel.LOW,
        triggerKeywords = setOf(
            "streamlit", "deployment healthy", "deployment status", "deployment problems",
            "dashboard reachable", "dashboard up", "dashboard status", "app healthy",
            "open ng signal pro", "open the dashboard",
        ),
    )

    override suspend fun execute(input: String): ToolResult {
        val url = deploymentStore.currentUrl()
            ?: return ToolResult.Failure("No Streamlit deployment URL is configured yet. Add one under Settings, Streamlit.")
        provider.refresh(url)
        val status = provider.status.value ?: return ToolResult.Failure("Streamlit status hasn't been checked yet.")
        val output = if (status.reachable) {
            "Streamlit is healthy -- responded HTTP ${status.httpStatusCode} in ${status.responseTimeMs}ms."
        } else {
            "Streamlit is not reachable: ${status.errorMessage ?: "HTTP ${status.httpStatusCode}"}."
        }
        return ToolResult.Success(output)
    }
}

// --- Google Workspace: four independent capabilities, Sprint 15 Phase 4 ---
// All four share RealGoogleWorkspaceStatusProvider (OAuth/token store/HTTP
// client are genuinely one thing underneath -- see that class's docstring),
// but each calls only the ONE provider method its own capability needs
// (Phase 5), and each owns its own triggerKeywords (Phase 8) so
// IntentRouter routes "check my calendar" straight to GoogleCalendarTool
// without ever touching Gmail or Drive.

@Singleton
class GoogleCalendarTool @Inject constructor(
    private val provider: GoogleWorkspaceStatusProvider,
) : Tool {
    override val definition = ToolDefinition(
        toolId = "google_calendar",
        name = "Google Calendar",
        description = "Answers questions about today's calendar: what's on, the next meeting, whether you're free.",
        riskLevel = RiskLevel.LOW,
        triggerKeywords = setOf(
            "calendar", "agenda", "my meetings", "meetings today", "meetings", "any meetings",
            "schedule for today", "my schedule", "today's schedule", "what's on my", "upcoming events",
            "next meeting", "free this afternoon", "am i free", "am i busy",
        ),
    )

    override suspend fun execute(input: String): ToolResult {
        return when (val result = provider.getTodaysEvents()) {
            is CalendarFetchResult.Failure -> ToolResult.Failure(result.message)
            is CalendarFetchResult.Success -> {
                val events = result.events
                val q = input.lowercase()
                val output = when {
                    "next meeting" in q || "next event" in q ->
                        events.firstOrNull()?.let { "Your next meeting today: ${it.title} at ${it.startTime}." }
                            ?: "No meetings left today."
                    "free" in q ->
                        if (events.isEmpty()) "You're free all day today."
                        else "You have ${events.size} event(s) today, so not fully free: " + events.joinToString { "${it.title} at ${it.startTime}" }
                    else ->
                        if (events.isEmpty()) "Nothing on the calendar today."
                        else "Today: " + events.joinToString { "${it.title} at ${it.startTime}" }
                }
                ToolResult.Success(output)
            }
        }
    }
}

@Singleton
class GoogleGmailTool @Inject constructor(
    private val provider: GoogleWorkspaceStatusProvider,
) : Tool {
    override val definition = ToolDefinition(
        toolId = "google_gmail",
        name = "Gmail",
        description = "Answers questions about unread and important email.",
        riskLevel = RiskLevel.LOW,
        triggerKeywords = setOf(
            "gmail", "my email", "my inbox", "unread mail", "unread email", "important email",
            "email summary", "summarize my email", "summarize my emails", "inbox summary",
            "new emails", "any emails",
        ),
    )

    override suspend fun execute(input: String): ToolResult {
        return when (val result = provider.getUnreadEmails()) {
            is GmailFetchResult.Failure -> ToolResult.Failure(result.message)
            is GmailFetchResult.Success -> ToolResult.Success(
                if (result.importantEmails.isEmpty()) "No important unread email (${result.unreadCount} unread total)."
                else "${result.unreadCount} unread. Important: " + result.importantEmails.joinToString { "\"${it.subject}\" from ${it.from}" },
            )
        }
    }
}

@Singleton
class GoogleDriveTool @Inject constructor(
    private val provider: GoogleWorkspaceStatusProvider,
) : Tool {
    override val definition = ToolDefinition(
        toolId = "google_drive",
        name = "Google Drive",
        description = "Answers questions about recent Drive files.",
        riskLevel = RiskLevel.LOW,
        triggerKeywords = setOf(
            "google drive", "my drive", "drive files", "recent documents", "recent files",
            "latest files", "latest documents", "my files", "search drive",
        ),
    )

    override suspend fun execute(input: String): ToolResult {
        return when (val result = provider.getRecentDriveFiles()) {
            is DriveFetchResult.Failure -> ToolResult.Failure(result.message)
            is DriveFetchResult.Success -> ToolResult.Success(
                if (result.recentFiles.isEmpty()) "No recent Drive files found."
                else "Recent Drive files: " + result.recentFiles.joinToString { it.name },
            )
        }
    }
}

@Singleton
class GoogleWorkspaceHealthTool @Inject constructor(
    private val provider: GoogleWorkspaceStatusProvider,
) : Tool {
    override val definition = ToolDefinition(
        toolId = "google_workspace_health",
        name = "Google Workspace Health",
        description = "Reports whether Google Workspace is connected and a combined snapshot across Calendar, Gmail, and Drive.",
        riskLevel = RiskLevel.LOW,
        triggerKeywords = setOf(
            "is google connected", "google connected", "google workspace connected",
            "workspace connected", "reconnect google", "google workspace health", "workspace status",
            "workspace healthy", "is google workspace",
        ),
    )

    override suspend fun execute(input: String): ToolResult {
        provider.refreshAll()
        return when (val result = provider.status.value) {
            null -> ToolResult.Failure("Google Workspace status hasn't been checked yet.")
            is GoogleWorkspaceFetchResult.Failure -> ToolResult.Failure(result.message)
            is GoogleWorkspaceFetchResult.Success -> ToolResult.Success(
                "Google Workspace is connected as ${result.status.accountEmail}. " +
                    "${result.status.todaysEvents.size} event(s) today, ${result.status.unreadEmailCount} unread email(s), " +
                    "${result.status.recentDriveFiles.size} recent Drive file(s).",
            )
        }
    }
}
