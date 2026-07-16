package com.jarvis.os.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.core.voice.SpeechSynthesizer
import com.jarvis.os.app.core.voice.SpeechToTextController
import com.jarvis.os.app.core.voice.VoiceRecognitionEvent
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.settings.AppearanceSettings
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.components.JarvisAvatarState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "Good day.",
    val briefingLines: List<String> = emptyList(),
    val avatarState: JarvisAvatarState = JarvisAvatarState.Idle,
    val pendingApprovalCount: Int = 0,
    val voiceTranscript: String = "",
    val isListening: Boolean = false,
)

/**
 * Sprint 14-16 "JARVIS Comes Alive" (Phase 1 + Phase 2). Extends
 * Sprint 13's JarvisCore-injecting HomeViewModel with the Theme Engine
 * (reads AppearanceSettings for accentColor/motionIntensity, same
 * SettingsRepository every other themed screen already reads) and the
 * Voice Experience (SpeechToTextController for input,
 * SpeechSynthesizer for output) -- both real, on-device implementations
 * (see those interfaces' own docstrings for exactly what's genuinely
 * live vs. still a placeholder).
 *
 * avatarState is now internally driven (Idle/Listening/Thinking/Speaking),
 * not fixed at Idle the way Sprint 13's version was -- Listening while
 * a voice session is active, Thinking while sendChatMessage is in
 * flight, Speaking driven by SpeechSynthesizer.isSpeaking's real
 * callback (not a guessed duration -- see that interface's docstring),
 * Idle otherwise.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val core: JarvisCore,
    private val speechToText: SpeechToTextController,
    private val speechSynthesizer: SpeechSynthesizer,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> = settingsRepository.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    /** Whether this device actually has a speech recognition service -- HomeScreen uses this to decide whether to show the mic button as usable or explain honestly that voice input isn't available here (Sprint 14-16's own "do not invent fake intelligence" rule, applied to a missing platform capability instead of missing data). */
    val voiceInputAvailable: Boolean get() = speechToText.isAvailable

    private val _avatarState = MutableStateFlow(JarvisAvatarState.Idle)
    private val _voiceTranscript = MutableStateFlow("")
    private val _isListening = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        core.approvals.items,
        _avatarState,
        _voiceTranscript,
        _isListening,
    ) { approvals, avatarState, transcript, listening ->
        HomeUiState(
            greeting = greetingForNow(),
            briefingLines = core.briefingEngine.generateMorningBriefing().lines,
            avatarState = avatarState,
            pendingApprovalCount = approvals.count { it.outcome == ApprovalOutcome.PENDING },
            voiceTranscript = transcript,
            isListening = listening,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private var listeningJob: Job? = null

    init {
        // Speaking state driven by SpeechSynthesizer's real callback, not
        // a guessed duration -- see that interface's docstring.
        viewModelScope.launch {
            speechSynthesizer.isSpeaking.collect { speaking ->
                if (speaking) {
                    _avatarState.value = JarvisAvatarState.Speaking
                } else if (_avatarState.value == JarvisAvatarState.Speaking) {
                    _avatarState.value = JarvisAvatarState.Idle
                }
            }
        }
    }

    /** Starts one voice session -- caller (HomeScreen) is responsible for having RECORD_AUDIO already granted; this does not request it (see that screen's permission-request call site). */
    fun startListening() {
        if (listeningJob?.isActive == true) return
        listeningJob = viewModelScope.launch {
            _isListening.value = true
            speechToText.startListening().collect { event ->
                when (event) {
                    is VoiceRecognitionEvent.Listening -> _avatarState.value = JarvisAvatarState.Listening
                    is VoiceRecognitionEvent.PartialResult -> _voiceTranscript.value = event.text
                    is VoiceRecognitionEvent.FinalResult -> {
                        _voiceTranscript.value = event.text
                        send(event.text)
                    }
                    is VoiceRecognitionEvent.Error -> _voiceTranscript.value = ""
                    is VoiceRecognitionEvent.Done -> {
                        _isListening.value = false
                        if (_avatarState.value == JarvisAvatarState.Listening) _avatarState.value = JarvisAvatarState.Idle
                    }
                }
            }
        }
    }

    fun stopListening() {
        speechToText.stopListening()
    }

    /** Home's conversation entry (typed or transcribed voice) -- same JarvisCore.sendChatMessage every other entry point in this app uses. */
    fun send(text: String) {
        if (text.isBlank()) return
        _voiceTranscript.value = ""
        viewModelScope.launch {
            _avatarState.value = JarvisAvatarState.Thinking
            core.sendChatMessage(text)
            if (_avatarState.value == JarvisAvatarState.Thinking) _avatarState.value = JarvisAvatarState.Idle

            if (appearance.value.voiceOutputEnabled) {
                val reply = core.chat.messages.value.lastOrNull { it.author == MessageAuthor.JARVIS }?.content
                reply?.let { speechSynthesizer.speak(it) }
            }
        }
    }
}

private fun greetingForNow(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good Morning."
        hour < 17 -> "Good Afternoon."
        else -> "Good Evening."
    }
}
