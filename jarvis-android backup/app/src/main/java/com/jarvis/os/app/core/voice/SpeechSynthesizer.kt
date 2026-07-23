package com.jarvis.os.app.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 14-16 "Live Speech Synthesis": "JARVIS should be capable of
 * speaking responses aloud... Architecture must allow replacing
 * Android TTS with premium voices later without redesign." Same
 * interface-plus-swappable-implementation shape this codebase already
 * uses for every other pluggable concern (ChatProvider, ToolRepository,
 * ApprovalRepository) -- a future premium-voice provider is a new class
 * implementing [SpeechSynthesizer] plus one @Binds line, not a redesign
 * of anything that calls [speak].
 */
interface SpeechSynthesizer {
    /** True once the underlying engine has finished initializing and can actually speak. */
    val isReady: StateFlow<Boolean>
    /** True for the actual duration of an utterance -- a real callback-driven signal (see AndroidSpeechSynthesizer's UtteranceProgressListener), not a guessed/timed duration, so the avatar's Speaking state reflects real speech, not an approximation of it. */
    val isSpeaking: StateFlow<Boolean>
    fun speak(text: String)
    fun stop()
}

/**
 * Android's built-in TextToSpeech engine -- no premium voice provider
 * exists yet (same "no live AI provider" honesty note this codebase
 * already applies elsewhere; see MockChatProvider/ContextManager). This
 * is a genuinely real, on-device implementation, not a placeholder --
 * it will actually speak using whatever TTS voice is installed on the
 * device.
 *
 * Deliberately app-scoped (one instance for the app's lifetime, never
 * explicitly shut down): TextToSpeech.shutdown() matters for releasing
 * engine resources promptly, but this codebase has no other example of
 * an app-scoped singleton with teardown logic (compare ChatSessionManager,
 * AgentRegistry -- neither disposes anything either), and process death
 * reclaims the engine regardless. A future revision could move this to
 * a lifecycle-aware scope if that turns out to matter in practice.
 */
@Singleton
class AndroidSpeechSynthesizer @Inject constructor(
    @ApplicationContext context: Context,
) : SpeechSynthesizer {

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var utteranceCounter = 0

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        _isReady.value = status == TextToSpeech.SUCCESS
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }
            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }
            @Deprecated("Deprecated in the platform API, still the one guaranteed callback on older OS versions")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
    }

    override fun speak(text: String) {
        if (!_isReady.value || text.isBlank()) return
        utteranceCounter += 1
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-utterance-$utteranceCounter")
    }

    override fun stop() {
        tts.stop()
        _isSpeaking.value = false
    }
}
