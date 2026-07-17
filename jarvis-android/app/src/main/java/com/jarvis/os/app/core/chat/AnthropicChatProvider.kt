package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.AnthropicKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
 * OpenAI-compatible provider under a different name -- same "separate
 * provider adapter" reasoning GeminiChatProvider's own docstring
 * already established for this codebase.
 *
 * Real streaming: Anthropic's SSE stream carries several event types
 * (message_start, content_block_start, content_block_delta,
 * content_block_stop, message_delta, message_stop) on the same `data:`
 * lines -- only `content_block_delta` events with a `text_delta` carry
 * actual reply text. Every other event type's JSON simply doesn't match
 * the shape being extracted below and is silently skipped via the same
 * `runCatching { }.getOrDefault("")` defensive parsing already proven
 * in OpenAiCompatibleChatProvider/GeminiChatProvider, not a special
 * case written just for this class.
 *
 * Same honesty boundary as every other real provider here: only
 * produces a real reply once the Owner has entered a real Anthropic API
 * key, and this sandbox has no network path to Anthropic's API to test
 * a live call end to end.
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

        val accumulated = StringBuilder()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ChatChunk.Error("Claude returned an error (HTTP ${response.code})."))
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(ChatChunk.Error("Claude returned an empty response."))
                    return@use
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    val delta = runCatching {
                        val json = JSONObject(payload)
                        if (json.optString("type") != "content_block_delta") return@runCatching ""
                        val deltaObj = json.getJSONObject("delta")
                        if (deltaObj.optString("type") != "text_delta") return@runCatching ""
                        deltaObj.optString("text", "")
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(ChatChunk.Token(accumulated.toString()))
                    }
                }
            }
            emit(ChatChunk.Complete(accumulated.toString()))
            if (accumulated.isNotEmpty()) keyStore.recordSuccess()
        } catch (e: Exception) {
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting Claude."))
        }
    }.flowOn(Dispatchers.IO)
}
