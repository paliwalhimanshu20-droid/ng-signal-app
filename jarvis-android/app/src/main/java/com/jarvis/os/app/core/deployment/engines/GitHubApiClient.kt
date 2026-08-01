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
 * RC-001/RC-002/RC-003 "Build/Deployment/Repository engine
 * abstractions" (ASDP-001): the shared low-level GitHub REST API
 * client every engine in this package is built on --
 * [GitHubActionsBuildEngine], [GitHubApiDeploymentEngine],
 * [GitHubRepositoryProvider] -- plus [com.jarvis.os.app.core.deployment.ChangeAnalyzer],
 * which was already written against this exact class and method
 * signature. One place owns the OkHttpClient, the Owner's PAT header,
 * and GitHub's JSON error shape, so each engine only has to know its
 * own endpoints, not how to authenticate or parse a response -- same
 * real-API-not-a-simulation reasoning as
 * [com.jarvis.os.app.data.repository.RealGitHubStatusProvider], which
 * this client's request/error-handling shape deliberately mirrors.
 *
 * Reads the Owner's token from [GitHubTokenStore] itself rather than
 * taking it as a parameter on every call -- every caller already needs
 * a token lookup before it can build a URL anyway.
 *
 * [get]/[post]/[put]/[patch] all return a Kotlin [Result] rather than
 * throwing, so callers can pattern-match success vs. failure without a
 * try/catch at every call site. On failure the exception is a
 * [GitHubHttpException] when GitHub actually answered with a non-2xx
 * status (callers that care about a specific code, e.g.
 * [GitHubRepositoryProvider] treating 404 as "not found" rather than
 * an error, can check [GitHubHttpException.code]); any other failure
 * (no token configured, network error) is a plain [IllegalStateException]
 * or the underlying I/O exception.
 */
class GitHubHttpException(val code: Int, message: String) : Exception(message)

@Singleton
class GitHubApiClient @Inject constructor(
    private val tokenStore: GitHubTokenStore,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Returns a parsed JSONObject or JSONArray depending on what the endpoint returns. */
    suspend fun get(url: String): Result<Any> = request("GET", url, null)

    suspend fun post(url: String, body: JSONObject): Result<Any> = request("POST", url, body)

    suspend fun put(url: String, body: JSONObject): Result<Any> = request("PUT", url, body)

    suspend fun patch(url: String, body: JSONObject): Result<Any> = request("PATCH", url, body)

    private suspend fun request(method: String, url: String, body: JSONObject?): Result<Any> = withContext(Dispatchers.IO) {
        val token = tokenStore.currentConfig()?.personalAccessToken
            ?: return@withContext Result.failure(
                IllegalStateException("No GitHub account is connected yet. Add a Personal Access Token under Settings, GitHub."),
            )

        try {
            val requestBody = if (method == "GET") null else (body ?: JSONObject()).toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .method(method, requestBody)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val githubMessage = extractGitHubMessage(responseBody)
                    val message = when (response.code) {
                        401 -> "GitHub rejected that token -- it may be invalid or expired."
                        403 -> "GitHub rate limit reached, or the token doesn't have access to this repository."
                        404 -> "That repository or resource wasn't found."
                        422 -> "GitHub rejected the request${githubMessage?.let { " ($it)" } ?: ""}."
                        else -> "GitHub returned an error (HTTP ${response.code})${githubMessage?.let { ": $it" } ?: ""}."
                    }
                    return@use Result.failure<Any>(GitHubHttpException(response.code, message))
                }

                if (responseBody.isBlank()) return@use Result.success<Any>(JSONObject())
                val parsed: Any = if (responseBody.trimStart().startsWith("[")) JSONArray(responseBody) else JSONObject(responseBody)
                Result.success(parsed)
            }
        } catch (e: GitHubHttpException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractGitHubMessage(body: String): String? =
        runCatching { JSONObject(body).optString("message").takeUnless { it.isBlank() } }.getOrNull()
}
