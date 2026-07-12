"""
jarvis.connections.models

Sprint-6 data models for the Connection Management Framework.

Owner Sovereignty (Part 3) is enforced structurally here, not just by
convention: `Connection.maximum_permission` is set once, at request
time, and no method anywhere in this package (see connection_manager.py)
ever raises it — only the Owner-triggered request_connection() call
establishes a ceiling, exactly the "ceiling, not grant" pattern
JARVIS-002 §18 already established for agent capabilities, applied here
to external connections.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional
from uuid import uuid4

__all__ = [
    "Connection",
    "ConnectionHealthStatus",
    "ConnectionStatus",
    "PermissionScope",
    "TrustLevel",
]


def new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


# --- Part 4: Trust Levels ----------------------------------------------------------


class PermissionScope(str, Enum):
    """
    Ordered by increasing consequence — READ < WRITE < EXECUTE <
    HIGH_RISK_ACTIONS. The ordering is used by TrustLevel.permits() to
    decide whether a granted ceiling covers a requested scope, mirroring
    jarvis.orchestrator.task_planner.Tier's own IntEnum-for-comparison
    pattern, applied here to permission scope instead of approval tier.
    """

    READ = "read"
    WRITE = "write"
    EXECUTE = "execute"
    HIGH_RISK_ACTIONS = "high_risk_actions"


_SCOPE_ORDER: tuple[PermissionScope, ...] = (
    PermissionScope.READ,
    PermissionScope.WRITE,
    PermissionScope.EXECUTE,
    PermissionScope.HIGH_RISK_ACTIONS,
)


@dataclass(frozen=True)
class TrustLevel:
    """
    Part 4's per-connection trust definition.

    `maximum_permission` is the ceiling this connection can ever reach —
    frozen for the connection's entire life, per Owner Sovereignty's "No
    permission expansion" mandate (Part 3): nothing in ConnectionManager
    can ever raise it after request_connection() sets it, only the Owner
    creating an entirely new connection request can establish a
    different ceiling.

    `granted_permissions` is what's ACTUALLY active right now — always a
    subset of (permission scopes at or below) `maximum_permission`, and
    the only field change_trust_level() is ever allowed to touch.

    `approval_required_for` names scopes that, even when granted, still
    require a fresh per-use Owner approval before a single action in
    that scope executes — the mechanism that makes "Approval Required"
    (Part 4) meaningfully different from "not granted at all."
    """

    granted_permissions: frozenset[PermissionScope]
    approval_required_for: frozenset[PermissionScope]
    maximum_permission: PermissionScope

    def permits(self, scope: PermissionScope) -> bool:
        if scope not in self.granted_permissions:
            return False
        return _SCOPE_ORDER.index(scope) <= _SCOPE_ORDER.index(self.maximum_permission)

    def requires_approval(self, scope: PermissionScope) -> bool:
        return scope in self.approval_required_for

    @staticmethod
    def none() -> "TrustLevel":
        """The trust level a rejected or freshly-disconnected connection holds — zero standing permissions, per 'No provider may retain permissions after rejection.'"""
        return TrustLevel(
            granted_permissions=frozenset(),
            approval_required_for=frozenset(),
            maximum_permission=PermissionScope.READ,
        )


# --- Connection lifecycle ------------------------------------------------------------


class ConnectionStatus(str, Enum):
    PENDING_APPROVAL = "pending_approval"
    APPROVED = "approved"
    CONNECTED = "connected"
    SUSPENDED = "suspended"
    DISCONNECTED = "disconnected"
    REJECTED = "rejected"
    FAILED = "failed"


class ConnectionHealthStatus(str, Enum):
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    UNHEALTHY = "unhealthy"
    UNKNOWN = "unknown"  # never checked yet, or connection isn't live


@dataclass
class Connection:
    """
    Mutable by design — status, trust_level, health, and last_sync
    genuinely change over the connection's life, same justified pattern
    already established for Task/ApprovalRequest/AgentRecord/Goal/
    AISession.

    `profile_tags` names which jarvis.connections.profiles.ConnectionProfile
    values this connection belongs to (Part 11) — a connection with no
    tags is never auto-included by any profile switch, per "Owner may
    switch instantly" being about instantly APPLYING a known
    configuration, never about guessing which connections should belong
    where.

    `metadata` NEVER holds a credential — see
    jarvis.connections.credentials.ConnectionCredentials's docstring for
    the structural separation between connection state (this class,
    freely inspectable/auditable) and secrets (never inspectable,
    never audited, never persisted in this object).
    """

    connection_id: str
    provider_id: str
    provider_name: str
    status: ConnectionStatus
    trust_level: TrustLevel
    health: ConnectionHealthStatus
    last_sync: Optional[str]
    profile_tags: tuple[str, ...]
    created_at: str
    updated_at: str
    metadata: dict[str, Any] = field(default_factory=dict)

    @staticmethod
    def new(
        provider_id: str,
        provider_name: str,
        requested_permissions: frozenset[PermissionScope],
        maximum_permission: PermissionScope,
        approval_required_for: frozenset[PermissionScope] = frozenset(),
        profile_tags: tuple[str, ...] = (),
        metadata: Optional[dict[str, Any]] = None,
    ) -> "Connection":
        now = utc_now_iso()
        return Connection(
            connection_id=new_id("connection"),
            provider_id=provider_id,
            provider_name=provider_name,
            status=ConnectionStatus.PENDING_APPROVAL,
            trust_level=TrustLevel(
                granted_permissions=frozenset(requested_permissions),
                approval_required_for=frozenset(approval_required_for),
                maximum_permission=maximum_permission,
            ),
            health=ConnectionHealthStatus.UNKNOWN,
            last_sync=None,
            profile_tags=profile_tags,
            created_at=now,
            updated_at=now,
            metadata=dict(metadata or {}),
        )
