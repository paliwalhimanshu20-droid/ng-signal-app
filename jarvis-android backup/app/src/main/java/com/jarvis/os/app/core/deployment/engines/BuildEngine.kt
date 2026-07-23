package com.jarvis.os.app.core.deployment.engines

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Code review RC-001 "Preserve Build Engine Abstraction": Mission
 * Control (ASDP-001 Phase 7/8, not yet built) must talk only to this
 * interface, never directly to GitHub Actions -- so a future
 * LocalBuildEngine/CloudBuildEngine/EnterpriseBuildEngine/
 * CustomBuildEngine is "implement BuildEngine," not "rewrite Mission
 * Control's build UI." Only GitHubActionsBuildEngine exists today, per
 * this review's own "today only [current] should be implemented"
 * instruction -- see ASDP-001's own feasibility note on why a real
 * on-device LocalBuildEngine isn't just unbuilt yet but genuinely
 * blocked on stock Android (no reachable JDK/Gradle/Android-SDK
 * toolchain from a normal app's sandbox) until that changes.
 */
enum class BuildState {
    QUEUED, PREPARING, UPLOADING_SOURCES, STARTING_BUILD, BUILDING,
    RUNNING_TESTS, PACKAGING, DOWNLOADING_ARTIFACT, READY_TO_INSTALL,
    COMPLETED, FAILED, CANCELLED,
}

data class BuildProgress(val state: BuildState, val message: String, val progressPercent: Int?)

sealed interface BuildTriggerResult {
    data class Triggered(val buildId: String) : BuildTriggerResult
    data class Failure(val message: String) : BuildTriggerResult
}

sealed interface BuildStatusResult {
    data class InProgress(val progress: BuildProgress) : BuildStatusResult
    data class Success(val progress: BuildProgress, val artifactDownloadUrl: String) : BuildStatusResult
    data class Failure(val progress: BuildProgress, val message: String) : BuildStatusResult
}

interface BuildEngine {
    val engineName: String

    /** [ref] is a branch or commit SHA to build -- ASDP-001 Phase 7's "no manual build command selection" means the workflow itself (not this call) decides how to build; this call only decides WHAT to build. */
    suspend fun triggerBuild(owner: String, repo: String, ref: String): BuildTriggerResult

    /** Polled, not pushed -- ASDP-001 Phase 8's "monitor progress" / this review's RC-004 "live progress instead of simple polling" is a UI-layer concern (a polling loop calling this repeatedly, shown as BuildState transitions), not something this interface itself needs to be a stream/socket for. Keeping this a plain suspend function is the boring, correct choice for a v1 -- a future SSE/WebSocket-based engine implementation can still satisfy this same interface by polling its own internal cache instead of GitHub every call. */
    suspend fun checkStatus(owner: String, repo: String, buildId: String): BuildStatusResult
}

@Singleton
class GitHubActionsBuildEngine @Inject constructor(
    private val api: GitHubApiClient,
) : BuildEngine {

    override val engineName: String = "GitHub Actions"

    /**
     * HONEST LIMIT, real today: ASDP-001 Phase 7 also asks to "create
     * workflow if missing" -- that's not built yet, so triggerBuild
     * below fails with a clear, specific message if no workflow file
     * matching [WORKFLOW_FILE_NAME] exists in the target repo, rather
     * than silently doing nothing or fabricating a build ID. Workflow
     * auto-creation is real future work (see ASDP-001's sequencing),
     * not something this class pretends to already do.
     */
    override suspend fun triggerBuild(owner: String, repo: String, ref: String): BuildTriggerResult {
        val body = JSONObject().apply {
            put("ref", ref)
        }
        val dispatchResult = api.post(
            "https://api.github.com/repos/$owner/$repo/actions/workflows/$WORKFLOW_FILE_NAME/dispatches",
            body,
        )
        if (dispatchResult.isFailure) {
            val message = dispatchResult.exceptionOrNull()?.message ?: "Failed to trigger a build."
            return BuildTriggerResult.Failure(
                if (message.contains("404")) {
                    "No '$WORKFLOW_FILE_NAME' workflow found in $owner/$repo yet -- workflow auto-creation isn't built yet (see ASDP-001 Phase 7)."
                } else {
                    message
                },
            )
        }

        // GitHub's workflow_dispatch endpoint returns no body/run ID on
        // success (a real, documented GitHub API quirk, not a bug in
        // this client) -- the actual run has to be looked up separately
        // by listing recent runs for this workflow immediately after.
        // Real API call, not a fabricated ID.
        val runsResult = api.get("https://api.github.com/repos/$owner/$repo/actions/workflows/$WORKFLOW_FILE_NAME/runs?per_page=1")
        val runId = ((runsResult.getOrNull() as? JSONObject)?.optJSONArray("workflow_runs")?.optJSONObject(0))?.optLong("id")
            ?: return BuildTriggerResult.Failure("Build was triggered, but its run ID couldn't be confirmed yet -- check GitHub Actions directly.")

        return BuildTriggerResult.Triggered(buildId = runId.toString())
    }

    override suspend fun checkStatus(owner: String, repo: String, buildId: String): BuildStatusResult {
        val result = api.get("https://api.github.com/repos/$owner/$repo/actions/runs/$buildId")
        val json = result.getOrNull() as? JSONObject
            ?: return BuildStatusResult.Failure(
                BuildProgress(BuildState.FAILED, "Couldn't check build status.", null),
                result.exceptionOrNull()?.message ?: "Unknown error.",
            )

        val status = json.optString("status") // queued | in_progress | completed
        val conclusion = json.optString("conclusion", "").takeUnless { it.isBlank() } // success | failure | cancelled | ...

        val state = when {
            status == "queued" -> BuildState.QUEUED
            status == "in_progress" -> BuildState.BUILDING
            status == "completed" && conclusion == "success" -> BuildState.COMPLETED
            status == "completed" && conclusion == "cancelled" -> BuildState.CANCELLED
            status == "completed" -> BuildState.FAILED
            else -> BuildState.PREPARING
        }
        val progress = BuildProgress(state, "GitHub Actions run $buildId: $status${conclusion?.let { " ($it)" } ?: ""}", progressPercent = null)

        return when (state) {
            BuildState.COMPLETED -> {
                val artifactsResult = api.get("https://api.github.com/repos/$owner/$repo/actions/runs/$buildId/artifacts")
                val firstArtifact = (artifactsResult.getOrNull() as? JSONObject)?.optJSONArray("artifacts")?.optJSONObject(0)
                val downloadUrl = firstArtifact?.optString("archive_download_url")
                if (downloadUrl.isNullOrBlank()) {
                    BuildStatusResult.Failure(progress, "Build succeeded but no downloadable artifact was found.")
                } else {
                    BuildStatusResult.Success(progress, downloadUrl)
                }
            }
            BuildState.FAILED, BuildState.CANCELLED -> BuildStatusResult.Failure(progress, "Build ended with status: $status/$conclusion")
            else -> BuildStatusResult.InProgress(progress)
        }
    }

    companion object {
        /** Real limit, stated once: ASDP-001 Phase 7 hasn't built per-project workflow generation yet, so this assumes a workflow file with this exact name already exists in the target repo (e.g. the same "build.yml" pattern this ecosystem's own ng-signal-app repo already uses for its Actions). */
        private const val WORKFLOW_FILE_NAME = "build.yml"
    }
}
