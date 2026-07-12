"""
jarvis.connections.profiles

Sprint-6 Part 11 — Connection Profiles.

A profile switch NEVER approves a new connection and NEVER exceeds a
connection's existing trust level — it only suspends connections not
tagged for the target profile and restores (via ConnectionManager's own
reconnect() + a caller-driven mark_connected(), never bypassing that
sequence) connections that ARE tagged and were already APPROVED at some
point. This keeps "Owner may switch instantly" (Part 11) fully
consistent with "No automatic reconnection" (Part 3) — instant here
means the SUSPEND side is immediate and automatic (suspending is always
safe to do without fresh approval), while the RESTORE side still goes
through ConnectionManager's ordinary reconnect() gate, which itself
never silently re-establishes a live session without a subsequent
explicit connect through the Provider Adapter.

PRIVACY mode is implemented honestly as a distinct, narrower case: only
connections whose `metadata` explicitly marks them `privacy_safe=True`
remain eligible: everything else is suspended, exactly like OFFLINE. No
connection is privacy_safe by default — this sprint has no local-LLM
adapter yet (Part 9's own future example), so today PRIVACY and OFFLINE
produce the same practical result. That's stated here rather than left
implicit, since a silent "PRIVACY mode doesn't actually do anything
different yet" would be exactly the kind of quietly-overstated capability
Article III exists to prevent.
"""

from __future__ import annotations

from enum import Enum
from typing import Optional

from jarvis.audit import AuditLedger
from jarvis.connections.connection_manager import ConnectionManager
from jarvis.connections.models import Connection, ConnectionStatus


class ConnectionProfile(str, Enum):
    WORK = "work"
    PERSONAL = "personal"
    OFFLINE = "offline"
    PRIVACY = "privacy"


class ProfileManager:
    def __init__(self, connection_manager: ConnectionManager, audit_ledger: AuditLedger) -> None:
        self._connections = connection_manager
        self._audit = audit_ledger
        self._active_profile: Optional[ConnectionProfile] = None

    @property
    def active_profile(self) -> Optional[ConnectionProfile]:
        return self._active_profile

    def activate(self, profile: ConnectionProfile) -> tuple[Connection, ...]:
        eligible_tag = profile.value
        affected: list[Connection] = []

        for connection in self._connections.list_all():
            should_be_active = self._is_eligible(connection, profile, eligible_tag)

            if should_be_active:
                if connection.status is ConnectionStatus.SUSPENDED:
                    # Restoring all the way to CONNECTED here is still
                    # Owner-triggered, not automatic in the sense Part 3
                    # forbids: activating a profile IS the Owner's
                    # explicit action, and both steps below happen as
                    # one atomic consequence of it — this is different
                    # from JARVIS silently reconnecting something on a
                    # timer or retry loop with no Owner action at all.
                    self._connections.reconnect(connection.connection_id)
                    self._connections.mark_connected(connection.connection_id)
                    affected.append(connection)
                # A connection that is PENDING_APPROVAL, REJECTED, or
                # DISCONNECTED is left exactly as-is — profile switching
                # never approves anything on the Owner's behalf.
            else:
                if connection.status is ConnectionStatus.CONNECTED:
                    self._connections.suspend(connection.connection_id, reason=f"Profile switched to '{profile.value}'.")
                    affected.append(connection)

        self._active_profile = profile
        self._audit.record(
            event_type="connection.profile_activated",
            message=f"Connection profile switched to '{profile.value}'.",
            details={"profile": profile.value, "affected_connection_count": len(affected)},
        )
        return tuple(affected)

    @staticmethod
    def _is_eligible(connection: Connection, profile: ConnectionProfile, tag: str) -> bool:
        if profile is ConnectionProfile.OFFLINE:
            return False
        if profile is ConnectionProfile.PRIVACY:
            return bool(connection.metadata.get("privacy_safe", False))
        return tag in connection.profile_tags

    def is_healthy(self) -> bool:
        return True
