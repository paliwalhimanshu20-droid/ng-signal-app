"""
Sprint-6 Part 13 — Connection Manager tests: approval, reject, disconnect,
suspend, reconnect, trust levels, disable all, audit.
"""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.connections.connection_manager import ConnectionError_, ConnectionManager
from jarvis.connections.models import ConnectionHealthStatus, ConnectionStatus, PermissionScope, TrustLevel


@pytest.fixture()
def audit_ledger(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    return ledger


@pytest.fixture()
def manager(audit_ledger):
    return ConnectionManager(audit_ledger)


def _request(manager, permissions=frozenset({PermissionScope.READ}), maximum=PermissionScope.READ, **kwargs):
    return manager.request_connection(
        provider_id="provider-test",
        provider_name="Test Provider",
        requested_permissions=permissions,
        maximum_permission=maximum,
        **kwargs,
    )


# --- Approval / Rejection --------------------------------------------------------


def test_request_connection_starts_pending_approval(manager):
    connection = _request(manager)
    assert connection.status is ConnectionStatus.PENDING_APPROVAL


def test_approve_transitions_to_approved(manager):
    connection = _request(manager)
    manager.approve(connection.connection_id, approved_by="owner")
    assert manager.get(connection.connection_id).status is ConnectionStatus.APPROVED


def test_cannot_approve_twice(manager):
    connection = _request(manager)
    manager.approve(connection.connection_id, approved_by="owner")
    with pytest.raises(ConnectionError_):
        manager.approve(connection.connection_id, approved_by="owner")


def test_reject_clears_permissions(manager):
    connection = _request(manager, permissions=frozenset({PermissionScope.READ, PermissionScope.WRITE}))
    manager.reject(connection.connection_id, reason="Owner declined")
    rejected = manager.get(connection.connection_id)
    assert rejected.status is ConnectionStatus.REJECTED
    assert rejected.trust_level.granted_permissions == frozenset()


def test_rejected_connection_never_reaches_connected(manager):
    connection = _request(manager)
    manager.reject(connection.connection_id, reason="no")
    with pytest.raises(ConnectionError_):
        manager.mark_connected(connection.connection_id)


# --- Connect / Disconnect / Suspend / Reconnect --------------------------------------


def test_mark_connected_requires_approved_first(manager):
    connection = _request(manager)
    with pytest.raises(ConnectionError_):
        manager.mark_connected(connection.connection_id)


def test_full_lifecycle_connect_then_disconnect(manager):
    connection = _request(manager)
    manager.approve(connection.connection_id, "owner")
    manager.mark_connected(connection.connection_id)
    assert manager.get(connection.connection_id).status is ConnectionStatus.CONNECTED

    manager.disconnect(connection.connection_id, reason="owner request")
    disconnected = manager.get(connection.connection_id)
    assert disconnected.status is ConnectionStatus.DISCONNECTED
    assert disconnected.trust_level.granted_permissions == frozenset()


def test_disconnect_is_idempotent(manager):
    connection = _request(manager)
    manager.reject(connection.connection_id, "no")
    # Disconnecting an already-rejected connection must not raise.
    result = manager.disconnect(connection.connection_id)
    assert result.status is ConnectionStatus.REJECTED  # unchanged, not overwritten to DISCONNECTED


def test_suspend_requires_connected(manager):
    connection = _request(manager)
    manager.approve(connection.connection_id, "owner")
    with pytest.raises(ConnectionError_):
        manager.suspend(connection.connection_id, reason="test")


def test_suspend_then_reconnect_cycle(manager):
    connection = _request(manager)
    manager.approve(connection.connection_id, "owner")
    manager.mark_connected(connection.connection_id)
    manager.suspend(connection.connection_id, reason="pausing")
    assert manager.get(connection.connection_id).status is ConnectionStatus.SUSPENDED

    manager.reconnect(connection.connection_id)
    assert manager.get(connection.connection_id).status is ConnectionStatus.APPROVED

    manager.mark_connected(connection.connection_id)
    assert manager.get(connection.connection_id).status is ConnectionStatus.CONNECTED


def test_reconnect_requires_suspended(manager):
    connection = _request(manager)
    manager.approve(connection.connection_id, "owner")
    with pytest.raises(ConnectionError_):
        manager.reconnect(connection.connection_id)


def test_no_automatic_reconnection_reconnect_never_reaches_connected_by_itself(manager):
    """reconnect() alone must never produce CONNECTED — see Part 3."""
    connection = _request(manager)
    manager.approve(connection.connection_id, "owner")
    manager.mark_connected(connection.connection_id)
    manager.suspend(connection.connection_id, reason="test")
    result = manager.reconnect(connection.connection_id)
    assert result.status is not ConnectionStatus.CONNECTED
    assert result.status is ConnectionStatus.APPROVED


# --- Disable All (Acceptance Scenario 3) --------------------------------------------


def test_disable_all_disconnects_every_active_connection(manager):
    connections = []
    for i in range(5):
        c = _request(manager)
        manager.approve(c.connection_id, "owner")
        manager.mark_connected(c.connection_id)
        connections.append(c)

    affected = manager.disable_all(reason="Owner requested full shutdown")
    assert len(affected) == 5
    for c in connections:
        assert manager.get(c.connection_id).status is ConnectionStatus.DISCONNECTED
        assert manager.get(c.connection_id).trust_level.granted_permissions == frozenset()


def test_disable_all_skips_already_inert_connections(manager):
    active = _request(manager)
    manager.approve(active.connection_id, "owner")
    manager.mark_connected(active.connection_id)

    rejected = _request(manager)
    manager.reject(rejected.connection_id, "no")

    affected = manager.disable_all(reason="shutdown")
    assert len(affected) == 1
    assert affected[0].connection_id == active.connection_id


# --- Trust Levels (Part 4) -----------------------------------------------------------


def test_trust_level_permits_within_granted_scope(manager):
    connection = _request(
        manager,
        permissions=frozenset({PermissionScope.READ, PermissionScope.WRITE}),
        maximum=PermissionScope.WRITE,
    )
    assert connection.trust_level.permits(PermissionScope.READ)
    assert connection.trust_level.permits(PermissionScope.WRITE)
    assert not connection.trust_level.permits(PermissionScope.EXECUTE)


def test_change_trust_level_cannot_exceed_maximum_permission(manager):
    connection = _request(manager, permissions=frozenset({PermissionScope.READ}), maximum=PermissionScope.READ)
    with pytest.raises(ConnectionError_):
        manager.change_trust_level(connection.connection_id, frozenset({PermissionScope.EXECUTE}))


def test_change_trust_level_can_narrow_within_ceiling(manager):
    connection = _request(
        manager,
        permissions=frozenset({PermissionScope.READ, PermissionScope.WRITE}),
        maximum=PermissionScope.WRITE,
    )
    manager.change_trust_level(connection.connection_id, frozenset({PermissionScope.READ}))
    updated = manager.get(connection.connection_id)
    assert updated.trust_level.granted_permissions == frozenset({PermissionScope.READ})
    assert updated.trust_level.maximum_permission is PermissionScope.WRITE  # ceiling never changes


def test_verify_permission_false_unless_connected(manager):
    connection = _request(manager, permissions=frozenset({PermissionScope.READ}), maximum=PermissionScope.READ)
    assert not manager.verify_permission(connection.connection_id, PermissionScope.READ)
    manager.approve(connection.connection_id, "owner")
    manager.mark_connected(connection.connection_id)
    assert manager.verify_permission(connection.connection_id, PermissionScope.READ)


def test_verify_trust_false_when_unhealthy(manager):
    connection = _request(manager)
    manager.approve(connection.connection_id, "owner")
    manager.mark_connected(connection.connection_id)
    assert manager.verify_trust(connection.connection_id)

    manager.record_health(connection.connection_id, ConnectionHealthStatus.UNHEALTHY)
    assert not manager.verify_trust(connection.connection_id)


def test_trust_level_none_has_zero_permissions():
    trust = TrustLevel.none()
    assert trust.granted_permissions == frozenset()
    for scope in PermissionScope:
        assert not trust.permits(scope)


# --- Audit -------------------------------------------------------------------------


def test_full_lifecycle_is_fully_audited(manager, audit_ledger):
    connection = _request(manager)
    manager.approve(connection.connection_id, "owner")
    manager.mark_connected(connection.connection_id)
    manager.record_health(connection.connection_id, ConnectionHealthStatus.HEALTHY)
    manager.suspend(connection.connection_id, "pause")
    manager.reconnect(connection.connection_id)
    manager.mark_connected(connection.connection_id)
    manager.change_trust_level(connection.connection_id, frozenset({PermissionScope.READ}))
    manager.disconnect(connection.connection_id)

    event_types = [e.event_type for e in audit_ledger.read_all()]
    required = (
        "connection.requested",
        "connection.approved",
        "connection.connected",
        "connection.health_checked",
        "connection.suspended",
        "connection.restored",
        "connection.permission_changed",
        "connection.disconnected",
    )
    for event in required:
        assert event in event_types, f"Missing connection audit event: {event}"


def test_rejection_is_audited(manager, audit_ledger):
    connection = _request(manager)
    manager.reject(connection.connection_id, reason="declined")
    event_types = [e.event_type for e in audit_ledger.read_all()]
    assert "connection.rejected" in event_types


# --- Queries ---------------------------------------------------------------------------


def test_unknown_connection_raises(manager):
    with pytest.raises(ConnectionError_):
        manager.get("does-not-exist")


def test_list_by_profile(manager):
    tagged = _request(manager, profile_tags=("work",))
    untagged = _request(manager)
    results = manager.list_by_profile("work")
    assert tagged.connection_id in [c.connection_id for c in results]
    assert untagged.connection_id not in [c.connection_id for c in results]
