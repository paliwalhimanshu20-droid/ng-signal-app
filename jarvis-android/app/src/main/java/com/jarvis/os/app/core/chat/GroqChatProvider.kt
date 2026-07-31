package com.jarvis.os.app.core.chat

import android.util.Log
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.GroqKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * "Add Groq as a fourth AI provider": a real, separate adapter for
 * Groq's real API (api.groq.com) -- the Owner explicitly asked for a
 * genuinely capable provider with no billing requirement, after OpenAI
 * turned out to need one and Gemini's free tier hit a rate limit.
 *
 * Groq's API is OpenAI-compatible in wire format (same
 * `/chat/completions` shape, same `choices[0].message.content` for a
 * non-streaming response), which is exactly why this is a real,
 * low-risk implementation to add: it reuses a request/response shape
 * already proven correct in OpenAiCompatibleChatProvider, pointed at a
 * different host and a different default model (Groq hosts open-weight
 * models like Llama, not GPT).
 *
 * Non-streaming from the start, not streaming-then-fixed-later --
 * learned directly from the Gemini/OpenAI debugging cycle earlier in
 * this project, where manual SSE parsing had no way to be verified
 * without a live call and turned into exactly the kind of silent
 * failure this avoids by construction.
 */
@Singleton
class GroqChatProvider @Inject constructor(
    private val keyStore: GroqKeyStore,
) : ChatProvider {
    override val id: String = "groq"
    override val displayName: String = "Groq"
    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.GENERAL_CHAT,
        AiCapability.REASONING,
        AiCapability.CODE_GENERATION,
    )

    /** Same condition [sendMessage] itself checks before emitting a "no key configured" error -- see ChatProvider.isConfigured's own docstring. */
    override fun isConfigured(): Boolean = keyStore.currentConfig() != null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _lastOutcome = MutableStateFlow<ProviderConnectionState.AttemptOutcome?>(null)
    val lastOutcome: StateFlow<ProviderConnectionState.AttemptOutcome?> = _lastOutcome.asStateFlow()

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val config = keyStore.currentConfig()
        if (config == null) {
            emit(ChatChunk.Error("No Groq API key is configured. Add one in Settings under AI Provider to talk to Groq."))
            return@flow
        }

        val requestJson = JSONObject().apply {
            put("model", config.model)
            put("stream", false)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    },
                ),
            )
        }
        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        Log.d(TAG, "request start: model=${config.model}")
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
                    val isRateLimited = response.code == 429 || type == "rate_limit_exceeded"
                    _lastOutcome.value = if (isRateLimited) ProviderConnectionState.AttemptOutcome.RATE_LIMITED else ProviderConnectionState.AttemptOutcome.FAILED
                    emit(ChatChunk.Error(friendlyErrorMessage(response.code, bodyString)))
                    return@use null
                }

                // Same non-streaming shape as OpenAI's own API -- choices[0].message.content -- since Groq's API is OpenAI-compatible.
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
                emit(ChatChunk.Error("Groq responded, but no reply text could be read from it. This has been logged for investigation."))
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
            keyStore.recordSuccess()
            _lastOutcome.value = ProviderConnectionState.AttemptOutcome.SUCCEEDED
        } catch (e: Exception) {
            Log.e(TAG, "network error", e)
            _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting Groq."))
        }
    }.flowOn(Dispatchers.IO)

    private fun friendlyErrorMessage(httpCode: Int, errorBody: String): String {
        val type = runCatching { JSONObject(errorBody).getJSONObject("error").optString("type") }.getOrNull()
        return when {
            httpCode == 429 || type == "rate_limit_exceeded" -> "Groq is temporarily rate limited. Please try again in a minute."
            httpCode == 401 -> "Groq rejected the API key. Check it under Settings, AI Provider."
            httpCode == 400 -> "Groq couldn't process that request -- the model name or request may be invalid."
            else -> "Groq returned an error (HTTP $httpCode)."
        }
    }

    private companion object {
        const val TAG = "GroqChatProvider"
    }
}
