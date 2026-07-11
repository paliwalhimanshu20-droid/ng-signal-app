"""
jarvis.approval.models

ApprovalRequest (mutable — its status genuinely transitions over its
lifetime, mirroring Task's own mutability pattern) and ApprovalDecision
(immutable — the final, point-in-time resolution).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from enum import Enum
from typing import Optional
from uuid import uuid4

from jarvis.orchestrator.task_planner import Tier


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class ApprovalStatus(str, Enum):
    NOT_REQUIRED = "not_required"
    WAITING = "waiting"
    APPROVED = "approved"
    REJECTED = "rejected"
    EXPIRED = "expired"


@dataclass
class ApprovalRequest:
    """
    A single approval's lifecycle record.

    Mutable by design (status advances WAITING -> APPROVED/REJECTED/EXPIRED),
    exactly like jarvis.intake.models.Task and jarvis.registry.models.AgentRecord
    before it — the same justified exception to "prefer immutability,"
    applied consistently across the codebase for anything that generically
    represents a genuine, auditable state machine over time.
    """

    approval_id: str
    task_id: str
    tier: Tier
    reason: str
    status: ApprovalStatus
    created_at: str
    expires_at: Optional[str]

    @staticmethod
    def new(
        task_id: str,
        tier: Tier,
        reason: str,
        status: ApprovalStatus,
        timeout_seconds: Optional[float] = None,
    ) -> "ApprovalRequest":
        now = _utc_now()
        expires_at = (now + timedelta(seconds=timeout_seconds)).isoformat() if timeout_seconds else None
        return ApprovalRequest(
            approval_id=_new_id("approval"),
            task_id=task_id,
            tier=tier,
            reason=reason,
            status=status,
            created_at=now.isoformat(),
            expires_at=expires_at,
        )

    def is_expired(self) -> bool:
        if self.expires_at is None:
            return False
        return _utc_now() > datetime.fromisoformat(self.expires_at)


@dataclass(frozen=True)
class ApprovalDecision:
    """The final, immutable resolution of an ApprovalRequest."""

    decision_id: str
    approved: bool
    approved_by: Optional[str]
    approval_timestamp: Optional[str]
    confirmation_required: bool
    audit_reference: str

    @staticmethod
    def new(
        approved: bool,
        approved_by: Optional[str],
        confirmation_required: bool,
        audit_reference: str,
    ) -> "ApprovalDecision":
        return ApprovalDecision(
            decision_id=_new_id("appdec"),
            approved=approved,
            approved_by=approved_by,
            approval_timestamp=_utc_now().isoformat() if approved_by else None,
            confirmation_required=confirmation_required,
            audit_reference=audit_reference,
        )
