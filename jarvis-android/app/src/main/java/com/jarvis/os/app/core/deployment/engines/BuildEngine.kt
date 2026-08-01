package com.jarvis.os.app.core.deployment.engines

import kotlinx.coroutines.delay
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A human-readable status line for the in-progress case -- what [com.jarvis.os.app.feature.deployment.DeploymentCenterViewModel.pollBuild] shows the Owner while polling (e.g. "Status: in_progress"). */
data class BuildProgress(val message: String)

/** Result of asking a CI provider to start a build for a branch that was just pushed to. */
sealed interface BuildTriggerResult {
    /** [buildId] is opaque to the caller -- whatever this engine's own [BuildEngine.checkStatus] needs to look the same run back up. */
    data class Triggered(val buildId: String) : BuildTriggerResult
    data class Failure(val message: String) : BuildTriggerResult
}

/** Result of polling a previously triggered build. */
sealed interface BuildStatusResult {
    data class InProgress(val progress: BuildProgress) : BuildStatusResult
    data object Success : BuildStatusResult
    data class Failure(val message: String) : BuildStatusResult
}

/**
 * RC-001 "Build engine abstraction" (ASDP-001): triggers and monitors
 * the CI build for a branch that [DeploymentEngine] just pushed to.
 * One real implementation today -- [GitHubActionsBuildEngine] -- kept
 * behind this interface for the same "swap point" reasoning as every
 * other Mock/Real pair in [com.jarvis.os.app.di.RepositoryModule]: a
 * future provider (e.g. a hosted CI service) is a new implementation
 * of this interface, not a change to
 * [com.jarvis.os.app.feature.deployment.DeploymentCenterViewModel].
 */
interface BuildEngine {
    suspend fun triggerBuild(owner: String, repo: String, branch: String): BuildTriggerResult
    suspend fun checkStatus(owner: String, repo: String, buildId: String): BuildStatusResult
}

/**
 * Real GitHub Actions implementation. GitHub's workflow_dispatch API
 * has no "run id" in its own response (a successful dispatch is an
 * empty 204) -- so [triggerBuild] dispatches the repository's first
 * enabled workflow, then looks up the newest run GitHub just created
 * for that workflow+branch to get a real run id to hand back. Real
 * limit stated honestly: if GitHub is slow enough that the new run
 * isn't visible yet after a few short retries, this reports a Failure
 * rather than fabricating a run id.
 */
@Singleton
class GitHubActionsBuildEngine @Inject constructor(
    private val api: GitHubApiClient,
) : BuildEngine {

    override suspend fun triggerBuild(owner: String, repo: String, branch: String): BuildTriggerResult {
        val workflowsResult = api.get("https://api.github.com/repos/$owner/$repo/actions/workflows")
        val workflowsJson = workflowsResult.getOrNull() as? JSONObject
            ?: return BuildTriggerResult.Failure(workflowsResult.exceptionOrNull()?.message ?: "Couldn't list GitHub Actions workflows for $owner/$repo.")

        val workflows = workflowsJson.optJSONArray("workflows")
        val activeWorkflow = (0 until (workflows?.length() ?: 0))
            .mapNotNull { workflows?.optJSONObject(it) }
            .firstOrNull { it.optString("state") == "active" }
            ?: return BuildTriggerResult.Failure("No active GitHub Actions workflow found in $owner/$repo. Add a workflow file (e.g. .github/workflows/build.yml) first.")

        val workflowId = activeWorkflow.optLong("id")

        val dispatchBody = JSONObject().put("ref", branch)
        val dispatchResult = api.post("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/dispatches", dispatchBody)
        if (dispatchResult.isFailure) {
            return BuildTriggerResult.Failure(dispatchResult.exceptionOrNull()?.message ?: "GitHub declined to dispatch the workflow.")
        }

        repeat(MAX_LOOKUP_ATTEMPTS) {
            delay(LOOKUP_RETRY_DELAY_MS)
            val runsResult = api.get("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowId/runs?branch=$branch&per_page=1")
            val runsJson = runsResult.getOrNull() as? JSONObject
            val latestRun = runsJson?.optJSONArray("workflow_runs")?.optJSONObject(0)
            if (latestRun != null) {
                return BuildTriggerResult.Triggered(latestRun.optLong("id").toString())
            }
        }

        return BuildTriggerResult.Failure("The workflow was dispatched, but GitHub hasn't shown a new run for it yet. Check GitHub Actions directly.")
    }

    override suspend fun checkStatus(owner: String, repo: String, buildId: String): BuildStatusResult {
        val result = api.get("https://api.github.com/repos/$owner/$repo/actions/runs/$buildId")
        val runJson = result.getOrNull() as? JSONObject
            ?: return BuildStatusResult.Failure(result.exceptionOrNull()?.message ?: "Couldn't read the status of that build.")

        val status = runJson.optString("status")
        if (status != "completed") {
            return BuildStatusResult.InProgress(BuildProgress("Status: $status"))
        }

        val conclusion = runJson.optString("conclusion")
        return if (conclusion == "success") {
            BuildStatusResult.Success
        } else {
            BuildStatusResult.Failure("Workflow run finished with conclusion: ${conclusion.ifBlank { "unknown" }}")
        }
    }

    private companion object {
        const val MAX_LOOKUP_ATTEMPTS = 5
        const val LOOKUP_RETRY_DELAY_MS = 2_000L
    }
}
