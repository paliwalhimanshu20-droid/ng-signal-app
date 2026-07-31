package com.jarvis.os.app.core.chat

import android.util.Log
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.GeminiKeyStore
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
 * "Fix AI Response Parsing -- Critical": switched from streaming
 * (`streamGenerateContent?alt=sse`, manual line-by-line SSE parsing) to
 * non-streaming (`generateContent`, one JSON object parsed once). This
 * is a deliberate reliability trade documented here, not a silent
 * downgrade: manual SSE parsing has several independent ways to fail
 * silently (a line split across two reads, a "data:" prefix that
 * doesn't match exactly, a multi-line JSON payload, buffering behavior
 * this sandbox has no way to test against a live response) -- any one
 * of which produces exactly the "no reply text could be read" symptom
 * that was reported, and I cannot fully rule out which one without a
 * live call. A single non-streaming response has exactly one place to
 * get parsing right instead of many, which is the responsible choice
 * when "make one real conversation work" is the literal, stated bar --
 * not a permanent architectural decision, a fix for right now.
 *
 * The reply still appears to arrive progressively in the UI: once the
 * full text is back, it's revealed word-by-word (same technique
 * MockChatProvider already uses for its own offline reply) rather than
 * flashing the whole message at once -- real text, revealed
 * client-side, not fake streaming from the network.
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
            emit(ChatChunk.Error("No Gemini API key is configured. Add one in Settings under AI Provider to talk to Gemini."))
            return@flow
        }

        val requestJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply { put("text", text) }))
                    },
                ),
            )
        }
        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        // Non-streaming endpoint: generateContent, not streamGenerateContent. No alt=sse.
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"

        val request = Request.Builder()
            .url(url)
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
                    val status = runCatching { JSONObject(bodyString).getJSONObject("error").optString("status") }.getOrNull()
                    val isRateLimited = response.code == 429 || status == "RESOURCE_EXHAUSTED"
                    _lastOutcome.value = if (isRateLimited) ProviderConnectionState.AttemptOutcome.RATE_LIMITED else ProviderConnectionState.AttemptOutcome.FAILED
                    emit(ChatChunk.Error(friendlyErrorMessage(response.code, bodyString)))
                    return@use null
                }

                val extracted = runCatching {
                    JSONObject(bodyString)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .optString("text", "")
                }.onFailure { Log.e(TAG, "failed to parse response body: ${bodyString.take(800)}", it) }
                    .getOrDefault("")

                if (extracted.isEmpty()) {
                    Log.w(TAG, "parsed successfully but text was empty -- full body: ${bodyString.take(800)}")
                }
                extracted
            } ?: return@flow

            if (fullText.isEmpty()) {
                _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
                emit(ChatChunk.Error("Gemini responded, but no reply text could be read from it. This has been logged for investigation."))
                return@flow
            }

            // Reveal word-by-word so the UI still shows real streaming-like behavior.
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
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting Gemini."))
        }
    }.flowOn(Dispatchers.IO)

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
