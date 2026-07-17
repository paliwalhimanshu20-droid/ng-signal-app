package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.testutil.FakeSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PR4: proves capability-based routing picks the highest-overlap bound
 * provider without disturbing manual switchProvider/active state, using
 * the three real providers this PR ships (not hand-rolled test
 * doubles) so this exercises the same object graph Hilt assembles.
 */
class AiRouterTest {

    private fun router() = AiRouter(setOf(MockChatProvider(FakeSettingsRepository()), MockClaudeProvider(), MockGptProvider()))

    @Test
    fun `routeFor with no requirement returns the active provider`() {
        val router = router()
        assertEquals(router.active.id, router.routeFor(emptySet()).id)
    }

    @Test
    fun `routeFor picks the provider with highest capability overlap`() {
        val router = router()
        val result = router.routeFor(setOf(AiCapability.REASONING, AiCapability.LONG_CONTEXT))
        assertEquals("claude", result.id)
    }

    @Test
    fun `routeFor for tool use and vision picks gpt`() {
        val router = router()
        val result = router.routeForAny(AiCapability.TOOL_USE, AiCapability.VISION)
        assertEquals("gpt", result.id)
    }

    @Test
    fun `routeFor does not mutate active provider`() {
        val router = router()
        val before = router.activeProviderId.value
        router.routeFor(setOf(AiCapability.REASONING))
        assertEquals(before, router.activeProviderId.value)
    }

    @Test
    fun `providersFor returns every bound provider declaring a capability`() {
        val router = router()
        val generalChatProviders = router.providersFor(AiCapability.GENERAL_CHAT)
        assertTrue(generalChatProviders.size == 3)
    }

    @Test
    fun `switchProvider still works unchanged from Sprint 8-1`() {
        val router = router()
        assertTrue(router.switchProvider("claude"))
        assertEquals("claude", router.active.id)
        assertTrue(!router.switchProvider("nonexistent"))
        assertEquals("claude", router.active.id)
    }
}
