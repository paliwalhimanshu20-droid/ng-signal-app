package com.jarvis.os.app.data

import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.PermissionScope
import com.jarvis.os.app.data.repository.ConnectionOperationError
import com.jarvis.os.app.data.repository.MockConnectionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MockConnectionRepositoryTest {

    @Test
    fun `acceptance scenario 4 - reject leaves connection with zero permissions`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection(
            "provider-gmail", "Gmail",
            requestedPermissions = setOf(PermissionScope.READ, PermissionScope.WRITE),
            maximumPermission = PermissionScope.WRITE,
        )

        repo.reject(connection.connectionId, reason = "Owner declined")

        val rejected = repo.connections.value.first { it.connectionId == connection.connectionId }
        assertEquals(ConnectionStatus.REJECTED, rejected.status)
        assertTrue(rejected.trustLevel.grantedPermissions.isEmpty())
    }

    @Test
    fun `rejected connection can never reach connected`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection("provider-x", "X", setOf(PermissionScope.READ), PermissionScope.READ)
        repo.reject(connection.connectionId, "no")

        assertThrows(ConnectionOperationError::class.java) {
            repo.markConnected(connection.connectionId)
        }
    }

    @Test
    fun `acceptance scenario 3 - disable all disconnects every active connection`() {
        val repo = MockConnectionRepository()
        val before = repo.connections.value
        val activeCount = before.count { it.status == ConnectionStatus.CONNECTED }
        assertTrue("seed data should include connected connections", activeCount > 0)

        repo.disableAll(reason = "Owner disabled all connections")

        val after = repo.connections.value
        assertTrue(after.none { it.status == ConnectionStatus.CONNECTED })
        assertTrue(after.filter { it.status != ConnectionStatus.REJECTED }.all { it.status == ConnectionStatus.DISCONNECTED })
    }

    @Test
    fun `approve then connect full lifecycle`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection("provider-tv", "Living Room TV", setOf(PermissionScope.READ), PermissionScope.READ)

        repo.approve(connection.connectionId, approvedBy = "owner")
        assertEquals(ConnectionStatus.APPROVED, repo.connections.value.first { it.connectionId == connection.connectionId }.status)

        repo.markConnected(connection.connectionId)
        assertEquals(ConnectionStatus.CONNECTED, repo.connections.value.first { it.connectionId == connection.connectionId }.status)
    }

    @Test
    fun `suspend requires connected status`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection("provider-x", "X", setOf(PermissionScope.READ), PermissionScope.READ)
        assertThrows(ConnectionOperationError::class.java) {
            repo.suspend(connection.connectionId, "pause")
        }
    }
}
