package com.jarvis.os.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.components.ExecutiveTimeline
import com.jarvis.os.app.designsystem.components.GlassPanel
import com.jarvis.os.app.designsystem.components.JarvisAvatar
import com.jarvis.os.app.designsystem.jarvisHeroStyle
import com.jarvis.os.app.designsystem.jarvisHudLabelStyle
import com.jarvis.os.app.navigation.JarvisDestination
import kotlinx.coroutines.launch

/**
 * Sprint 13 "Home Screen": "the first screen must create the feeling
 * 'JARVIS is already working.'" Four fixed sections, top to bottom --
 * AI Presence (the avatar, at rest but visibly alive), the greeting,
 * Executive Briefing (real Sprint 12 data, never static text -- see
 * HomeViewModel), and a persistent Conversation Entry at the bottom
 * that is the primary way to reach JARVIS from this screen, not a
 * secondary action buried under a card. The recent-activity timeline
 * sits between the briefing and the entry field as supporting evidence
 * ("JARVIS was already working") rather than the headline.
 */
@Composable
fun HomeScreen(navController: NavHostController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun sendAndOpenChat() {
        val text = input.trim()
        if (text.isEmpty()) return
        viewModel.send(text)
        input = ""
        navController.navigate(JarvisDestination.Chat.route)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBrand.Void),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = JarvisSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(JarvisSpacing.xxl))

                JarvisAvatar(state = state.avatarState, size = 132.dp)

                Spacer(modifier = Modifier.height(JarvisSpacing.lg))

                Text(
                    text = state.greeting,
                    style = jarvisHeroStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (state.pendingApprovalCount > 0) {
                    Text(
                        text = if (state.pendingApprovalCount == 1) "One approval needs your attention." else "${state.pendingApprovalCount} approvals need your attention.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisBrand.CoreCyan,
                        modifier = Modifier.padding(top = JarvisSpacing.xs),
                    )
                }

                Spacer(modifier = Modifier.height(JarvisSpacing.xl))

                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "EXECUTIVE BRIEFING",
                            style = jarvisHudLabelStyle(),
                            color = JarvisBrand.CoreCyan,
                        )
                        Spacer(modifier = Modifier.height(JarvisSpacing.sm))
                        if (state.briefingLines.isEmpty()) {
                            Text(
                                "Nothing to report yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.briefingLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = JarvisSpacing.xs),
                                )
                            }
                        }
                    }
                }

                if (state.recentTimeline.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(JarvisSpacing.md))
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(
                                text = "RECENT ACTIVITY",
                                style = jarvisHudLabelStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(JarvisSpacing.md))
                            ExecutiveTimeline(entries = state.recentTimeline)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(JarvisSpacing.lg))
            }

            ConversationEntry(
                input = input,
                onInputChange = { input = it },
                onSend = { scope.launch { sendAndOpenChat() } },
            )
        }
    }
}

/**
 * Sprint 13 "Conversation First" (section 5): the primary way to reach
 * JARVIS from Home, always visible at the bottom of the screen rather
 * than a chat tab the owner has to think to open. Submitting sends the
 * message through HomeViewModel exactly as ChatScreen's own input does
 * (same JarvisCore.sendChatMessage call), then opens Chat so the owner
 * sees the reply -- the entry field itself stays on Home, it is not a
 * duplicate, parallel conversation surface.
 */
@Composable
private fun ConversationEntry(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(JarvisSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask JARVIS anything…") },
            shape = MaterialTheme.shapes.extraLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                focusedBorderColor = JarvisBrand.CoreCyan.copy(alpha = 0.6f),
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        )
        IconButton(
            onClick = onSend,
            colors = IconButtonDefaults.iconButtonColors(containerColor = JarvisBrand.CoreBlue),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send to JARVIS", tint = androidx.compose.ui.graphics.Color.White)
        }
    }
}
