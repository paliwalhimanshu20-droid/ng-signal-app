package com.jarvis.os.app.data

import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.PermissionScope
import com.jarvis.os.app.data.repository.ConnectionOperationError
import com.jarvis.os.app.data.repository.MockConnectionRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
    fun `sprint 9 - full lifecycle goes through connecting before connected`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection("provider-tv", "Living Room TV", setOf(PermissionScope.READ), PermissionScope.READ)

        repo.approve(connection.connectionId, approvedBy = "owner")
        assertEquals(ConnectionStatus.APPROVED, repo.connections.value.first { it.connectionId == connection.connectionId }.status)

        // Sprint 9: APPROVED cannot jump straight to CONNECTED anymore.
        assertThrows(ConnectionOperationError::class.java) {
            repo.markConnected(connection.connectionId)
        }

        repo.connect(connection.connectionId)
        assertEquals(ConnectionStatus.CONNECTING, repo.connections.value.first { it.connectionId == connection.connectionId }.status)

        repo.markConnected(connection.connectionId)
        assertEquals(ConnectionStatus.CONNECTED, repo.connections.value.first { it.connectionId == connection.connectionId }.status)
    }

    @Test
    fun `sprint 9 - connecting attempt can fail into error`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection("provider-x", "X", setOf(PermissionScope.READ), PermissionScope.READ)
        repo.approve(connection.connectionId, "owner")
        repo.connect(connection.connectionId)

        repo.markError(connection.connectionId, "handshake timed out")

        assertEquals(ConnectionStatus.ERROR, repo.connections.value.first { it.connectionId == connection.connectionId }.status)
    }

    @Test
    fun `sprint 9 - error state can retry back through connecting`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection("provider-x", "X", setOf(PermissionScope.READ), PermissionScope.READ)
        repo.approve(connection.connectionId, "owner")
        repo.connect(connection.connectionId)
        repo.markError(connection.connectionId, "handshake timed out")

        repo.reconnect(connection.connectionId)
        assertEquals(ConnectionStatus.CONNECTING, repo.connections.value.first { it.connectionId == connection.connectionId }.status)

        repo.markConnected(connection.connectionId)
        assertEquals(ConnectionStatus.CONNECTED, repo.connections.value.first { it.connectionId == connection.connectionId }.status)
    }

    @Test
    fun `sprint 9 - a live connection can error out without going through suspend`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection("provider-x", "X", setOf(PermissionScope.READ), PermissionScope.READ)
        repo.approve(connection.connectionId, "owner")
        repo.connect(connection.connectionId)
        repo.markConnected(connection.connectionId)

        repo.markError(connection.connectionId, "dropped")

        assertEquals(ConnectionStatus.ERROR, repo.connections.value.first { it.connectionId == connection.connectionId }.status)
    }

    @Test
    fun `sprint 9 - disconnect from a terminal state is a no-op, not a thrown error`() {
        val repo = MockConnectionRepository()
        val connection = repo.requestConnection("provider-x", "X", setOf(PermissionScope.READ), PermissionScope.READ)
        repo.reject(connection.connectionId, "no")

        repo.disconnect(connection.connectionId) // must not throw

        assertEquals(ConnectionStatus.REJECTED, repo.connections.value.first { it.connectionId == connection.connectionId }.status)
    }

    @Test
    fun `sprint 9 - every accepted transition is published on the transitions flow`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockConnectionRepository()
        val seen = mutableListOf<ConnectionStatus>()
        val connection = repo.requestConnection("provider-x", "X", setOf(PermissionScope.READ), PermissionScope.READ)

        // UnconfinedTestDispatcher runs a freshly-launched coroutine eagerly, up
        // to its first suspension point, before `launch` returns control here --
        // so by the next line the collector below is already registered and
        // suspended inside collect(), with no race against the emissions that
        // follow (the earlier StandardTestDispatcher + yield() version of this
        // test depended on scheduler ordering that didn't hold in CI -- this
        // does not).
        val job = launch { repo.transitions.collect { seen += it.newStatus } }

        repo.approve(connection.connectionId, "owner")
        repo.connect(connection.connectionId)
        repo.markConnected(connection.connectionId)
        repo.suspend(connection.connectionId, "pause")

        job.cancel()
        assertEquals(
            listOf(ConnectionStatus.APPROVED, ConnectionStatus.CONNECTING, ConnectionStatus.CONNECTED, ConnectionStatus.SUSPENDED),
            seen,
        )
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
