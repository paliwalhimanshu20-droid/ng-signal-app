package com.jarvis.os.app.core.chat

import android.util.Log
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.ApiKeyStore
import com.jarvis.os.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * "Fix AI Response Parsing -- Critical": switched from streaming
 * (`stream: true`, manual SSE line parsing, `choices[0].delta.content`)
 * to non-streaming (`stream: false` / omitted, one JSON object parsed
 * once, `choices[0].message.content` -- note this is a REAL, different
 * field path from the streaming shape, not the same path reused; this
 * was re-verified against OpenAI's documented non-streaming response
 * shape specifically, not assumed to match the streaming one). See
 * GeminiChatProvider's own docstring for the full reasoning on why
 * streaming was dropped for now rather than debugged further blind.
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
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _lastOutcome = MutableStateFlow<ProviderConnectionState.AttemptOutcome?>(null)
    val lastOutcome: StateFlow<ProviderConnectionState.AttemptOutcome?> = _lastOutcome.asStateFlow()

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val config = apiKeyStore.currentConfig()
        if (config == null || config.apiKey.isBlank()) {
            emit(ChatChunk.Error("No API key is configured. Add one in Settings under AI Provider to enable real AI conversation."))
            return@flow
        }

        val appearance = settingsRepository.appearance.first()
        val language = appearance.language

        val requestJson = JSONObject().apply {
            put("model", config.model)
            put("stream", false)
            put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", JarvisPersona.systemPrompt(language, appearance.personaDisplayName))
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

        try {
            val fullText = client.newCall(request).execute().use { response ->
                val latencyMs = System.currentTimeMillis() - startedAt
                Log.d(TAG, "request finish: status=${response.code} latencyMs=$latencyMs")

                val bodyString = response.body?.string().orEmpty()
                Log.d(TAG, "response size: ${bodyString.length} chars")

                if (!response.isSuccessful) {
                    Log.w(TAG, "error response body: ${bodyString.take(500)}")
                    val type = runCatching { JSONObject(bodyString).getJSONObject("error").optString("type") }.getOrNull()
                    val isRateLimited = response.code == 429 || type == "rate_limit_exceeded" || type == "insufficient_quota"
                    _lastOutcome.value = if (isRateLimited) ProviderConnectionState.AttemptOutcome.RATE_LIMITED else ProviderConnectionState.AttemptOutcome.FAILED
                    emit(ChatChunk.Error(friendlyErrorMessage(response.code, bodyString)))
                    return@use null
                }

                // Non-streaming shape: choices[0].message.content -- NOT choices[0].delta.content (that's the streaming-only shape).
                val extracted = runCatching {
                    JSONObject(bodyString)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "")
                }.onFailure { Log.e(TAG, "failed to parse response body: ${bodyString.take(800)}", it) }
                    .getOrDefault("")

                if (extracted.isEmpty()) {
                    Log.w(TAG, "parsed successfully but text was empty -- full body: ${bodyString.take(800)}")
                }
                extracted
            } ?: return@flow

            if (fullText.isEmpty()) {
                _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
                emit(ChatChunk.Error("The AI provider responded, but no reply text could be read from it. This has been logged for investigation."))
                return@flow
            }

            val words = fullText.split(" ")
            val builder = StringBuilder()
            for ((index, word) in words.withIndex()) {
                if (index > 0) builder.append(" ")
                builder.append(word)
                emit(ChatChunk.Token(builder.toString()))
                delay(20)
            }
            emit(ChatChunk.Complete(fullText))
            apiKeyStore.recordSuccess()
            _lastOutcome.value = ProviderConnectionState.AttemptOutcome.SUCCEEDED
        } catch (e: Exception) {
            Log.e(TAG, "network error", e)
            _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting the AI provider."))
        }
    }.flowOn(Dispatchers.IO)

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
