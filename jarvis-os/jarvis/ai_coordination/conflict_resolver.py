"""
jarvis.ai_coordination.conflict_resolver

Sprint-5 Part 10 — Conflict Resolver.

Given a CONFLICT ConsensusResult, deterministically decides one of
Retry / Escalate / Ask Owner / Reject. "No automatic execution" — this
module never acts on its own decision; it returns a ConflictResolution
for the AICoordinator (and eventually the owner) to see, exactly like
jarvis.intelligence.decision_engine's Decision is advisory input to real
governance, never a substitute for it.

Rules are intentionally simple and risk-anchored, matching
jarvis.ai_coordination.model_selector's "risk narrows what's acceptable"
pattern: the higher the risk, the less this resolver is willing to
decide on its own.
"""

from __future__ import annotations

from datetime import datetime, timezone
from uuid import uuid4

from jarvis.audit import AuditLedger
from jarvis.ai_coordination.models import ConflictResolution, ConflictResolutionType, ConsensusResult, ConsensusStatus
from jarvis.intelligence.models import RiskLevel

_RETRY_LIMIT = 2
_LOW_CONFIDENCE_THRESHOLD = 0.4


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class ConflictResolver:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger

    def resolve(self, consensus: ConsensusResult, risk: RiskLevel, retry_count: int) -> ConflictResolution:
        if consensus.status is not ConsensusStatus.CONFLICT:
            raise ValueError("ConflictResolver.resolve() called on a non-CONFLICT ConsensusResult.")

        if risk in (RiskLevel.HIGH, RiskLevel.CRITICAL):
            resolution_type = ConflictResolutionType.ASK_OWNER
            reason = f"Risk level '{risk.value}' — a conflicting recommendation at this stakes level is never auto-resolved."
        elif consensus.confidence < _LOW_CONFIDENCE_THRESHOLD:
            resolution_type = ConflictResolutionType.REJECT
            reason = (
                f"Providers disagree AND even the most confident response ({consensus.confidence:.2f}) "
                f"is below the {_LOW_CONFIDENCE_THRESHOLD} threshold; no response here is worth retrying "
                f"or escalating toward."
            )
        elif retry_count >= _RETRY_LIMIT:
            resolution_type = ConflictResolutionType.ESCALATE
            reason = f"Retry limit ({_RETRY_LIMIT}) already reached without resolving the conflict; escalating rather than retrying indefinitely."
        elif not consensus.dissenting_provider_ids or not consensus.contributing_provider_ids:
            # A conflict with no clear majority/minority split (e.g. every
            # provider disagreed with every other) is not something a
            # retry is likely to fix by chance.
            resolution_type = ConflictResolutionType.ASK_OWNER
            reason = "No majority position exists among the conflicting responses; owner input is needed to break the tie."
        else:
            resolution_type = ConflictResolutionType.RETRY
            reason = f"Risk is '{risk.value}' and the retry limit has not been reached (retry_count={retry_count}); retrying is reasonable before escalating."

        resolution = ConflictResolution(
            resolution_id=_new_id("resolution"),
            resolution_type=resolution_type,
            reason=reason,
            created_at=_utc_now_iso(),
        )
        self._audit.record(
            event_type="ai_coordination.conflict_resolved",
            message=f"Conflict resolution: {resolution_type.value}.",
            details={"resolution_id": resolution.resolution_id, "consensus_id": consensus.consensus_id, "risk": risk.value},
        )
        return resolution

    def is_healthy(self) -> bool:
        return True
