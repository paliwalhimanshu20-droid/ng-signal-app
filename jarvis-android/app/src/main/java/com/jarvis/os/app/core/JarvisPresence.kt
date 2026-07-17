package com.jarvis.os.app.core

import com.jarvis.os.app.designsystem.components.JarvisAvatarState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "JARVIS Experience Transformation" (Phase 1 + Phase 2): "the
 * environment should react together with JARVIS." Rendering the Living
 * Background once, at the app root, behind every screen (see
 * JarvisApp.kt) means it needs a state to react to that isn't tied to
 * whichever single screen happens to be on top -- this is that shared
 * state. HomeViewModel and ChatViewModel push real state in when they
 * have it (Listening/Thinking/Speaking, driven by real
 * SpeechToTextController/SpeechSynthesizer signals, same as before);
 * nothing else pushes, so JARVIS is honestly Idle everywhere else --
 * this class does not fabricate activity, it just gives real activity
 * one place to be seen from regardless of screen.
 *
 * Deliberately a plain @Singleton class, not an interface -- there is
 * no second implementation this would ever be swapped for (compare
 * ChatSessionManager, the same shape for the same reason).
 */
@Singleton
class JarvisPresence @Inject constructor() {
    private val _state = MutableStateFlow(JarvisAvatarState.Idle)
    val state: StateFlow<JarvisAvatarState> = _state.asStateFlow()

    fun update(newState: JarvisAvatarState) {
        _state.value = newState
    }
}
