package com.jarvis.os.app.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 14-16 "Voice Experience": "Large microphone interaction,
 * listening animation, thinking animation... typing remains available
 * but secondary." This is the real, on-device Android SpeechRecognizer
 * -- genuinely new territory for this codebase and, honestly, the
 * single highest-risk file in this delivery (see the integration
 * report). Every other file in this project could be reasoned about
 * from Kotlin language semantics and this codebase's own established
 * patterns; this one depends on exact Android framework callback
 * behavior (RecognitionListener, SpeechRecognizer's main-thread
 * requirement) that no amount of careful reading substitutes for an
 * actual device test.
 */
sealed interface VoiceRecognitionEvent {
    data object Listening : VoiceRecognitionEvent
    data class PartialResult(val text: String) : VoiceRecognitionEvent
    data class FinalResult(val text: String) : VoiceRecognitionEvent
    data class Error(val message: String) : VoiceRecognitionEvent
    data object Done : VoiceRecognitionEvent
}

interface SpeechToTextController {
    val isAvailable: Boolean

    /**
     * Starts one listening session and emits events until the session
     * ends (a final result, an error, or the caller cancelling
     * collection). RECORD_AUDIO must already be granted -- this
     * function does not request it (see HomeScreen's permission-request
     * call site, which is where that belongs, not buried in this
     * controller).
     */
    fun startListening(): Flow<VoiceRecognitionEvent>
    fun stopListening()
}

@Singleton
class AndroidSpeechToTextController @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechToTextController {

    private var activeRecognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening(): Flow<VoiceRecognitionEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(VoiceRecognitionEvent.Error("Speech recognition isn't available on this device."))
            trySend(VoiceRecognitionEvent.Done)
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        activeRecognizer = recognizer

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceRecognitionEvent.Listening)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                trySend(VoiceRecognitionEvent.Error(messageForError(error)))
                trySend(VoiceRecognitionEvent.Done)
                close()
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    trySend(VoiceRecognitionEvent.FinalResult(text))
                }
                trySend(VoiceRecognitionEvent.Done)
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    trySend(VoiceRecognitionEvent.PartialResult(text))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)

        awaitClose {
            recognizer.destroy()
            activeRecognizer = null
        }
    }

    override fun stopListening() {
        activeRecognizer?.stopListening()
    }

    private fun messageForError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during recognition."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "JARVIS is already listening."
        else -> "Voice recognition error."
    }
}
