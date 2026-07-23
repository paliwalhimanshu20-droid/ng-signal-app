package com.jarvis.os.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Universal Connection Ecosystem -- Phase 1": "Support: Deployment
 * status, Health, Latest deployment, Connection status. If restart or
 * deployment control is unavailable, clearly report that instead of
 * fabricating controls."
 *
 * HONEST LIMIT, stated once here rather than implied: Streamlit
 * Community Cloud has no public management API. There is no way for
 * this app to check "latest deployment" history, view deploy logs, or
 * trigger a restart -- none of that is exposed by anything this app
 * can call. What IS honestly checkable: whether the app's public URL
 * currently responds, and how quickly. This class does exactly that
 * and nothing more -- a real HTTP request, a real status code, a real
 * response time, not a simulation of a richer deployment dashboard
 * this platform doesn't actually offer a way to build.
 */
data class StreamlitStatus(
    val reachable: Boolean,
    val httpStatusCode: Int?,
    val responseTimeMs: Long?,
    val checkedAt: Instant,
    val errorMessage: String? = null,
)

interface StreamlitStatusProvider {
    val status: StateFlow<StreamlitStatus?>
    suspend fun refresh(deploymentUrl: String)
}

@Singleton
class RealStreamlitStatusProvider @Inject constructor() : StreamlitStatusProvider {

    private val _status = MutableStateFlow<StreamlitStatus?>(null)
    override val status: StateFlow<StreamlitStatus?> = _status.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun refresh(deploymentUrl: String) {
        if (deploymentUrl.isBlank()) {
            _status.value = StreamlitStatus(
                reachable = false,
                httpStatusCode = null,
                responseTimeMs = null,
                checkedAt = Instant.now(),
                errorMessage = "No deployment URL is configured yet.",
            )
            return
        }

        _status.value = withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            try {
                val request = Request.Builder().url(deploymentUrl).head().build()
                client.newCall(request).execute().use { response ->
                    StreamlitStatus(
                        reachable = response.isSuccessful,
                        httpStatusCode = response.code,
                        responseTimeMs = System.currentTimeMillis() - startedAt,
                        checkedAt = Instant.now(),
                    )
                }
            } catch (e: Exception) {
                StreamlitStatus(
                    reachable = false,
                    httpStatusCode = null,
                    responseTimeMs = null,
                    checkedAt = Instant.now(),
                    errorMessage = e.message ?: "Couldn't reach that deployment.",
                )
            }
        }
    }
}
