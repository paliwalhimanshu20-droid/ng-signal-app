package com.jarvis.os.app.core.chat

import android.util.Log
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.GeminiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 * "AI Provider Stabilization & Truthfulness Audit": this file had a
 * real bug, not a hypothetical one -- confirmed by the Owner actually
 * testing the app. `if (!response.isSuccessful)` discarded the
 * response body on every error, so a 429 always read as a bare "HTTP
 * 429" regardless of whether Gemini's own error body said
 * RESOURCE_EXHAUSTED (rate limit), INVALID_ARGUMENT (bad request), or
 * anything else -- the actual reason was thrown away before this class
 * ever looked at it. Fixed: the error body is now read and parsed for
 * Gemini's real error shape (`error.status`/`error.message`), and every
 * request/response is logged (Logcat only, never shown to the Owner --
 * see [TAG]) so a future failure is diagnosable instead of a silent
 * "HTTP 429."
 *
 * "Empty responses must be treated as connection failures": the
 * previous version emitted `ChatChunk.Complete("")` whenever nothing
 * could be extracted from the stream, which is exactly what let
 * Settings show "Connected, but the reply was empty" -- a contradiction
 * in terms. Fixed: an empty result after a 200 OK response now emits
 * `ChatChunk.Error`, with the raw response body logged (truncated) so
 * the actual shape Gemini returned is visible next time this runs on a
 * real device, since this sandbox still has no network path to
 * verify the SSE parsing against a live response.
 */
@Singleton
class GeminiChatProvider @Inject constructor(
    private val keyStore: GeminiKeyStore,
) : ChatProvider {
    override val id: String = "gemini"
    override val displayName: String = "Gemini"
    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.GENERAL_CHAT,
        AiCapability.REASONING,
        AiCapability.LONG_CONTEXT,
        AiCapability.VISION,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * "AI Provider Stabilization & Truthfulness Audit" Requirement 4:
     * updated at the end of every real sendMessage() call, whether it
     * came from an actual chat message or Settings' Test Connection
     * (which calls this exact same function) -- both paths converge on
     * one truth here, rather than Settings tracking its own separate
     * "did the test pass" state.
     */
    private val _lastOutcome = MutableStateFlow<ProviderConnectionState.AttemptOutcome?>(null)
    val lastOutcome: StateFlow<ProviderConnectionState.AttemptOutcome?> = _lastOutcome.asStateFlow()

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val config = keyStore.currentConfig()
        if (config == null) {
            emit(ChatChunk.Error("No Gemini API key is configured. Add one in Settings under AI Provider to talk to Gemini."))
            return@flow
        }

        val requestJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray().put(JSONObject().apply { put("text", text) }),
                        )
                    },
                ),
            )
        }
        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:streamGenerateContent" +
            "?alt=sse&key=${config.apiKey}"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        Log.d(TAG, "request start: model=${config.model}")
        val startedAt = System.currentTimeMillis()
        val accumulated = StringBuilder()
        var rawLineCount = 0

        try {
            client.newCall(request).execute().use { response ->
                val latencyMs = System.currentTimeMillis() - startedAt
                Log.d(TAG, "request finish: status=${response.code} latencyMs=$latencyMs")

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    Log.w(TAG, "error response body: ${errorBody.take(500)}")
                    val isRateLimited = response.code == 429 || runCatching { JSONObject(errorBody).getJSONObject("error").optString("status") == "RESOURCE_EXHAUSTED" }.getOrDefault(false)
                    _lastOutcome.value = if (isRateLimited) ProviderConnectionState.AttemptOutcome.RATE_LIMITED else ProviderConnectionState.AttemptOutcome.FAILED
                    emit(ChatChunk.Error(friendlyErrorMessage(response.code, errorBody)))
                    return@use
                }

                val source = response.body?.source()
                if (source == null) {
                    Log.w(TAG, "successful response had no body")
                    emit(ChatChunk.Error("Gemini returned an empty response."))
                    return@use
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    rawLineCount += 1
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    val delta = runCatching {
                        JSONObject(payload)
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .optString("text", "")
                    }.onFailure { Log.w(TAG, "failed to parse SSE payload: ${payload.take(300)}", it) }
                        .getOrDefault("")
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(ChatChunk.Token(accumulated.toString()))
                    }
                }
            }

            if (accumulated.isEmpty()) {
                Log.w(TAG, "empty reply after $rawLineCount data line(s) -- response did not match the expected shape")
                _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
                emit(ChatChunk.Error("Gemini responded, but no reply text could be read from it. This has been logged for investigation."))
            } else {
                Log.d(TAG, "response size: ${accumulated.length} chars")
                emit(ChatChunk.Complete(accumulated.toString()))
                keyStore.recordSuccess()
                _lastOutcome.value = ProviderConnectionState.AttemptOutcome.SUCCEEDED
            }
        } catch (e: Exception) {
            Log.e(TAG, "network error", e)
            _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting Gemini."))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Gemini's real error body shape: {"error": {"code": ..., "message":
     * ..., "status": "RESOURCE_EXHAUSTED" | "INVALID_ARGUMENT" |
     * "PERMISSION_DENIED" | ...}}. `status` is the reliable field for
     * distinguishing rate limiting from other failures -- HTTP 429 from
     * Google's API is consistently RESOURCE_EXHAUSTED, covering both
     * per-minute rate limits and quota exhaustion (Google's API doesn't
     * distinguish those two at the HTTP layer the way OpenAI's does).
     */
    private fun friendlyErrorMessage(httpCode: Int, errorBody: String): String {
        val status = runCatching { JSONObject(errorBody).getJSONObject("error").optString("status") }.getOrNull()
        return when {
            httpCode == 429 || status == "RESOURCE_EXHAUSTED" -> "Gemini is temporarily rate limited or has reached its quota. Please try again in a minute."
            httpCode == 401 || status == "UNAUTHENTICATED" -> "Gemini rejected the API key. Check it under Settings, AI Provider."
            httpCode == 403 || status == "PERMISSION_DENIED" -> "Gemini denied this request -- the API key may not have access to this model."
            status == "INVALID_ARGUMENT" -> "Gemini couldn't process that request -- the model name or request may be invalid."
            else -> "Gemini returned an error (HTTP $httpCode)."
        }
    }

    private companion object {
        const val TAG = "GeminiChatProvider"
    }
}
