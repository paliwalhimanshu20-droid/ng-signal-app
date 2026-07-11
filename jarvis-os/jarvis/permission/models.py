"""
jarvis.permission.models

PermissionRequest and PermissionDecision — the structured input and
output of every Permission Engine evaluation. Both are immutable,
timestamped records, since a permission evaluation is a point-in-time
governance decision that must be reconstructible after the fact
(Article IV) — never mutated once made.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from jarvis.orchestrator.task_planner import Tier


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass(frozen=True)
class PermissionRequest:
    """
    A single request to the Permission Engine: "may agent X exercise
    capability Y at tier Z for task T?"
    """

    request_id: str
    task_id: str
    agent_id: str
    requested_capability: str
    execution_tier: Tier
    timestamp: str

    @staticmethod
    def new(task_id: str, agent_id: str, requested_capability: str, execution_tier: Tier) -> "PermissionRequest":
        return PermissionRequest(
            request_id=_new_id("permreq"),
            task_id=task_id,
            agent_id=agent_id,
            requested_capability=requested_capability,
            execution_tier=execution_tier,
            timestamp=_utc_now_iso(),
        )


@dataclass(frozen=True)
class PermissionDecision:
    """
    The Permission Engine's structured answer.

    `allowed` reflects ONLY whether the agent/capability/tier combination
    is structurally valid to proceed — it does NOT mean execution may
    happen immediately. `required_approval` (and, downstream, the
    Approval Engine's own confirmation_required for Tier 3) governs
    whether execution must additionally wait on the owner. This split is
    deliberate: "permitted in principle" and "cleared to run right now"
    are different questions, and collapsing them was exactly the kind of
    ambiguity earlier architecture reviews in this series warned against.
    """

    decision_id: str
    allowed: bool
    reason: str
    required_approval: bool
    required_tier: Optional[Tier]
    warnings: tuple[str, ...]
    timestamp: str

    @staticmethod
    def new(
        allowed: bool,
        reason: str,
        required_approval: bool,
        required_tier: Optional[Tier],
        warnings: tuple[str, ...] = (),
    ) -> "PermissionDecision":
        return PermissionDecision(
            decision_id=_new_id("permdec"),
            allowed=allowed,
            reason=reason,
            required_approval=required_approval,
            required_tier=required_tier,
            warnings=warnings,
            timestamp=_utc_now_iso(),
        )
