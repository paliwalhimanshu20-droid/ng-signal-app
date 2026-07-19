package com.jarvis.os.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.core.CoreEvent
import com.jarvis.os.app.core.voice.SpeechSynthesizer
import com.jarvis.os.app.core.voice.SpeechToTextController
import com.jarvis.os.app.core.voice.VoiceRecognitionEvent
import com.jarvis.os.app.data.model.ChatMessage
import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.model.MessageContentKind
import com.jarvis.os.app.core.chat.markdown.MarkdownText
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sprint 8.1: routes through JarvisCore instead of calling
 * ChatRepository directly, per this sprint's required flow (ChatScreen
 * -> ChatViewModel -> JarvisCore -> ChatRepository -> AiRouter ->
 * active ChatProvider).
 *
 * The typing indicator has two independent signals, each with one
 * job: the init block's collector on core.events is the sole,
 * authoritative source that clears it -- reacting to
 * CoreEvent.ChatResponseReceived is what makes this a genuine
 * dependency on the event bus rather than decoration next to a
 * separate direct-call signal that would make the event redundant. If
 * that collector were ever broken, the indicator would visibly get
 * stuck, which is deliberate: it makes the event chain's correctness
 * observable, not silent. send()'s inner launch is a separate,
 * earlier UX nicety -- clearing the indicator the moment the first
 * reply chunk actually appears in the list, before the whole turn
 * finishes -- and is not relied on for correctness.
 *
 * Sprint 14-16: voice input/output wired in, same
 * SpeechToTextController/SpeechSynthesizer HomeViewModel uses --
 * Speaking/Listening are real states now, not the "not wired in yet"
 * this docstring used to say.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val core: JarvisCore,
    private val speechToText: SpeechToTextController,
    private val speechSynthesizer: SpeechSynthesizer,
    private val settingsRepository: SettingsRepository,
    private val presence: com.jarvis.os.app.core.JarvisPresence,
) : ViewModel() {
    val messages = core.chat.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /** Sprint 15 Executive Integration Audit item 2 "Tool Execution Feedback": non-null while a tool is actually running (set on CoreEvent.ToolStarted, cleared with isTyping on ChatResponseReceived) -- what lets the UI show "Checking your calendar..." instead of a generic "thinking" spinner while GoogleCalendarTool's real network call is in flight. */
    private val _workingOnLabel = MutableStateFlow<String?>(null)
    val workingOnLabel: StateFlow<String?> = _workingOnLabel.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    val voiceInputAvailable: Boolean get() = speechToText.isAvailable

    /**
     * Sprint 13 "Conversation First", extended by Sprint 14-16: the
     * avatar stays visible during chat and reflects real state --
     * Listening while a voice session is active, Thinking while
     * awaiting a reply (isTyping), Speaking driven by
     * SpeechSynthesizer's real callback (see that interface's
     * docstring), Idle otherwise. Priority order matters here (a voice
     * session naturally overlaps with typing briefly): Listening and
     * Speaking are momentary and explicit, so they take priority over
     * the ambient isTyping-derived Thinking state.
     *
     * Sprint 16 "Executive Conversation UI" Phase 8: workingOnLabel
     * non-null (a real CoreEvent.ToolStarted fired, a connector is
     * actually mid-network-call) now maps to the existing Working
     * state instead of the generic Thinking one -- reusing
     * JarvisAvatarState's current enum rather than adding a new
     * Searching/Analyzing case per this sprint's own "use existing
     * avatar capabilities where possible" instruction. Checked before
     * Thinking in priority order: every tool-backed turn is also
     * isTyping=true, so without this ordering Thinking would always
     * win and Working would never actually show.
     */
    val avatarState: StateFlow<com.jarvis.os.app.designsystem.components.JarvisAvatarState> = kotlinx.coroutines.flow.combine(
        isTyping, _isListening, speechSynthesizer.isSpeaking, workingOnLabel,
    ) { typing, listening, speaking, working ->
        when {
            listening -> com.jarvis.os.app.designsystem.components.JarvisAvatarState.Listening
            speaking -> com.jarvis.os.app.designsystem.components.JarvisAvatarState.Speaking
            working != null -> com.jarvis.os.app.designsystem.components.JarvisAvatarState.Working
            typing -> com.jarvis.os.app.designsystem.components.JarvisAvatarState.Thinking
            else -> com.jarvis.os.app.designsystem.components.JarvisAvatarState.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.jarvis.os.app.designsystem.components.JarvisAvatarState.Idle)

    init {
        // "JARVIS Experience Transformation" (Phase 1): mirrors
        // HomeViewModel's own forwarding -- see that class's docstring
        // for why one collector here, not a call at each place that
        // changes the underlying signals avatarState is derived from.
        viewModelScope.launch {
            avatarState.collect { state -> presence.update(state) }
        }

        viewModelScope.launch {
            core.events.collect { event ->
                when {
                    event is CoreEvent.ChatResponseReceived && event.sessionId == core.chat.activeSessionId -> {
                        _isTyping.value = false
                        _workingOnLabel.value = null
                    }
                    event is CoreEvent.ToolStarted -> _workingOnLabel.value = friendlyWorkingLabel(event.toolId, event.toolName)
                }
            }
        }
    }

    /** Sprint 15 Executive Integration Audit item 2: the exact phrasing this audit's own examples asked for ("Checking your calendar...", "Reading your Gmail...", "Searching Drive..."), falling back to a generic-but-still-specific phrase using the tool's own display name for any connector that doesn't have a hand-tuned line yet -- a new connector is never left with no feedback at all, just a less polished one until someone adds a line here. */
    private fun friendlyWorkingLabel(toolId: String, toolName: String): String = when (toolId) {
        "google_calendar" -> "Checking your calendar…"
        "google_gmail" -> "Reading your Gmail…"
        "google_drive" -> "Searching Google Drive…"
        "google_workspace_health" -> "Checking Google Workspace…"
        "github_status" -> "Checking GitHub…"
        "ng_signal_pro_status" -> "Checking NG Signal Pro…"
        "streamlit_status" -> "Checking Streamlit…"
        else -> "Checking $toolName…"
    }

    fun startListening() {
        viewModelScope.launch {
            _isListening.value = true
            speechToText.startListening().collect { event ->
                when (event) {
                    is VoiceRecognitionEvent.FinalResult -> send(event.text)
                    is VoiceRecognitionEvent.Done -> _isListening.value = false
                    else -> Unit
                }
            }
        }
    }

    fun stopListening() {
        speechToText.stopListening()
    }

    /** Sprint 12 "Support interruptions": tapping the avatar while it's speaking stops the TTS output immediately -- see ChatScreen's avatar tap handler. */
    fun stopSpeaking() {
        speechSynthesizer.stop()
    }

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isTyping.value = true

            val baselineCount = messages.value.size + 1
            launch {
                messages.first { it.size > baselineCount }
                _isTyping.value = false
            }

            core.sendChatMessage(text)

            if (settingsRepository.appearance.first().voiceOutputEnabled) {
                val reply = messages.value.lastOrNull { it.author == com.jarvis.os.app.data.model.MessageAuthor.JARVIS }?.content
                reply?.let { speechSynthesizer.speak(it) }
            }
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val workingOnLabel by viewModel.workingOnLabel.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val avatarState by viewModel.avatarState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission is needed for voice input.") }
        }
    }

    fun onMicTapped() {
        if (!viewModel.voiceInputAvailable) {
            scope.launch { snackbarHostState.showSnackbar("Voice input isn't available on this device.") }
            return
        }
        val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            if (isListening) viewModel.stopListening() else viewModel.startListening()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // "JARVIS Experience Transformation" (Phase 1): no longer paints its
    // own flat background -- the Living Background now renders once, at
    // the app root (see JarvisApp.kt), so Chat sits inside the same
    // continuous environment every other screen does.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sprint 13 "Conversation First", extended by Sprint 14-16: a
            // small, persistent avatar above the transcript reflecting
            // real Listening/Thinking/Speaking/Idle state (see
            // ChatViewModel's docstring).
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.jarvis.os.app.designsystem.components.JarvisAvatarEngine(
                    state = avatarState,
                    size = 64.dp,
                    modifier = Modifier
                        .padding(top = JarvisSpacing.md, bottom = JarvisSpacing.sm)
                        .let { base ->
                            if (avatarState == com.jarvis.os.app.designsystem.components.JarvisAvatarState.Speaking) {
                                base.clickable { viewModel.stopSpeaking() }
                            } else {
                                base
                            }
                        },
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(JarvisSpacing.md),
                verticalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
            ) {
                // "What exactly you are doing wrong": this used to be a
                // real, seeded ChatMessage -- which silently poisoned
                // every future message's context sent to a real
                // provider (see MockChatRepository's own docstring for
                // the full mechanism). Pure UI text now, shown only
                // while there's genuinely no conversation yet, and
                // never part of anything a real provider sees.
                if (messages.isEmpty()) {
                    item {
                        Text(
                            "I'm currently operating in offline mode because no AI provider has been connected yet. " +
                                "Once you connect Gemini, OpenAI, or Claude from AI Provider Settings, I'll be ready for live conversations. " +
                                "Until then, I can still help with what's already available here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(JarvisSpacing.md),
                        )
                    }
                }
                items(messages, key = { it.messageId }) { message -> MessageBubble(message) }
                if (isTyping) item { TypingIndicator(label = workingOnLabel ?: "JARVIS is thinking…") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(JarvisSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message JARVIS…") },
                    keyboardActions = KeyboardActions(onSend = {
                        scope.launch { viewModel.send(input); input = "" }
                    }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                )
                // Sprint 14-16: real voice input now -- the Sprint 7
                // placeholder ("voice input will be enabled in a future
                // sprint") is that future sprint.
                IconButton(onClick = { onMicTapped() }) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = if (isListening) "Stop listening" else "Voice input",
                        tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { scope.launch { viewModel.send(input); input = "" } }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Sprint 16 "Executive Conversation UI" Phase 4 "Connected System
 * Indicators" + Phase 5 "Premium Message Presentation" (error
 * differentiation). Reads message.sourceToolIds/toolFailureOccurred --
 * real facts JarvisCore stamped on this exact message when it was
 * built (see ChatMessage's own docstring), never inferred by scanning
 * the LLM's rendered text afterward.
 */
private fun connectedSourceLabel(toolId: String): String = when (toolId) {
    "google_calendar" -> "Google Calendar"
    "google_gmail" -> "Gmail"
    "google_drive" -> "Google Drive"
    "google_workspace_health" -> "Google Workspace"
    "github_status" -> "GitHub"
    "ng_signal_pro_status" -> "NG Signal Pro"
    "streamlit_status" -> "Streamlit"
    else -> toolId
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isOwner = message.author == MessageAuthor.OWNER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isOwner) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isOwner) Alignment.End else Alignment.Start) {
            if (!isOwner && message.sourceToolIds.isNotEmpty()) {
                val labels = message.sourceToolIds.joinToString(" · ") { connectedSourceLabel(it) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 2.dp, start = JarvisSpacing.xs),
                ) {
                    Icon(
                        imageVector = if (message.toolFailureOccurred) Icons.Filled.Warning else Icons.Filled.Link,
                        contentDescription = null,
                        tint = if (message.toolFailureOccurred) JarvisStatusColors.Unhealthy else JarvisStatusColors.Healthy,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        "via $labels",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.toolFailureOccurred) JarvisStatusColors.Unhealthy else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            JarvisCard(modifier = Modifier.padding(vertical = JarvisSpacing.xs)) {
                when (message.kind) {
                    MessageContentKind.CODE_BLOCK -> Text(
                        message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(JarvisSpacing.sm),
                    )
                    MessageContentKind.MARKDOWN -> MarkdownText(message.content)
                    MessageContentKind.TEXT -> Text(message.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator(label: String = "JARVIS is thinking…") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.padding(end = JarvisSpacing.sm).size(14.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
