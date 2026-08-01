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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Universal Connection Ecosystem -- Phase 1": NG Signal Pro's actual
 * Python/Streamlit code was confirmed (by checking, not assuming) to
 * live in this exact same GitHub repository, alongside jarvis-android/
 * -- `app.py`, `generate_signals.py`, `signal_log.py`, and real GitHub
 * Actions workflows (`check_signals.yml`, `generate_signals.yml`,
 * `generate_research.yml`, `migrate_csv_to_db.yml`) all confirmed
 * present before this file was written. That makes a real bridge
 * genuinely possible via the same GitHub Actions API this whole
 * project's own CI has been checked through all along -- not a
 * simulation, and not the "no live bridge exists" limitation this
 * class's Sprint 12 version stated (that was true then; it changed
 * once this repo layout was actually checked for this sprint).
 *
 * HONEST LIMIT on what this can actually report: GitHub's Actions API
 * tells you whether a workflow run succeeded or failed, and when --
 * it does NOT expose the actual signal data those workflows produce
 * (that lives in Parquet/DuckDB files this Android app has no client
 * library to read). That's why [NgSignalProStatus] no longer has a
 * marketBias/confidencePercent/buyCandidateCount -- Sprint 12's mock
 * had those fields, and they cannot be honestly filled from workflow
 * status alone. Reporting a fabricated "bullish, 87% confidence" from
 * nothing but "the job exited 0" would be exactly the fake data this
 * sprint's own brief forbids. What IS honestly reportable: whether the
 * scanner ran and succeeded, whether the warehouse sync ran and
 * succeeded, whether the alert pipeline ran and succeeded, and when.
 *
 * Gated on GitHubTokenStore having a real config, same as
 * GitHubStatusProvider -- if no GitHub account is connected, this
 * honestly reports "not connected" rather than attempting a call.
 */
data class NgSignalProStatus(
    val scannerStatusSummary: String,
    val scannerHealthy: Boolean,
    val warehouseSynchronized: Boolean,
    /** Whether check_signals.yml's most recent run succeeded -- a real proxy for "the alert pipeline ran," not a direct Telegram API health check (this app has no way to verify Telegram delivery specifically). */
    val alertPipelineHealthy: Boolean,
    val lastUpdated: Instant?,
)

interface NgSignalProStatusProvider {
    val status: StateFlow<NgSignalProStatus>
    suspend fun refresh()
}

@Singleton
class RealNgSignalProStatusProvider @Inject constructor(
    private val tokenStore: GitHubTokenStore,
) : NgSignalProStatusProvider {

    private val _status = MutableStateFlow(
        NgSignalProStatus(
            scannerStatusSummary = "Not connected yet.",
            scannerHealthy = false,
            warehouseSynchronized = false,
            alertPipelineHealthy = false,
            lastUpdated = null,
        ),
    )
    override val status: StateFlow<NgSignalProStatus> = _status.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun refresh() {
        val config = tokenStore.currentConfig()
        if (config == null) {
            _status.value = _status.value.copy(scannerStatusSummary = "NG Signal Pro isn't connected yet.")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val scanRun = latestRunFor(config.owner, config.repo, "generate_signals.yml", config.personalAccessToken)
                val warehouseRun = latestRunFor(config.owner, config.repo, "migrate_csv_to_db.yml", config.personalAccessToken)
                val alertRun = latestRunFor(config.owner, config.repo, "check_signals.yml", config.personalAccessToken)

                val scannerHealthy = scanRun?.optString("conclusion") == "success"
                val warehouseHealthy = warehouseRun?.optString("conclusion") == "success"
                val alertHealthy = alertRun?.optString("conclusion") == "success"

                val summary = when {
                    scanRun == null -> "No scan runs found yet."
                    scannerHealthy -> "Last scan completed successfully."
                    else -> "Last scan did not complete successfully."
                }

                val timestamps = listOfNotNull(
                    scanRun?.optString("updated_at")?.takeUnless { it.isBlank() },
                    warehouseRun?.optString("updated_at")?.takeUnless { it.isBlank() },
                    alertRun?.optString("updated_at")?.takeUnless { it.isBlank() },
                ).mapNotNull { runCatching { Instant.parse(it) }.getOrNull() }

                _status.value = NgSignalProStatus(
                    scannerStatusSummary = summary,
                    scannerHealthy = scannerHealthy,
                    warehouseSynchronized = warehouseHealthy,
                    alertPipelineHealthy = alertHealthy,
                    lastUpdated = timestamps.maxOrNull(),
                )
            } catch (e: Exception) {
                _status.value = _status.value.copy(
                    scannerStatusSummary = e.message ?: "Couldn't reach NG Signal Pro's status right now.",
                )
            }
        }
    }

    /** Most recent run of a specific workflow file, or null if that workflow has never run or the lookup fails. */
    private fun latestRunFor(owner: String, repo: String, workflowFileName: String, token: String): JSONObject? {
        val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/runs?per_page=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val runs: JSONArray = json.optJSONArray("workflow_runs") ?: return null
            if (runs.length() == 0) return null
            return runs.getJSONObject(0)
        }
    }
}
