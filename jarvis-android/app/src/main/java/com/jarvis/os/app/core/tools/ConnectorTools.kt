package com.jarvis.os.app.core.tools

import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.model.ToolDefinition
import com.jarvis.os.app.data.repository.GitHubFetchResult
import com.jarvis.os.app.data.repository.GitHubStatusProvider
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
 * Sprint 13 "Connected Intelligence Platform": four LOW-risk (read-only,
 * never approval-gated -- see ToolDefinition.requiresApproval) tools,
 * one per connector, bound into the same Tool Framework CalculatorTool
 * uses (Sprint 10). This is deliberately the integration point for
 * "Executive Questions" rather than a separate NLP layer: JARVIS's
 * chat pipeline already discovers and calls Tool instances through
 * ToolRepository, so a connector becoming chat-answerable is "write a
 * Tool, bind it" -- the same swap point every other capability in this
 * codebase uses, not a bespoke question-router this sprint would have
 * to maintain in parallel.
 *
 * Each tool does simple keyword matching on the raw input text against
 * this sprint's own listed executive questions (e.g. "any failed
 * workflows", "what changed today") -- deterministic, not an LLM call,
 * matching CalculatorTool's own "no third-party dependency, verify
 * what this sandbox can actually run" preference. It reads whatever
 * the underlying *StatusProvider already fetched (calling refresh()
 * first so the answer is current) and reports the real fields on that
 * status -- never a fabricated number for something the provider
 * itself cannot honestly know (see NgSignalProStatusTool below for the
 * clearest example of that limit).
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

@Singleton
class GoogleWorkspaceTool @Inject constructor(
    private val provider: GoogleWorkspaceStatusProvider,
) : Tool {
    override val definition = ToolDefinition(
        toolId = "google_workspace_status",
        name = "Google Workspace",
        description = "Answers questions about today's calendar and important unread emails via Gmail and Calendar.",
        riskLevel = RiskLevel.LOW,
    )

    override suspend fun execute(input: String): ToolResult {
        provider.refresh()
        val result = provider.status.value
        return when (result) {
            null -> ToolResult.Failure("Google Workspace status hasn't been fetched yet.")
            is GoogleWorkspaceFetchResult.Failure -> ToolResult.Failure(result.message)
            is GoogleWorkspaceFetchResult.Success -> {
                val status = result.status
                val q = input.lowercase()
                val output = when {
                    "calendar" in q || "agenda" in q || "meeting" in q ->
                        if (status.todaysEvents.isEmpty()) "Nothing on the calendar today."
                        else "Today: " + status.todaysEvents.joinToString { "${it.title} at ${it.startTime}" }
                    "email" in q || "mail" in q || "inbox" in q ->
                        if (status.importantEmails.isEmpty()) "No important unread email (${status.unreadEmailCount} unread total)."
                        else "${status.unreadEmailCount} unread. Important: " + status.importantEmails.joinToString { "\"${it.subject}\" from ${it.from}" }
                    else -> "${status.todaysEvents.size} event(s) today, ${status.unreadEmailCount} unread email(s)."
                }
                ToolResult.Success(output)
            }
        }
    }
}
