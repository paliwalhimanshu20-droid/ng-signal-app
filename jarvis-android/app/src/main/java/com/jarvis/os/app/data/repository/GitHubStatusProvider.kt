package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.settings.GitHubTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One real GitHub Actions workflow run -- not a fabricated status, the actual most recent run's real conclusion. */
data class GitHubWorkflowRunSummary(
    val workflowName: String,
    val status: String,
    val conclusion: String?,
    val updatedAt: Instant?,
)

data class GitHubStatus(
    val repoFullName: String,
    val defaultBranch: String,
    val openPullRequestCount: Int,
    val recentPullRequestTitles: List<String>,
    val openIssueCount: Int,
    val recentWorkflowRuns: List<GitHubWorkflowRunSummary>,
    val fetchedAt: Instant,
)

sealed interface GitHubFetchResult {
    data class Success(val status: GitHubStatus) : GitHubFetchResult
    data class Failure(val message: String) : GitHubFetchResult
}

/**
 * "Universal Connection Ecosystem -- Phase 1": a real GitHub REST API
 * client -- api.github.com, not a simulation. Requires the Owner's own
 * Personal Access Token (see GitHubTokenStore); returns an honest
 * Failure (never a crash, never a silently-empty success) when no
 * token is configured, the token is invalid, the repo doesn't exist,
 * or the network call fails for any reason -- see [GitHubFetchResult].
 *
 * Deliberately NOT gated on ConnectionRepository's approval state
 * inside this class -- that would create a dependency this repository
 * layer doesn't otherwise have (ConnectionRepository's own docstring:
 * "JarvisCore coordinates only," nothing else depends on it directly
 * except through JarvisCore). The calling ViewModel is responsible for
 * only invoking [refresh] when the Owner has actually approved and
 * connected GitHub -- same governance-respecting shape as every other
 * real integration in this delivery.
 *
 * Issues vs. pull requests: GitHub's `/issues` endpoint includes pull
 * requests by default (a real, well-known API quirk, not a Kotlin
 * detail) -- every returned item that has a `pull_request` key is
 * actually a PR, not an issue, and is filtered out before counting.
 */
interface GitHubStatusProvider {
    val status: StateFlow<GitHubFetchResult?>
    suspend fun refresh()
}

@Singleton
class RealGitHubStatusProvider @Inject constructor(
    private val tokenStore: GitHubTokenStore,
) : GitHubStatusProvider {

    private val _status = MutableStateFlow<GitHubFetchResult?>(null)
    override val status: StateFlow<GitHubFetchResult?> = _status.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun refresh() {
        val config = tokenStore.currentConfig()
        if (config == null) {
            _status.value = GitHubFetchResult.Failure("No GitHub account is connected yet. Add a Personal Access Token under Settings, GitHub.")
            return
        }

        _status.value = withContext(Dispatchers.IO) {
            try {
                val repoJson = getJson("https://api.github.com/repos/${config.owner}/${config.repo}", config.personalAccessToken)
                    as? JSONObject ?: return@withContext GitHubFetchResult.Failure("Couldn't read that repository. Check the owner and repo name under Settings, GitHub.")

                val defaultBranch = repoJson.optString("default_branch", "main")
                val fullName = repoJson.optString("full_name", "${config.owner}/${config.repo}")

                val pullsJson = getJson("https://api.github.com/repos/${config.owner}/${config.repo}/pulls?state=open&per_page=10", config.personalAccessToken) as? JSONArray
                val openPrTitles = mutableListOf<String>()
                if (pullsJson != null) {
                    for (i in 0 until pullsJson.length()) {
                        openPrTitles += pullsJson.getJSONObject(i).optString("title", "Untitled")
                    }
                }

                val issuesJson = getJson("https://api.github.com/repos/${config.owner}/${config.repo}/issues?state=open&per_page=30", config.personalAccessToken) as? JSONArray
                var issueCount = 0
                if (issuesJson != null) {
                    for (i in 0 until issuesJson.length()) {
                        val item = issuesJson.getJSONObject(i)
                        if (!item.has("pull_request")) issueCount += 1
                    }
                }

                val runsJson = getJson("https://api.github.com/repos/${config.owner}/${config.repo}/actions/runs?per_page=5", config.personalAccessToken) as? JSONObject
                val runsArray = runsJson?.optJSONArray("workflow_runs")
                val recentRuns = mutableListOf<GitHubWorkflowRunSummary>()
                if (runsArray != null) {
                    for (i in 0 until runsArray.length()) {
                        val run = runsArray.getJSONObject(i)
                        recentRuns += GitHubWorkflowRunSummary(
                            workflowName = run.optString("name", "workflow"),
                            status = run.optString("status", "unknown"),
                            conclusion = if (run.isNull("conclusion")) null else run.optString("conclusion").takeUnless { it.isBlank() },
                            updatedAt = parseInstantOrNull(if (run.has("updated_at")) run.optString("updated_at") else null),
                        )
                    }
                }

                GitHubFetchResult.Success(
                    GitHubStatus(
                        repoFullName = fullName,
                        defaultBranch = defaultBranch,
                        openPullRequestCount = openPrTitles.size,
                        recentPullRequestTitles = openPrTitles.take(5),
                        openIssueCount = issueCount,
                        recentWorkflowRuns = recentRuns,
                        fetchedAt = Instant.now(),
                    ),
                )
            } catch (e: Exception) {
                GitHubFetchResult.Failure(e.message ?: "Couldn't reach GitHub. Check your connection and try again.")
            }
        }
    }

    /** Returns a parsed JSONObject or JSONArray depending on what the endpoint returns, or throws on any non-2xx response with the real status code in the message. */
    private fun getJson(url: String, token: String): Any {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "GitHub rejected that token -- it may be invalid or expired."
                    403 -> "GitHub rate limit reached, or the token doesn't have access to this repository."
                    404 -> "That repository wasn't found -- check the owner and repo name."
                    else -> "GitHub returned an error (HTTP ${response.code})."
                }
                throw IllegalStateException(message)
            }
            return if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
        }
    }

    private fun parseInstantOrNull(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
