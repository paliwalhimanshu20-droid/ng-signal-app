package com.jarvis.os.app.core.deployment.engines

import com.jarvis.os.app.data.settings.GitHubTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Code review RC-001/RC-002/RC-003: shared real HTTP plumbing behind
 * GitHubActionsBuildEngine, GitHubApiDeploymentEngine, and
 * GitHubRepositoryProvider -- the one place a GitHub REST call is
 * actually made from the deployment engine abstractions, so those
 * three classes stay focused on "what GitHub endpoint answers this
 * question" rather than each re-implementing auth headers and JSON
 * error handling. Reuses GitHubTokenStore (Sprint 13) for the PAT --
 * the same credential GitHubStatusProvider already uses -- rather than
 * inventing a second GitHub credential store.
 */
@Singleton
class GitHubApiClient @Inject constructor(
    private val tokenStore: GitHubTokenStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun currentToken(): String? = tokenStore.currentConfig()?.personalAccessToken

    suspend fun get(url: String): Result<Any> = request(url, "GET", null)

    suspend fun post(url: String, body: JSONObject): Result<Any> = request(url, "POST", body)

    suspend fun patch(url: String, body: JSONObject): Result<Any> = request(url, "PATCH", body)

    private suspend fun request(url: String, method: String, body: JSONObject?): Result<Any> = withContext(Dispatchers.IO) {
        val token = currentToken()
            ?: return@withContext Result.failure(IllegalStateException("GitHub isn't connected yet. Add a Personal Access Token under Settings, GitHub."))

        val builder = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").addHeader("Accept", "application/vnd.github+json")
        val requestBody = body?.toString()?.toRequestBody(jsonMediaType)
        when (method) {
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody(jsonMediaType))
            else -> builder.get()
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        401 -> "GitHub rejected that token -- it may have expired or been revoked."
                        403 -> "GitHub denied that request -- check the token's scopes (repo access is required)."
                        404 -> "GitHub returned 404 Not Found for this request."
                        422 -> "GitHub rejected the request as invalid: $text"
                        else -> "GitHub returned HTTP ${response.code}: $text"
                    }
                    return@withContext Result.failure(IllegalStateException(message))
                }
                if (text.isBlank()) return@withContext Result.success(JSONObject())
                val parsed: Any = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Couldn't reach GitHub. Check your connection and try again."))
        }
    }
}
