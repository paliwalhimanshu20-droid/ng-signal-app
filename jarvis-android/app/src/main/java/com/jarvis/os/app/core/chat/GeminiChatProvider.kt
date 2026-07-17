package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.GeminiKeyStore
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
 * "Universal Connection Ecosystem -- Phase 1": "Implement Gemini as an
 * additional AI Provider through the existing AI Coordinator...
 * Separate provider adapter." A genuinely distinct implementation from
 * OpenAiCompatibleChatProvider -- Gemini's real native REST API
 * (generativelanguage.googleapis.com), not that provider's base URL
 * pointed at Google's OpenAI-compatibility layer. Both would have
 * worked; a separate adapter is what was explicitly asked for, and it
 * keeps Gemini's own request/response shape (contents/parts, not
 * messages/role) visible in this codebase rather than hidden behind a
 * generic client that happens to also work for it.
 *
 * Real streaming: `alt=sse` on the streamGenerateContent endpoint is a
 * real, documented Gemini API query parameter that returns Server-Sent
 * Events, parsed manually the same way OpenAiCompatibleChatProvider
 * parses OpenAI's SSE stream -- same technique, different response
 * shape (`candidates[0].content.parts[0].text` here, not
 * `choices[0].delta.content`).
 *
 * Same honesty boundary as every other real provider in this codebase:
 * this only actually produces a real reply once the Owner has entered
 * a real Gemini API key (Settings -> AI Provider), and this sandbox
 * has no network path to Google's API to test a live call end to end.
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

        val accumulated = StringBuilder()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ChatChunk.Error("Gemini returned an error (HTTP ${response.code})."))
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(ChatChunk.Error("Gemini returned an empty response."))
                    return@use
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
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
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(ChatChunk.Token(accumulated.toString()))
                    }
                }
            }
            emit(ChatChunk.Complete(accumulated.toString()))
        } catch (e: Exception) {
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting Gemini."))
        }
    }.flowOn(Dispatchers.IO)
}
