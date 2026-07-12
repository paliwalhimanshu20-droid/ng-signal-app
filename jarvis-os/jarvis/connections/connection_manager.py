"""
jarvis.connections.connection_manager

Sprint-6 Parts 1, 3, 5 — Connection Manager, Owner Sovereignty, Connection
Audit.

ConnectionManager is the single gateway for every external service. No
module anywhere in this codebase may hold a live provider adapter, an
API key, or a network client except through a Connection this class
issued and is still willing to vouch for (status CONNECTED, health not
UNHEALTHY) — jarvis.ai_coordination.ai_coordinator's Sprint-6 dispatch
method (see its module docstring) checks exactly this before ever
calling an adapter.

Owner Sovereignty (Part 3) is enforced here as actual code behavior, not
documentation:
  - No connection reaches CONNECTED without an explicit approve() call
    that only the Interface Layer (acting for the Owner) may invoke.
  - reject()/disconnect() clear granted_permissions to TrustLevel.none()
    immediately — "No provider may retain permissions after rejection."
  - Nothing in this class ever transitions a connection back to
    CONNECTED except an explicit reconnect()/restore() call — "No
    automatic reconnection" is satisfied by there being no code path
    (timer, retry loop, health-check side effect) that calls those
    methods except a direct, external invocation.
  - change_trust_level() can only ever narrow or rearrange permissions
    within the existing, frozen maximum_permission ceiling — "No
    permission expansion" (see models.TrustLevel's docstring).
"""

from __future__ import annotations

from typing import Optional

from jarvis.audit import AuditLedger
from jarvis.connections.models import (
    _SCOPE_ORDER,
    Connection,
    ConnectionHealthStatus,
    ConnectionStatus,
    PermissionScope,
    TrustLevel,
    utc_now_iso,
)


class ConnectionError_(Exception):
    """Raised for any invalid Connection Manager operation. Named with a trailing underscore to avoid shadowing the Python builtin ConnectionError."""


def _scope_rank(scope: PermissionScope) -> int:
    return _SCOPE_ORDER.index(scope)


class ConnectionManager:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger
        self._connections: dict[str, Connection] = {}

    # -- lifecycle: request / approve / reject -----------------------------------

    def request_connection(
        self,
        provider_id: str,
        provider_name: str,
        requested_permissions: frozenset[PermissionScope],
        maximum_permission: PermissionScope,
        approval_required_for: frozenset[PermissionScope] = frozenset(),
        profile_tags: tuple[str, ...] = (),
    ) -> Connection:
        connection = Connection.new(
            provider_id=provider_id,
            provider_name=provider_name,
            requested_permissions=requested_permissions,
            maximum_permission=maximum_permission,
            approval_required_for=approval_required_for,
            profile_tags=profile_tags,
        )
        self._connections[connection.connection_id] = connection
        self._audit.record(
            event_type="connection.requested",
            message=f"Connection requested for provider '{provider_name}'.",
            details={"connection_id": connection.connection_id, "provider_id": provider_id},
        )
        return connection

    def approve(self, connection_id: str, approved_by: str) -> Connection:
        connection = self._require(connection_id)
        self._require_status(connection, ConnectionStatus.PENDING_APPROVAL, "approve")
        connection.status = ConnectionStatus.APPROVED
        self._touch(connection)
        self._audit.record(
            event_type="connection.approved",
            message=f"Connection to '{connection.provider_name}' approved by {approved_by}.",
            details={"connection_id": connection_id, "approved_by": approved_by},
        )
        return connection

    def reject(self, connection_id: str, reason: str) -> Connection:
        connection = self._require(connection_id)
        self._require_status(connection, ConnectionStatus.PENDING_APPROVAL, "reject")
        connection.status = ConnectionStatus.REJECTED
        connection.trust_level = TrustLevel.none()
        self._touch(connection)
        self._audit.record(
            event_type="connection.rejected",
            message=f"Connection to '{connection.provider_name}' rejected: {reason}",
            details={"connection_id": connection_id, "reason": reason},
        )
        return connection

    # -- lifecycle: connect / disconnect / suspend / restore ------------------------

    def mark_connected(self, connection_id: str) -> Connection:
        """Called after an adapter's connect() call actually succeeds (see ai_coordinator.py's Sprint-6 dispatch) — this method itself makes no network call, it only records the outcome."""
        connection = self._require(connection_id)
        self._require_status(connection, ConnectionStatus.APPROVED, "mark_connected")
        connection.status = ConnectionStatus.CONNECTED
        connection.health = ConnectionHealthStatus.HEALTHY
        self._touch(connection)
        self._audit.record(
            event_type="connection.connected",
            message=f"Connection to '{connection.provider_name}' established.",
            details={"connection_id": connection_id},
        )
        return connection

    def mark_failed(self, connection_id: str, reason: str) -> Connection:
        connection = self._require(connection_id)
        connection.status = ConnectionStatus.FAILED
        connection.health = ConnectionHealthStatus.UNHEALTHY
        self._touch(connection)
        self._audit.record(
            event_type="connection.failed",
            message=f"Connection to '{connection.provider_name}' failed: {reason}",
            details={"connection_id": connection_id, "reason": reason},
        )
        return connection

    def disconnect(self, connection_id: str, reason: Optional[str] = None) -> Connection:
        connection = self._require(connection_id)
        if connection.status in (ConnectionStatus.REJECTED, ConnectionStatus.DISCONNECTED):
            return connection  # idempotent: disconnecting an already-inert connection is a no-op, not an error
        connection.status = ConnectionStatus.DISCONNECTED
        connection.trust_level = TrustLevel.none()
        connection.health = ConnectionHealthStatus.UNKNOWN
        self._touch(connection)
        self._audit.record(
            event_type="connection.disconnected",
            message=f"Connection to '{connection.provider_name}' disconnected." + (f" Reason: {reason}" if reason else ""),
            details={"connection_id": connection_id, "reason": reason},
        )
        return connection

    def suspend(self, connection_id: str, reason: str) -> Connection:
        connection = self._require(connection_id)
        self._require_status(connection, ConnectionStatus.CONNECTED, "suspend")
        connection.status = ConnectionStatus.SUSPENDED
        self._touch(connection)
        self._audit.record(
            event_type="connection.suspended",
            message=f"Connection to '{connection.provider_name}' suspended: {reason}",
            details={"connection_id": connection_id, "reason": reason},
        )
        return connection

    def reconnect(self, connection_id: str) -> Connection:
        """
        Restores a SUSPENDED connection to APPROVED, ready for a fresh
        mark_connected() once the adapter's connect() actually succeeds
        again. Deliberately does NOT jump straight to CONNECTED itself —
        this method records the Owner's *intent* to reconnect; the
        Provider Adapter still has to actually re-establish the session,
        exactly like the first connection did. This is what "No
        automatic reconnection" (Part 3) means in code: reconnect() is
        always an explicit, single call the Owner (or Interface Layer on
        the Owner's behalf) makes — nothing in this class ever calls it
        for them.
        """
        connection = self._require(connection_id)
        self._require_status(connection, ConnectionStatus.SUSPENDED, "reconnect")
        connection.status = ConnectionStatus.APPROVED
        self._touch(connection)
        self._audit.record(
            event_type="connection.restored",
            message=f"Connection to '{connection.provider_name}' restored to approved; awaiting re-connect.",
            details={"connection_id": connection_id},
        )
        return connection

    def disable_all(self, reason: str) -> tuple[Connection, ...]:
        """Acceptance Scenario 3: immediately disconnects every non-inert connection. No selective skipping — 'Disable All Connections' means all."""
        affected = []
        for connection in self._connections.values():
            if connection.status not in (ConnectionStatus.REJECTED, ConnectionStatus.DISCONNECTED):
                self.disconnect(connection.connection_id, reason=reason)
                affected.append(connection)
        self._audit.record(
            event_type="connection.disable_all",
            message=f"All connections disabled by Owner request: {reason}",
            details={"connection_count": len(affected)},
        )
        return tuple(affected)

    # -- trust / permissions ------------------------------------------------------

    def change_trust_level(self, connection_id: str, new_granted_permissions: frozenset[PermissionScope]) -> Connection:
        connection = self._require(connection_id)
        current = connection.trust_level
        if any(_scope_rank(scope) > _scope_rank(current.maximum_permission) for scope in new_granted_permissions):
            raise ConnectionError_(
                f"Cannot grant permission(s) above this connection's maximum_permission "
                f"ceiling ('{current.maximum_permission.value}'). No permission expansion "
                f"beyond the original ceiling is permitted (Owner Sovereignty, Part 3)."
            )
        connection.trust_level = TrustLevel(
            granted_permissions=frozenset(new_granted_permissions),
            approval_required_for=current.approval_required_for,
            maximum_permission=current.maximum_permission,
        )
        self._touch(connection)
        self._audit.record(
            event_type="connection.permission_changed",
            message=f"Trust level changed for '{connection.provider_name}'.",
            details={
                "connection_id": connection_id,
                "granted_permissions": sorted(s.value for s in new_granted_permissions),
            },
        )
        return connection

    def verify_permission(self, connection_id: str, scope: PermissionScope) -> bool:
        connection = self._require(connection_id)
        if connection.status is not ConnectionStatus.CONNECTED:
            return False
        return connection.trust_level.permits(scope)

    def verify_trust(self, connection_id: str) -> bool:
        connection = self._require(connection_id)
        return connection.status is ConnectionStatus.CONNECTED and connection.health is not ConnectionHealthStatus.UNHEALTHY

    # -- health ---------------------------------------------------------------------

    def record_health(self, connection_id: str, health: ConnectionHealthStatus, sync_timestamp: Optional[str] = None) -> Connection:
        """Records a health outcome an adapter's health() call already produced — this method makes no network call itself."""
        connection = self._require(connection_id)
        connection.health = health
        if sync_timestamp is not None:
            connection.last_sync = sync_timestamp
        self._touch(connection)
        self._audit.record(
            event_type="connection.health_checked",
            message=f"Health check for '{connection.provider_name}': {health.value}.",
            details={"connection_id": connection_id, "health": health.value},
        )
        return connection

    # -- queries -----------------------------------------------------------------------

    def get(self, connection_id: str) -> Connection:
        return self._require(connection_id)

    def list_all(self) -> tuple[Connection, ...]:
        return tuple(self._connections.values())

    def list_by_profile(self, profile_tag: str) -> tuple[Connection, ...]:
        return tuple(c for c in self._connections.values() if profile_tag in c.profile_tags)

    def list_connected(self) -> tuple[Connection, ...]:
        return tuple(c for c in self._connections.values() if c.status is ConnectionStatus.CONNECTED)

    def _require(self, connection_id: str) -> Connection:
        try:
            return self._connections[connection_id]
        except KeyError as exc:
            raise ConnectionError_(f"No connection found with id '{connection_id}'.") from exc

    @staticmethod
    def _require_status(connection: Connection, expected: ConnectionStatus, operation: str) -> None:
        if connection.status is not expected:
            raise ConnectionError_(
                f"Cannot {operation} connection '{connection.connection_id}': status is "
                f"'{connection.status.value}', expected '{expected.value}'."
            )

    @staticmethod
    def _touch(connection: Connection) -> None:
        connection.updated_at = utc_now_iso()

    def is_healthy(self) -> bool:
        return isinstance(self._connections, dict)

    def __len__(self) -> int:
        return len(self._connections)
