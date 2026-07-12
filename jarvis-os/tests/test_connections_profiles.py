"""Sprint-6 Part 13 — Profile tests."""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.connections.connection_manager import ConnectionManager
from jarvis.connections.models import ConnectionStatus, PermissionScope
from jarvis.connections.profiles import ConnectionProfile, ProfileManager


@pytest.fixture()
def audit_ledger(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    return ledger


@pytest.fixture()
def manager(audit_ledger):
    return ConnectionManager(audit_ledger)


def _connect(manager, provider_id, name, tags=(), privacy_safe=False):
    connection = manager.request_connection(
        provider_id=provider_id,
        provider_name=name,
        requested_permissions=frozenset({PermissionScope.READ}),
        maximum_permission=PermissionScope.READ,
        profile_tags=tags,
    )
    manager.approve(connection.connection_id, "owner")
    manager.mark_connected(connection.connection_id)
    if privacy_safe:
        connection.metadata["privacy_safe"] = True
    return connection


def test_acceptance_scenario_4_work_profile(manager, audit_ledger):
    github = _connect(manager, "provider-github", "GitHub", tags=("work",))
    chatgpt = _connect(manager, "provider-openai", "ChatGPT", tags=("work",))
    claude = _connect(manager, "provider-anthropic", "Claude", tags=("work",))
    ng_signal = _connect(manager, "provider-ngsignal", "NG Signal Pro", tags=("work",))
    projectos = _connect(manager, "provider-projectos", "ProjectOS", tags=("work",))
    spotify = _connect(manager, "provider-spotify", "Spotify", tags=("personal",))
    calendar = _connect(manager, "provider-calendar", "Calendar", tags=("personal", "work"))

    profiles = ProfileManager(manager, audit_ledger)
    profiles.activate(ConnectionProfile.WORK)

    for c in (github, chatgpt, claude, ng_signal, projectos, calendar):
        assert manager.get(c.connection_id).status is ConnectionStatus.CONNECTED
    assert manager.get(spotify.connection_id).status is ConnectionStatus.SUSPENDED


def test_offline_profile_suspends_everything(manager, audit_ledger):
    a = _connect(manager, "provider-a", "A", tags=("work",))
    b = _connect(manager, "provider-b", "B", tags=("personal",))
    profiles = ProfileManager(manager, audit_ledger)
    profiles.activate(ConnectionProfile.OFFLINE)

    assert manager.get(a.connection_id).status is ConnectionStatus.SUSPENDED
    assert manager.get(b.connection_id).status is ConnectionStatus.SUSPENDED


def test_privacy_profile_only_keeps_privacy_safe_connections(manager, audit_ledger):
    safe = _connect(manager, "provider-safe", "Safe", privacy_safe=True)
    unsafe = _connect(manager, "provider-unsafe", "Unsafe")
    profiles = ProfileManager(manager, audit_ledger)
    profiles.activate(ConnectionProfile.PRIVACY)

    assert manager.get(safe.connection_id).status is ConnectionStatus.CONNECTED
    assert manager.get(unsafe.connection_id).status is ConnectionStatus.SUSPENDED


def test_profile_switch_never_approves_pending_connections(manager, audit_ledger):
    pending = manager.request_connection(
        provider_id="provider-x",
        provider_name="X",
        requested_permissions=frozenset({PermissionScope.READ}),
        maximum_permission=PermissionScope.READ,
        profile_tags=("work",),
    )
    profiles = ProfileManager(manager, audit_ledger)
    profiles.activate(ConnectionProfile.WORK)
    assert manager.get(pending.connection_id).status is ConnectionStatus.PENDING_APPROVAL


def test_profile_switch_is_instant_and_switching_back_restores(manager, audit_ledger):
    work_only = _connect(manager, "provider-w", "WorkTool", tags=("work",))
    personal_only = _connect(manager, "provider-p", "PersonalTool", tags=("personal",))

    profiles = ProfileManager(manager, audit_ledger)
    profiles.activate(ConnectionProfile.PERSONAL)
    assert manager.get(work_only.connection_id).status is ConnectionStatus.SUSPENDED
    assert manager.get(personal_only.connection_id).status is ConnectionStatus.CONNECTED

    profiles.activate(ConnectionProfile.WORK)
    assert manager.get(work_only.connection_id).status is ConnectionStatus.CONNECTED
    assert manager.get(personal_only.connection_id).status is ConnectionStatus.SUSPENDED


def test_active_profile_tracked(manager, audit_ledger):
    profiles = ProfileManager(manager, audit_ledger)
    assert profiles.active_profile is None
    profiles.activate(ConnectionProfile.WORK)
    assert profiles.active_profile is ConnectionProfile.WORK


def test_profile_activation_is_audited(manager, audit_ledger):
    profiles = ProfileManager(manager, audit_ledger)
    profiles.activate(ConnectionProfile.OFFLINE)
    event_types = [e.event_type for e in audit_ledger.read_all()]
    assert "connection.profile_activated" in event_types
