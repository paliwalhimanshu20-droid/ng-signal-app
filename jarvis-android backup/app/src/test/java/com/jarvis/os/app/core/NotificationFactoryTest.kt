package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.NotificationCategory
import com.jarvis.os.app.data.model.NotificationPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFactoryTest {

    @Test
    fun `approval requested becomes an approval category notification`() {
        val event = CoreEvent.ApprovalRequested("approval-1", "Connect Claude")
        val notification = NotificationFactory.from(event)

        requireNotNull(notification)
        assertEquals(NotificationCategory.APPROVAL, notification.category)
        assertEquals("approval-1", notification.relatedEntityId)
        assertEquals("Connect Claude", notification.message)
    }

    @Test
    fun `approval approved becomes a normal-priority approval notification`() {
        val event = CoreEvent.ApprovalStatusChanged("a1", "Connect Claude", "c1", ApprovalOutcome.PENDING, ApprovalOutcome.APPROVED, "owner")
        val notification = NotificationFactory.from(event)

        requireNotNull(notification)
        assertEquals(NotificationCategory.APPROVAL, notification.category)
        assertEquals(NotificationPriority.NORMAL, notification.priority)
        assertEquals("Connect Claude approved", notification.title)
        // Related to the connection when one exists, so a tap could one
        // day open the right screen -- see NotificationFactory's docstring.
        assertEquals("c1", notification.relatedEntityId)
    }

    @Test
    fun `approval revoked becomes a high-priority approval notification`() {
        val event = CoreEvent.ApprovalStatusChanged("a1", "GitHub write", null, ApprovalOutcome.APPROVED, ApprovalOutcome.REVOKED, "owner", reason = "no longer needed")
        val notification = NotificationFactory.from(event)

        requireNotNull(notification)
        assertEquals(NotificationCategory.APPROVAL, notification.category)
        assertEquals(NotificationPriority.HIGH, notification.priority)
        assertEquals("GitHub write revoked", notification.title)
        assertEquals("no longer needed", notification.message)
        // Falls back to the approvalId when there's no related connection.
        assertEquals("a1", notification.relatedEntityId)
    }

    @Test
    fun `connection connected becomes a normal-priority connection notification`() {
        val event = CoreEvent.ConnectionStatusChanged("c1", "GitHub", ConnectionStatus.CONNECTING, ConnectionStatus.CONNECTED)
        val notification = NotificationFactory.from(event)

        requireNotNull(notification)
        assertEquals(NotificationCategory.CONNECTION, notification.category)
        assertEquals(NotificationPriority.NORMAL, notification.priority)
        assertEquals("GitHub connected", notification.title)
    }

    @Test
    fun `connection error becomes a high-priority error notification, not a connection one`() {
        val event = CoreEvent.ConnectionStatusChanged("c1", "GitHub", ConnectionStatus.CONNECTING, ConnectionStatus.ERROR, reason = "handshake timed out")
        val notification = NotificationFactory.from(event)

        requireNotNull(notification)
        assertEquals(NotificationCategory.ERROR, notification.category)
        assertEquals(NotificationPriority.HIGH, notification.priority)
        assertEquals("handshake timed out", notification.message)
    }

    @Test
    fun `intermediate connection hops produce no notification`() {
        assertNull(NotificationFactory.from(CoreEvent.ConnectionStatusChanged("c1", "GitHub", ConnectionStatus.PENDING_APPROVAL, ConnectionStatus.APPROVED)))
        assertNull(NotificationFactory.from(CoreEvent.ConnectionStatusChanged("c1", "GitHub", ConnectionStatus.APPROVED, ConnectionStatus.CONNECTING)))
    }

    @Test
    fun `chat and task events produce no notification`() {
        assertNull(NotificationFactory.from(CoreEvent.ChatMessageSent("s1", "hello")))
        assertNull(NotificationFactory.from(CoreEvent.ChatResponseReceived("s1", "m1")))
        assertNull(NotificationFactory.from(CoreEvent.TaskStatusChanged("t1", true)))
    }
}
