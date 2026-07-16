package com.jarvis.os.app.feature.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.components.GlassPanel
import com.jarvis.os.app.designsystem.components.JarvisAvatar
import com.jarvis.os.app.designsystem.components.LivingBackground
import com.jarvis.os.app.designsystem.jarvisHeroStyle
import com.jarvis.os.app.designsystem.jarvisHudLabelStyle
import com.jarvis.os.app.navigation.JarvisDestination
import kotlinx.coroutines.launch

/**
 * Sprint 14-16 "Living JARVIS Experience": "The Home screen should
 * contain ONLY JARVIS. Everything else belongs elsewhere." Sprint 13's
 * Home had a Recent Activity timeline alongside the briefing -- that's
 * gone from this screen now (Mission Control already has its own copy,
 * see that screen), leaving exactly what this sprint's Home section
 * names: avatar, greeting, Executive Briefing, voice entry,
 * conversation entry. The flat JarvisBrand.Void background from Sprint
 * 13 is replaced by [LivingBackground] -- "nothing static."
 */
@Composable
fun HomeScreen(navController: NavHostController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.startListening() }

    fun onMicTapped() {
        val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            if (state.isListening) viewModel.stopListening() else viewModel.startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun sendAndOpenChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModel.send(trimmed)
        input = ""
        navController.navigate(JarvisDestination.Chat.route)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LivingBackground(
            accentColor = appearance.accentColor.seed,
            motionIntensity = appearance.motionIntensity,
            modifier = Modifier.fillMaxSize(),
            avatarState = state.avatarState,
        )

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

                JarvisAvatar(
                    state = state.avatarState,
                    size = 148.dp,
                    themeColor = appearance.accentColor.seed,
                    modifier = Modifier.let { base ->
                        if (state.avatarState == com.jarvis.os.app.designsystem.components.JarvisAvatarState.Speaking) {
                            base.clickable { viewModel.stopSpeaking() }
                        } else {
                            base
                        }
                    },
                )

                Spacer(modifier = Modifier.height(JarvisSpacing.lg))

                Text(
                    text = state.greeting,
                    style = jarvisHeroStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (state.isListening) {
                    Text(
                        text = state.voiceTranscript.ifBlank { "Listening…" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = appearance.accentColor.seed,
                        modifier = Modifier.padding(top = JarvisSpacing.sm),
                    )
                } else if (state.pendingApprovalCount > 0) {
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

                Spacer(modifier = Modifier.height(JarvisSpacing.lg))
            }

            VoiceAndConversationEntry(
                input = input,
                onInputChange = { input = it },
                onSend = { scope.launch { sendAndOpenChat(input) } },
                isListening = state.isListening,
                accentColor = appearance.accentColor.seed,
                onMicTapped = { onMicTapped() },
            )
        }
    }
}

/**
 * Sprint 14-16 "Voice Experience": "Large microphone interaction...
 * typing remains available but secondary." The mic is the visually
 * dominant control; the text field is present and fully functional
 * (nothing here is voice-only), but smaller and positioned second.
 */
@Composable
private fun VoiceAndConversationEntry(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isListening: Boolean,
    accentColor: Color,
    onMicTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(JarvisSpacing.md)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(
                onClick = onMicTapped,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isListening) accentColor else accentColor.copy(alpha = 0.18f),
                ),
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Talk to JARVIS",
                    tint = if (isListening) Color.White else accentColor,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(JarvisSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Or type to JARVIS…") },
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedBorderColor = accentColor.copy(alpha = 0.6f),
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            )
            IconButton(
                onClick = onSend,
                colors = IconButtonDefaults.iconButtonColors(containerColor = accentColor),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send to JARVIS", tint = Color.White)
            }
        }
    }
}
