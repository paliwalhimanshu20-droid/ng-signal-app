package com.jarvis.os.app.core.chat

import android.util.Log
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.AnthropicKeyStore
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
 * "Fix AI Response Parsing -- Critical": not explicitly named in this
 * sprint's brief (only Gemini/OpenAI were), but switched to
 * non-streaming for the same reliability reasoning as both of those --
 * see GeminiChatProvider's own docstring. Non-streaming Claude response
 * shape: `content[0].text` (an array of content blocks at the top
 * level), a genuinely different shape from the streaming
 * `content_block_delta` events this class used before -- re-verified
 * against Anthropic's documented non-streaming response shape
 * specifically.
 */
@Singleton
class AnthropicChatProvider @Inject constructor(
    private val keyStore: AnthropicKeyStore,
    private val settingsRepository: SettingsRepository,
) : ChatProvider {
    override val id: String = "anthropic"
    override val displayName: String = "Claude"
    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.GENERAL_CHAT,
        AiCapability.REASONING,
        AiCapability.LONG_CONTEXT,
        AiCapability.CODE_GENERATION,
    )

    /** Same condition [sendMessage] itself checks before emitting "No Claude API key is configured" -- see ChatProvider.isConfigured's own docstring. */
    override fun isConfigured(): Boolean = keyStore.currentConfig() != null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _lastOutcome = MutableStateFlow<ProviderConnectionState.AttemptOutcome?>(null)
    val lastOutcome: StateFlow<ProviderConnectionState.AttemptOutcome?> = _lastOutcome.asStateFlow()

    override fun sendMessage(sessionId: String, prompt: ChatPrompt): Flow<ChatChunk> = flow {
        val config = keyStore.currentConfig()
        if (config == null) {
            emit(ChatChunk.Error("No Claude API key is configured. Add one in AI Provider Settings to talk to Claude."))
            return@flow
        }

        // "Conversation Replay Bug Fix": same fix as every other real provider -- see
        // GroqChatProvider's docstring on this same change for the full history. Claude's
        // `system` field is top-level (not a role inside `messages`, unlike OpenAI/Groq), so the
        // persona + labeled background block are combined into that one field; the sole entry in
        // `messages` is prompt.userMessage alone, never contextHint-contaminated.
        val appearance = settingsRepository.appearance.first()
        val systemText = buildString {
            append(JarvisPersona.systemPrompt(appearance.language, appearance.personaDisplayName))
            PromptBuilder.backgroundContextBlock(prompt)?.let { append("\n\n").append(it) }
        }

        val requestJson = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 1024)
            put("system", systemText)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt.userMessage)
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

        try {
            val fullText = client.newCall(request).execute().use { response ->
                val latencyMs = System.currentTimeMillis() - startedAt
                Log.d(TAG, "request finish: status=${response.code} latencyMs=$latencyMs")

                val bodyString = response.body?.string().orEmpty()
                Log.d(TAG, "response size: ${bodyString.length} chars")

                if (!response.isSuccessful) {
                    Log.w(TAG, "error response body: ${bodyString.take(500)}")
                    val type = runCatching { JSONObject(bodyString).getJSONObject("error").optString("type") }.getOrNull()
                    val isRateLimited = response.code == 429 || type == "rate_limit_error" || type == "overloaded_error"
                    _lastOutcome.value = if (isRateLimited) ProviderConnectionState.AttemptOutcome.RATE_LIMITED else ProviderConnectionState.AttemptOutcome.FAILED
                    emit(ChatChunk.Error(friendlyErrorMessage(response.code, bodyString)))
                    return@use null
                }

                // Non-streaming shape: top-level "content" array of blocks, each with "text" -- NOT the content_block_delta event shape (that's streaming-only).
                val extracted = runCatching {
                    val contentArray = JSONObject(bodyString).getJSONArray("content")
                    val builder = StringBuilder()
                    for (i in 0 until contentArray.length()) {
                        val block = contentArray.getJSONObject(i)
                        if (block.optString("type") == "text") builder.append(block.optString("text", ""))
                    }
                    builder.toString()
                }.onFailure { Log.e(TAG, "failed to parse response body: ${bodyString.take(800)}", it) }
                    .getOrDefault("")

                if (extracted.isEmpty()) {
                    Log.w(TAG, "parsed successfully but text was empty -- full body: ${bodyString.take(800)}")
                }
                extracted
            } ?: return@flow

            if (fullText.isEmpty()) {
                _lastOutcome.value = ProviderConnectionState.AttemptOutcome.FAILED
                emit(ChatChunk.Error("Claude responded, but no reply text could be read from it. This has been logged for investigation."))
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
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting Claude."))
        }
    }.flowOn(Dispatchers.IO)

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
