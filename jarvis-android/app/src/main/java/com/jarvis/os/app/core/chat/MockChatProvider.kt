package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.JarvisLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint-8: the same honest-echo behavior MockChatRepository had in
 * Sprint-7, now expressed through the ChatProvider interface instead
 * of being hardcoded inside the repository — this is a relocation, not
 * new fabricated behavior.
 *
 * Sprint 12 "Real AI Conversation": no longer the only ChatProvider —
 * OpenAiCompatibleChatProvider is a real one now. This one remains
 * bound as JARVIS's offline fallback (see AiRouter — routeFor always
 * has a valid candidate even with no API key configured, and
 * switchProvider lets the Owner pick this one deliberately).
 *
 * "JARVIS Personality & Experience Bible": this is the one place in the
 * app where JARVIS "speaks" without a real model behind her (see
 * JarvisPersona's own docstring for that honesty boundary) -- the reply
 * below is deterministic template text, not generated language, so it
 * can only carry her voice as far as hand-written copy honestly can.
 * It stays warm and in-character rather than reading like a system
 * status message, and follows [JarvisLanguage] the same way the real
 * provider's system prompt does, but it is still fundamentally a fixed
 * string, not JARVIS actually thinking -- that distinction matters and
 * is not hidden from this docstring even though it's invisible to the
 * Owner.
 */
@Singleton
class MockChatProvider @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ChatProvider {
    override val id: String = "mock"
    override val displayName: String = "JARVIS (offline)"

    /** Always-available fallback -- deliberately just GENERAL_CHAT so routeFor never has to treat "no provider matched" as a real failure mode when this one is bound. */
    override val capabilities: Set<AiCapability> = setOf(AiCapability.GENERAL_CHAT)

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val language = settingsRepository.appearance.first().language
        val reply = when (language) {
            JarvisLanguage.Hinglish -> "Maine sun liya: \"$text\". Main abhi **offline mode** mein hoon kyunki koi AI provider connect nahi hua hai. " +
                "AI Provider Settings se Gemini, OpenAI, ya Claude connect kar dijiye, phir hum properly baat kar sakte hain."
            JarvisLanguage.Hindi -> "मैंने सुन लिया: \"$text\"। मैं अभी **ऑफ़लाइन मोड** में हूं क्योंकि कोई AI प्रोवाइडर कनेक्ट नहीं हुआ है। " +
                "AI Provider Settings से Gemini, OpenAI, या Claude कनेक्ट कर दीजिए, फिर हम ठीक से बात कर सकते हैं।"
            JarvisLanguage.English -> "I heard you: \"$text\". I'm currently operating in **offline mode** because no AI provider has been connected yet. " +
                "Once you connect Gemini, OpenAI, or Claude from AI Provider Settings, I'll be ready for live conversations."
        }
        // Word-by-word, not one giant Token: makes the streaming
        // architecture genuinely observable on screen rather than a
        // code path nobody can actually see exercised. The 30ms pace
        // is deliberate pacing for this mock, not simulated network
        // latency — a real provider drives its own pacing.
        val words = reply.split(" ")
        val builder = StringBuilder()
        for ((index, word) in words.withIndex()) {
            if (index > 0) builder.append(" ")
            builder.append(word)
            emit(ChatChunk.Token(builder.toString()))
            delay(30)
        }
        emit(ChatChunk.Complete(reply))
    }
}
