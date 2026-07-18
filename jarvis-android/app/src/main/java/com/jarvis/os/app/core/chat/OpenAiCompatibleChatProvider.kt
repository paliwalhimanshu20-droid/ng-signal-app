package com.jarvis.os.app.core.chat

import android.util.Log
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.ApiKeyStore
import com.jarvis.os.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * Sprint 12 "Real AI Conversation": a genuine, real streaming
 * ChatProvider -- this makes an actual HTTP request to a real
 * endpoint, not a simulation. It only actually produces a real AI
 * reply once the Owner has entered a real API key (Settings -> AI
 * Provider) -- this class cannot fabricate one.
 *
 * "OpenAI-compatible" specifically: POST {baseUrl}/chat/completions
 * with `stream: true`, Server-Sent Events response
 * (`data: {...}` lines, `choices[0].delta.content`, terminated by
 * `data: [DONE]`) is the same shape OpenAI itself uses and that a wide
 * range of other vendors' and self-hosted endpoints also implement.
 *
 * "AI Provider Stabilization & Truthfulness Audit": same real bug
 * GeminiChatProvider had, confirmed by the Owner testing the app --
 * `if (!response.isSuccessful)` discarded the error body, so every
 * failure read as a bare HTTP code no matter what OpenAI's own error
 * JSON actually said. Fixed: the body is read and parsed for OpenAI's
 * real error shape (`error.type`, which is the field that actually
 * distinguishes a transient rate limit from an exhausted billing quota
 * -- both return HTTP 429, and the status code alone can't tell them
 * apart). Logging added (Logcat only, see [TAG]). An empty result after
 * a 200 OK now emits `ChatChunk.Error`, not a fabricated `Complete("")`.
 */
@Singleton
class OpenAiCompatibleChatProvider @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val settingsRepository: SettingsRepository,
) : ChatProvider {
    override val id: String = "openai-compatible"
    override val displayName: String = "OpenAI"
    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.GENERAL_CHAT,
        AiCapability.REASONING,
        AiCapability.CODE_GENERATION,
        AiCapability.LONG_CONTEXT,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** See GeminiChatProvider's own docstring for why this exists -- one truth, updated by both chat and Test Connection since they call the same function. */
    private val _lastOutcome = MutableStateFlow<ProviderConnectionState.AttemptOutcome?>(null)
    val lastOutcome: StateFlow<ProviderConnectionState.AttemptOutcome?> = _lastOutcome.asStateFlow()

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val config = apiKeyStore.currentConfig()
        if (config == null || config.apiKey.isBlank()) {
            emit(ChatChunk.Error("No API key is configured. Add one in Settings under AI Provider to enable real AI conversation."))
            return@flow
        }

        val language = settingsRepository.appearance.first().language

        val requestJson = JSONObject().apply {
            put("model", config.model)
            put("stream", true)
            put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", JarvisPersona.systemPrompt(language))
                        },
                    )
                    .put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", text)
                        },
                    ),
            )
        }
        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        Log.d(TAG, "request start: model=${config.model} baseUrl=${config.baseUrl}")
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
                    val type = runCatching { JSONObject(errorBody).getJSONObject("error").optString("type") }.getOrNull()
                    val isRateLimited = response.code == 429 || type == "rate_limit_exceeded" || type == "insufficient_quota"
                    _lastOutcome.value = if (isRateLimited) ProviderConnectionState.AttemptOutcome.RATE_LIMITED else ProviderConnectionState.AttemptOutcome.FAILED
                    emit(ChatChunk.Error(friendlyErrorMessage(response.code, errorBody)))
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    Log.w(TAG, "successful response had no body")
                    emit(ChatChunk.Error("The AI provider returned an empty response."))
                    return@use
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    rawLineCount += 1
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val delta = runCatching {
                        JSONObject(payload)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("delta")
                            .optString("content", "")
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
                emit(ChatChunk.Error("The AI provider responded, but no reply text could be read from it. This has been logged for investigation."))
            } else {
                Log.d(TAG, "response size: ${accumulated.length} chars")
                emit(ChatChunk.Complete(accumulated.toString()))
                apiKeyStore.recordSuccess()
                _lastOutcome.value = ProviderConnectionState.AttemptOutcome.SUCCEEDED
            }
        } catch (e: Exception) {
            Log.e(TAG, "network error", e)
            _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting the AI provider."))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * OpenAI's real error body shape: {"error": {"message": ..., "type":
     * "insufficient_quota" | "rate_limit_exceeded" | "invalid_api_key" |
     * "invalid_request_error" | ...}}. `type` is what actually
     * distinguishes a transient rate limit (retry shortly) from an
     * exhausted billing quota (won't resolve on its own) -- both share
     * HTTP 429, so the status code alone is not enough, exactly the gap
     * this sprint's brief called out by name.
     */
    private fun friendlyErrorMessage(httpCode: Int, errorBody: String): String {
        val type = runCatching { JSONObject(errorBody).getJSONObject("error").optString("type") }.getOrNull()
        return when {
            type == "insufficient_quota" -> "OpenAI quota has been exceeded. Check billing on your OpenAI account."
            httpCode == 429 || type == "rate_limit_exceeded" -> "OpenAI is temporarily rate limited. Please try again in a minute."
            httpCode == 401 || type == "invalid_api_key" -> "OpenAI rejected the API key. Check it under Settings, AI Provider."
            type == "invalid_request_error" -> "OpenAI couldn't process that request -- the model name or request may be invalid."
            else -> "The AI provider returned an error (HTTP $httpCode)."
        }
    }

    private companion object {
        const val TAG = "OpenAiCompatibleChatProvider"
    }
}
