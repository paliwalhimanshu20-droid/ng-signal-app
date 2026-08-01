package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.core.intelligence.localintent.LocalServiceDomain
import com.jarvis.os.app.data.model.ChatMessage
import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.model.MessageContentKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** "Phase 3C, Section 3+4 -- Response Source Engine + Confidence Engine." */
class ResponseSourceEngineTest {

    private fun message(sourceLocalDomain: String? = null, sourceToolIds: List<String> = emptyList()) = ChatMessage(
        messageId = "m1", author = MessageAuthor.JARVIS, kind = MessageContentKind.TEXT,
        content = "reply", timestamp = Instant.now(), sourceLocalDomain = sourceLocalDomain, sourceToolIds = sourceToolIds,
    )

    @Test
    fun `a device action message is HIGH confidence DEVICE_ACTION`() {
        val provenance = ResponseSourceEngine.classify(message(sourceLocalDomain = LocalServiceDomain.DEVICE_ACTION.name))
        assertEquals(ResponseSource.DEVICE_ACTION, provenance.source)
        assertEquals(ResponseConfidence.HIGH, provenance.confidence)
    }

    @Test
    fun `an evidence-engine message is HIGH confidence EVIDENCE_ENGINE`() {
        val provenance = ResponseSourceEngine.classify(message(sourceLocalDomain = LocalServiceDomain.EVIDENCE.name))
        assertEquals(ResponseSource.EVIDENCE_ENGINE, provenance.source)
        assertEquals(ResponseConfidence.HIGH, provenance.confidence)
    }

    @Test
    fun `a TIDB message is HIGH confidence TRADING_INTELLIGENCE_DATABASE`() {
        val provenance = ResponseSourceEngine.classify(message(sourceLocalDomain = LocalServiceDomain.TIDB.name))
        assertEquals(ResponseSource.TRADING_INTELLIGENCE_DATABASE, provenance.source)
        assertEquals(ResponseConfidence.HIGH, provenance.confidence)
    }

    @Test
    fun `a pure AI-composed message with no local domain and no tool ids is LOW confidence AI_PROVIDER`() {
        val provenance = ResponseSourceEngine.classify(message())
        assertEquals(ResponseSource.AI_PROVIDER, provenance.source)
        assertEquals(ResponseConfidence.LOW, provenance.confidence)
    }

    @Test
    fun `an AI reply grounded in real tool output is MEDIUM confidence, not LOW`() {
        val provenance = ResponseSourceEngine.classify(message(sourceToolIds = listOf("google-calendar")))
        assertEquals(ResponseSource.AI_PROVIDER, provenance.source)
        assertEquals(ResponseConfidence.MEDIUM, provenance.confidence)
    }

    @Test
    fun `a knowledge base template answer is MEDIUM confidence local knowledge`() {
        val provenance = ResponseSourceEngine.classify(message(sourceLocalDomain = LocalServiceDomain.KNOWLEDGE_BASE.name))
        assertEquals(ResponseSource.LOCAL_KNOWLEDGE, provenance.source)
        assertEquals(ResponseConfidence.MEDIUM, provenance.confidence)
    }

    @Test
    fun `a greeting template answer is HIGH confidence local knowledge, never presented as AI reasoning`() {
        val provenance = ResponseSourceEngine.classify(message(sourceLocalDomain = LocalServiceDomain.GREETING.name))
        assertEquals(ResponseSource.LOCAL_KNOWLEDGE, provenance.source)
        assertEquals(ResponseConfidence.HIGH, provenance.confidence)
    }
}
