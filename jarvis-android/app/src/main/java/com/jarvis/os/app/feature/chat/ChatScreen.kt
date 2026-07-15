package com.jarvis.os.app.feature.chat

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
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
import com.jarvis.os.app.data.model.ChatMessage
import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.model.MessageContentKind
import com.jarvis.os.app.core.chat.markdown.MarkdownText
import com.jarvis.os.app.designsystem.JarvisSpacing
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
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val core: JarvisCore,
) : ViewModel() {
    val messages = core.chat.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /**
     * Sprint 13 "Conversation First": the avatar stays visible during
     * chat and reflects real state, not a decorative loop -- Thinking
     * while awaiting a reply (the same isTyping signal already driving
     * the text indicator below), Idle otherwise. Deliberately not
     * Speaking/Listening here: those imply actual audio output/input,
     * and this app has neither wired in yet (see ChatScreen's own
     * voice-input note) -- claiming them visually would be exactly the
     * "invented intelligence" this sprint's experience rules forbid,
     * applied to the avatar instead of briefing text.
     */
    val avatarState: StateFlow<com.jarvis.os.app.designsystem.components.JarvisAvatarState> = isTyping
        .map { typing -> if (typing) com.jarvis.os.app.designsystem.components.JarvisAvatarState.Thinking else com.jarvis.os.app.designsystem.components.JarvisAvatarState.Idle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.jarvis.os.app.designsystem.components.JarvisAvatarState.Idle)

    init {
        viewModelScope.launch {
            core.events.collect { event ->
                if (event is CoreEvent.ChatResponseReceived && event.sessionId == core.chat.activeSessionId) {
                    _isTyping.value = false
                }
            }
        }
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
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val avatarState by viewModel.avatarState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(modifier = Modifier.fillMaxSize().background(com.jarvis.os.app.designsystem.JarvisBrand.Void)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sprint 13 "Conversation First": a small, persistent avatar
            // above the transcript -- the same JarvisAvatar used on Home,
            // reflecting the same real Thinking/Idle state ChatViewModel
            // already tracks (see its docstring for why not Speaking/Listening).
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.jarvis.os.app.designsystem.components.JarvisAvatar(
                    state = avatarState,
                    size = 64.dp,
                    modifier = Modifier.padding(top = JarvisSpacing.md, bottom = JarvisSpacing.sm),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(JarvisSpacing.md),
                verticalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
            ) {
                items(messages, key = { it.messageId }) { message -> MessageBubble(message) }
                if (isTyping) item { TypingIndicator() }
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
                // Voice interaction UI only, per Sprint-7 scope — no
                // speech-to-text or wake-word wiring exists behind this
                // button yet (wake-word is explicitly deferred). The tap
                // itself must still do something observable rather than
                // nothing (Sprint-7.1 UX polish) — it surfaces an honest
                // status message instead of pretending to listen.
                IconButton(onClick = {
                    scope.launch { snackbarHostState.showSnackbar("Voice input will be enabled in a future sprint.") }
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice input (not yet available)")
                }
                IconButton(onClick = { scope.launch { viewModel.send(input); input = "" } }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isOwner = message.author == MessageAuthor.OWNER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isOwner) Arrangement.End else Arrangement.Start) {
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

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.padding(end = JarvisSpacing.sm).size(14.dp), strokeWidth = 2.dp)
        Text("JARVIS is thinking…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
