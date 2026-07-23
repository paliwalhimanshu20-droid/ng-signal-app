package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.ConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 11 Governance: AuditFactory has no null case -- every CoreEvent produces an AuditEntry, unlike NotificationFactory which filters. */
class AuditFactoryTest {

    @Test
    fun `chat events produce audit entries even though they produce no notification`() {
        val entry = AuditFactory.from(CoreEvent.ChatMessageSent("default", "hello"))
        assertEquals("Chat", entry.category)
        assertTrue(entry.summary.contains("default"))
    }

    @Test
    fun `tool execution is categorized as Tool`() {
        val entry = AuditFactory.from(CoreEvent.ToolExecuted("calculator", true, "4.0"))
        assertEquals("Tool", entry.category)
        assertTrue(entry.summary.contains("succeeded"))
    }

    @Test
    fun `connection status change is categorized as Connection`() {
        val entry = AuditFactory.from(
            CoreEvent.ConnectionStatusChanged("c1", "Claude", ConnectionStatus.APPROVED, ConnectionStatus.CONNECTING),
        )
        assertEquals("Connection", entry.category)
    }

    @Test
    fun `approval status change is categorized as Approval`() {
        val entry = AuditFactory.from(
            CoreEvent.ApprovalStatusChanged("a1", "Connect X", null, ApprovalOutcome.PENDING, ApprovalOutcome.APPROVED, "owner"),
        )
        assertEquals("Approval", entry.category)
    }
}
