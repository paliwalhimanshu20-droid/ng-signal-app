package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.ChatChunk
import com.jarvis.os.app.core.chat.ChatPrompt
import com.jarvis.os.app.core.chat.ChatProvider
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontScale
import com.jarvis.os.app.designsystem.JarvisLanguage
import com.jarvis.os.app.designsystem.JarvisMotionIntensity
import com.jarvis.os.app.testutil.FakePreferredProviderStore
import com.jarvis.os.app.testutil.FakeSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Phase 3C, Section 7+8+9+12 -- DEVICE_ACTION + Command Authority." Every command below must
 * actually call a real SettingsRepository/AiRouter method -- these tests assert on the resulting
 * state, not just the reply text, since a confirmation message with nothing behind it would be
 * exactly the kind of fake success this codebase explicitly forbids.
 */
class DeviceActionLocalIntentHandlerTest {

    private class FakeProvider(override val id: String, override val displayName: String) : ChatProvider {
        override val capabilities: Set<AiCapability> = setOf(AiCapability.GENERAL_CHAT)
        override fun sendMessage(sessionId: String, prompt: ChatPrompt): Flow<ChatChunk> = flowOf(ChatChunk.Complete("n/a"))
    }

    private fun buildHandler(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        providers: Set<ChatProvider> = setOf(
            FakeProvider("mock", "Offline"), FakeProvider("anthropic", "Claude"),
            FakeProvider("gemini", "Gemini"), FakeProvider("groq", "Groq"), FakeProvider("openai-compatible", "ChatGPT"),
        ),
    ): Triple<DeviceActionLocalIntentHandler, FakeSettingsRepository, AiRouter> {
        val aiRouter = AiRouter(providers, FakePreferredProviderStore())
        return Triple(DeviceActionLocalIntentHandler(settings, aiRouter), settings, aiRouter)
    }

    @Test
    fun `speak hindi actually persists Hindi through SettingsRepository, not just a worded reply`() = runTest {
        val (handler, settings, _) = buildHandler()

        val answer = handler.tryHandle("Speak Hindi")

        assertEquals(LocalIntentOutcome.DEVICE_ACTION, answer!!.outcome)
        assertEquals(JarvisLanguage.Hindi, settings.appearance.first().language)
    }

    @Test
    fun `dark mode actually persists AppearanceMode Dark`() = runTest {
        val (handler, settings, _) = buildHandler()

        handler.tryHandle("Dark mode")

        assertEquals(AppearanceMode.Dark, settings.appearance.first().mode)
    }

    @Test
    fun `increase font actually moves JarvisFontScale up one step from the default`() = runTest {
        val (handler, settings, _) = buildHandler()

        handler.tryHandle("increase font")

        assertEquals(JarvisFontScale.Large, settings.appearance.first().fontScale)
    }

    @Test
    fun `increase font at the maximum setting stays at the maximum rather than throwing`() = runTest {
        val settings = FakeSettingsRepository()
        settings.setFontScale(JarvisFontScale.ExtraLarge)
        val (handler, _, _) = buildHandler(settings)

        val answer = handler.tryHandle("increase font")

        assertEquals(JarvisFontScale.ExtraLarge, settings.appearance.first().fontScale)
        assertTrue(answer!!.response.contains("largest"))
    }

    @Test
    fun `reduce motion actually moves JarvisMotionIntensity down from Standard to Calm`() = runTest {
        val (handler, settings, _) = buildHandler()

        handler.tryHandle("reduce motion")

        assertEquals(JarvisMotionIntensity.Calm, settings.appearance.first().motionIntensity)
    }

    @Test
    fun `disable voice actually persists voiceOutputEnabled false`() = runTest {
        val (handler, settings, _) = buildHandler()

        handler.tryHandle("disable voice")

        assertEquals(false, settings.appearance.first().voiceOutputEnabled)
    }

    @Test
    fun `use claude actually switches the active AiRouter provider`() = runTest {
        val (handler, _, aiRouter) = buildHandler()

        val answer = handler.tryHandle("Use Claude")

        assertEquals("anthropic", aiRouter.activeProviderId.value)
        assertTrue(answer!!.response.contains("Claude"))
    }

    @Test
    fun `use local mode switches to the offline mock provider`() = runTest {
        val (handler, _, aiRouter) = buildHandler()
        aiRouter.switchProvider("anthropic")

        handler.tryHandle("use local mode")

        assertEquals("mock", aiRouter.activeProviderId.value)
    }

    @Test
    fun `an unrecognized provider name is reported honestly, never silently ignored`() = runTest {
        // This AiRouter only has "mock" bound -- "use openai" maps to "openai-compatible", which isn't.
        val (handler, _, aiRouter) = buildHandler(providers = setOf(FakeProvider("mock", "Offline")))

        val answer = handler.tryHandle("use openai")

        assertEquals("mock", aiRouter.activeProviderId.value) // unchanged
        assertTrue(answer!!.response.contains("openai-compatible"))
    }

    @Test
    fun `an ordinary question never matches this handler`() = runTest {
        val (handler, _, _) = buildHandler()

        assertNull(handler.tryHandle("What's the price of natural gas?"))
    }
}
