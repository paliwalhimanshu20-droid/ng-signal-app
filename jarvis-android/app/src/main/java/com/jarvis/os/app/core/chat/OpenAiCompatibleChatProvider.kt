package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.ApiKeyStore
import com.jarvis.os.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
 * Provider) -- this class cannot fabricate one, and this codebase's
 * development sandbox has no network path to any real AI provider's
 * API to test a live call end to end (only github.com, pypi.org,
 * npmjs.com, and crates.io are reachable from where this was written
 * -- see this sprint's integration report for exactly what could and
 * couldn't be verified).
 *
 * "OpenAI-compatible" specifically: POST {baseUrl}/chat/completions
 * with `stream: true`, Server-Sent Events response
 * (`data: {...}` lines, `choices[0].delta.content`, terminated by
 * `data: [DONE]`) is the same shape OpenAI itself uses and that a wide
 * range of other vendors' and self-hosted endpoints also implement --
 * one real implementation covers more than one real provider depending
 * on what base URL/model the Owner configures, not five near-duplicate
 * classes.
 *
 * SSE is parsed manually over the response body's BufferedSource
 * (line-by-line, "data: " prefix), not via the separate okhttp-sse
 * artifact -- one fewer library dependency for a wire format simple
 * enough to parse directly and reliably.
 *
 * "JARVIS Personality & Experience Bible": every request now leads with
 * a system-role message built from JarvisPersona.systemPrompt -- see
 * that object's own docstring for the full reasoning. This is the one
 * place in the whole app where the Bible actually reaches a real
 * language model, which is also the only place it fully CAN reach one;
 * everywhere else in this app that speaks in JARVIS's voice is a
 * deterministic template approximating it, not the real thing.
 */
@Singleton
class OpenAiCompatibleChatProvider @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val settingsRepository: SettingsRepository,
) : ChatProvider {
    override val id: String = "openai-compatible"
    override val displayName: String = "OpenAI-compatible"
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

        val accumulated = StringBuilder()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ChatChunk.Error("The AI provider returned an error (HTTP ${response.code})."))
                    return@use
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(ChatChunk.Error("The AI provider returned an empty response."))
                    return@use
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val delta = runCatching {
                        JSONObject(payload)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("delta")
                            .optString("content", "")
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(ChatChunk.Token(accumulated.toString()))
                    }
                }
            }
            emit(ChatChunk.Complete(accumulated.toString()))
        } catch (e: Exception) {
            emit(ChatChunk.Error(e.message ?: "A network error occurred while contacting the AI provider."))
        }
    }.flowOn(Dispatchers.IO)
}
