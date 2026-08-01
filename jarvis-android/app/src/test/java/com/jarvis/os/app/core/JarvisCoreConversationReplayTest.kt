package com.jarvis.os.app.core

import com.jarvis.os.app.core.agents.DefaultMultiAiCoordinator
import com.jarvis.os.app.core.agents.MockAgentRegistry
import com.jarvis.os.app.core.agents.WatchTowerOrchestrator
import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.ChatSessionManager
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.core.intelligence.ContextManager
import com.jarvis.os.app.core.intelligence.ExecutiveBriefingEngine
import com.jarvis.os.app.core.intelligence.JarvisDecisionEngine
import com.jarvis.os.app.core.intelligence.KeywordIntentRouter
import com.jarvis.os.app.core.intelligence.localintent.ConversationLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.DefaultLocalIntentRouter
import com.jarvis.os.app.core.intelligence.localintent.GreetingLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.HelpLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.KnowledgeBaseLocalIntentHandler
import com.jarvis.os.app.core.intelligence.localintent.LocalIntentHandler
import com.jarvis.os.app.core.memory.ConversationMemoryImpl
import com.jarvis.os.app.core.memory.PersonalMemoryImpl
import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockAuditRepository
import com.jarvis.os.app.data.repository.MockChatRepository
import com.jarvis.os.app.data.repository.MockConnectionRepository
import com.jarvis.os.app.data.repository.MockMemoryRepository
import com.jarvis.os.app.data.repository.MockNotificationRepository
import com.jarvis.os.app.data.repository.MockProjectRepository
import com.jarvis.os.app.data.repository.MockToolRepository
import com.jarvis.os.app.testutil.FakeGitHubStatusProvider
import com.jarvis.os.app.testutil.FakeNgSignalProStatusProvider
import com.jarvis.os.app.testutil.FakePreferredProviderStore
import com.jarvis.os.app.testutil.FakeSettingsRepository
import com.jarvis.os.app.testutil.FakeTradingIntelligenceOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Conversation Replay Bug Fix": end-to-end regression coverage through the real object graph
 * (same construction pattern as [JarvisCoreApprovalTest]/[JarvisCoreNotificationTest]) -- proves
 * Requirement 9's scenarios all actually hold once wired together, not just in each unit's own
 * isolated test. Requirement 8's six categories are covered: Greeting/Help are exercised directly;
 * Database and Natural Gas are covered in depth by [com.jarvis.os.app.core.intelligence.localintent.TidbLocalIntentHandlerTest];
 * Conversation Summary by [com.jarvis.os.app.core.intelligence.localintent.ConversationLocalIntentHandlerTest];
 * "Unknown question" is exercised here via the real AI-bound fallback path (MockChatProvider,
 * offline) to prove that path itself is clean of any history-replay artifact now that the router
 * finds no local match.
 *
 * Deliberately does NOT wire the real TidbLocalIntentHandler here (it needs a full TIDB
 * repository graph -- see TidbLocalIntentHandlerTest for that in isolation); this file's LAYER 1
 * handler set is Greeting/Help/Conversation/KnowledgeBase, which is enough to prove the specific
 * regression this fix targets: an ordinary AI-bound question must never echo back an earlier
 * question's content, and MUST NOT contain "we recently touched on" (or any equivalent phrase),
 * regardless of how much real conversation history already exists in this session.
 */
class JarvisCoreConversationReplayTest {

    private fun buildCore(scope: CoroutineScope): JarvisCore {
        val settingsRepository = FakeSettingsRepository()
        val chatSessionManager = ChatSessionManager()
        val mockChatProvider = MockChatProvider(settingsRepository)
        val aiRouter = AiRouter(setOf(mockChatProvider), FakePreferredProviderStore())
        val approvalsRepo = MockApprovalRepository()
        val memoryRepo = MockMemoryRepository()
        val projectsRepo = MockProjectRepository()
        val chatRepo = MockChatRepository(aiRouter, chatSessionManager)
        val toolsRepo = MockToolRepository(emptySet(), approvalsRepo)
        val agentRegistry = MockAgentRegistry(emptySet())
        val multiAiCoordinator = DefaultMultiAiCoordinator(agentRegistry, approvalsRepo)
        val connectionsRepo = MockConnectionRepository()
        val notificationsRepo = MockNotificationRepository(scope)
        val auditRepo = MockAuditRepository()

        val handlers: Set<LocalIntentHandler> = setOf(
            GreetingLocalIntentHandler(),
            HelpLocalIntentHandler(aiRouter),
            ConversationLocalIntentHandler(chatRepo),
            KnowledgeBaseLocalIntentHandler(),
        )

        return JarvisCore(
            connections = connectionsRepo,
            approvals = approvalsRepo,
            memory = memoryRepo,
            projects = projectsRepo,
            chat = chatRepo,
            notifications = notificationsRepo,
            tools = toolsRepo,
            audit = auditRepo,
            contextManager = ContextManager(ConversationMemoryImpl(memoryRepo), PersonalMemoryImpl(memoryRepo), chatRepo, projectsRepo),
            decisionEngine = JarvisDecisionEngine(toolsRepo, agentRegistry),
            intentRouter = KeywordIntentRouter(toolsRepo),
            tradingIntelligenceOrchestrator = FakeTradingIntelligenceOrchestrator(),
            watchTower = WatchTowerOrchestrator(multiAiCoordinator, approvalsRepo),
            briefingEngine = ExecutiveBriefingEngine(
                projectsRepo, approvalsRepo, notificationsRepo, connectionsRepo, memoryRepo, agentRegistry,
                FakeNgSignalProStatusProvider(), settingsRepository, FakeGitHubStatusProvider(),
            ),
            localIntentRouter = DefaultLocalIntentRouter(handlers),
            appScope = scope,
        )
    }

    private fun lastJarvisReply(core: JarvisCore) =
        core.chat.messages.value.last { it.author == MessageAuthor.JARVIS }.content

    @Test
    fun `a greeting is answered locally with zero AI involvement`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)

        core.sendChatMessage("Hey")

        val reply = lastJarvisReply(core)
        assertTrue(reply.contains("JARVIS is online"))
        assertFalse("a local reply must never mention recalled conversation", reply.lowercase().contains("we recently touched on"))
    }

    @Test
    fun `an unrelated follow-up question never replays an earlier question`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)

        // First, real conversation history is created -- this is the exact scenario that used to
        // trigger the bug: a later, unrelated question receiving a reply contaminated by this one.
        core.sendChatMessage("What do you have of natural gas?")
        core.sendChatMessage("What's the capital of France?")

        val reply = lastJarvisReply(core)
        assertFalse(
            "the reply to an unrelated question must never lead with recalled history",
            reply.lowercase().contains("we recently touched on"),
        )
        assertFalse(
            "the reply must never quote back the EARLIER, unrelated question's content",
            reply.lowercase().contains("natural gas"),
        )
        assertTrue(
            "the reply must actually address the current question",
            reply.contains("What's the capital of France?"),
        )
    }

    @Test
    fun `an explicit recap request after real conversation answers locally and honestly`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)
        core.sendChatMessage("Hey")

        core.sendChatMessage("What did we discuss so far?")

        val reply = lastJarvisReply(core)
        assertTrue("an explicit recap request should reference the real prior turn", reply.contains("Hey") || reply.contains("hey", ignoreCase = true))
    }

    @Test
    fun `an unknown question falls through cleanly to the AI-bound path with no history artifact`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)

        core.sendChatMessage("Can you explain quantum entanglement to me?")

        val reply = lastJarvisReply(core)
        assertFalse(reply.lowercase().contains("we recently touched on"))
        assertTrue("the offline fallback should echo the real question, not a contaminated blob", reply.contains("quantum entanglement"))
    }
}
