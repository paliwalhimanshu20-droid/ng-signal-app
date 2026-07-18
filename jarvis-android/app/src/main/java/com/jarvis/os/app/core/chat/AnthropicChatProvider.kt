package com.jarvis.os.app.core.chat

import android.util.Log
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.AnthropicKeyStore
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
 * "JARVIS Goes Live": "AI Provider Settings... Anthropic Claude." A
 * genuine, real streaming client against Anthropic's native Messages
 * API (api.anthropic.com/v1/messages), not a mock and not the
 * OpenAI-compatible provider under a different name.
 *
 * Real streaming: Anthropic's SSE stream carries several event types
 * (message_start, content_block_start, content_block_delta,
 * content_block_stop, message_delta, message_stop) on the same `data:`
 * lines -- only `content_block_delta` events with a `text_delta` carry
 * actual reply text. Every other event type's JSON simply doesn't match
 * the shape being extracted below and is silently skipped.
 *
 * "AI Provider Stabilization & Truthfulness Audit": same real bug the
 * other two providers had -- `if (!response.isSuccessful)` discarded
 * the error body. Fixed: parses Anthropic's real error shape
 * (`error.type`: rate_limit_error/overloaded_error/authentication_error/
 * invalid_request_error), with logging (Logcat only, see [TAG]) and
 * honest handling of an empty result after a 200 OK.
 */
@Singleton
class AnthropicChatProvider @Inject constructor(
    private val keyStore: AnthropicKeyStore,
) : ChatProvider {
    override val id: String = "anthropic"
    override val displayName: String = "Claude"
    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.GENERAL_CHAT,
        AiCapability.REASONING,
        AiCapability.LONG_CONTEXT,
        AiCapability.CODE_GENERATION,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** See GeminiChatProvider's own docstring for why this exists -- one truth, updated by both chat and Test Connection since they call the same function. */
    private val _lastOutcome = MutableStateFlow<ProviderConnectionState.AttemptOutcome?>(null)
    val lastOutcome: StateFlow<ProviderConnectionState.AttemptOutcome?> = _lastOutcome.asStateFlow()

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val config = keyStore.currentConfig()
        if (config == null) {
            emit(ChatChunk.Error("No Claude API key is configured. Add one in AI Provider Settings to talk to Claude."))
            return@flow
        }

        val requestJson = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 1024)
            put("stream", true)
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
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
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
                    val type = runCatching { JSONObject(errorBody).getJSONObject("error").optString("type") }.getOrNull()
                    val isRateLimited = response.code == 429 || type == "rate_limit_error" || type == "overloaded_error"
                    _lastOutcome.value = if (isRateLimited) ProviderConnectionState.AttemptOutcome.RATE_LIMITED else ProviderConnectionState.AttemptOutcome.FAILED
                    emit(ChatChunk.Error(friendlyErrorMessage(response.code, errorBody)))
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    Log.w(TAG, "successful response had no body")
                    emit(ChatChunk.Error("Claude returned an empty response."))
                    return@use
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    rawLineCount += 1
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    val delta = runCatching {
                        val json = JSONObject(payload)
                        if (json.optString("type") != "content_block_delta") return@runCatching ""
                        val deltaObj = json.getJSONObject("delta")
                        if (deltaObj.optString("type") != "text_delta") return@runCatching ""
                        deltaObj.optString("text", "")
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
                emit(ChatChunk.Error("Claude responded, but no reply text could be read from it. This has been logged for investigation."))
            } else {
                Log.d(TAG, "response size: ${accumulated.length} chars")
                emit(ChatChunk.Complete(accumulated.toString()))
                keyStore.recordSuccess()
                _lastOutcome.value = ProviderConnectionState.AttemptOutcome.SUCCEEDED
            }
        } catch (e: Exception) {
            Log.e(TAG, "network error", e)
            _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting Claude."))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Anthropic's real error body shape: {"type": "error", "error":
     * {"type": "rate_limit_error" | "overloaded_error" |
     * "authentication_error" | "invalid_request_error" |
     * "permission_error", "message": ...}}.
     */
    private fun friendlyErrorMessage(httpCode: Int, errorBody: String): String {
        val type = runCatching { JSONObject(errorBody).getJSONObject("error").optString("type") }.getOrNull()
        return when {
            httpCode == 429 || type == "rate_limit_error" -> "Claude is temporarily rate limited. Please try again in a minute."
            type == "overloaded_error" -> "Claude's servers are temporarily overloaded. Please try again shortly."
            httpCode == 401 || type == "authentication_error" -> "Claude rejected the API key. Check it under Settings, AI Provider."
            type == "permission_error" -> "Claude denied this request -- the API key may not have access to this model."
            type == "invalid_request_error" -> "Claude couldn't process that request -- the model name or request may be invalid."
            else -> "Claude returned an error (HTTP $httpCode)."
        }
    }

    private companion object {
        const val TAG = "AnthropicChatProvider"
    }
}
