"""
jarvis.ai_coordination.consensus_engine

Sprint-5 Part 9 — Consensus Engine.

"Do NOT call providers. Framework only." — this module's single method
takes an already-assembled sequence of ProviderResponse objects (from
tests today; from real, independently-dispatched provider adapters in a
future sprint) and compares them. It never dispatches anything itself.

Agreement is judged on `recommended_action` — a short, categorical field
(see models.ProviderResponse) — rather than free-text `content`
similarity, deliberately: comparing prose similarity would require
either a fixed-vocabulary heuristic that's really a disguised keyword
matcher and easy to fool, or an actual NLP/AI component this sprint
explicitly may not build. Categorical comparison is exactly as
deterministic as everything else in this layer and doesn't pretend to a
sophistication this sprint isn't scoped to have.
"""

from __future__ import annotations

from collections import Counter
from datetime import datetime, timezone
from typing import Sequence
from uuid import uuid4

from jarvis.audit import AuditLedger
from jarvis.ai_coordination.models import ConsensusResult, ConsensusStatus, ProviderResponse


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class ConsensusEngine:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger

    def build_consensus(self, responses: Sequence[ProviderResponse]) -> ConsensusResult:
        if len(responses) < 2:
            result = ConsensusResult(
                consensus_id=_new_id("consensus"),
                status=ConsensusStatus.INSUFFICIENT_RESPONSES,
                agreed_action=responses[0].recommended_action if responses else None,
                confidence=responses[0].confidence if responses else 0.0,
                contributing_provider_ids=tuple(r.provider_id for r in responses),
                dissenting_provider_ids=(),
                reason="Fewer than two responses were provided; no consensus can be formed or refuted.",
                created_at=_utc_now_iso(),
            )
            return self._finalize(result)

        action_counts = Counter(r.recommended_action for r in responses)
        top_action, top_count = action_counts.most_common(1)[0]

        if top_count == len(responses):
            agreeing = list(responses)
            result = ConsensusResult(
                consensus_id=_new_id("consensus"),
                status=ConsensusStatus.AGREEMENT,
                agreed_action=top_action,
                # The combined confidence is never higher than the weakest
                # agreeing response — agreement doesn't manufacture
                # certainty beyond what the least-confident contributor
                # actually supports.
                confidence=min(r.confidence for r in agreeing),
                contributing_provider_ids=tuple(r.provider_id for r in agreeing),
                dissenting_provider_ids=(),
                reason=f"All {len(responses)} response(s) agreed on action '{top_action}'.",
                created_at=_utc_now_iso(),
            )
            return self._finalize(result)

        agreeing = [r for r in responses if r.recommended_action == top_action]
        dissenting = [r for r in responses if r.recommended_action != top_action]
        result = ConsensusResult(
            consensus_id=_new_id("consensus"),
            status=ConsensusStatus.CONFLICT,
            agreed_action=None,
            # Best-case confidence across ALL responses (not just the
            # majority) — this is what lets ConflictResolver distinguish
            # "providers disagree, but at least one is confident" from
            # "providers disagree AND none of them are confident either,"
            # which call for different resolutions (escalate vs. reject).
            confidence=max(r.confidence for r in responses),
            contributing_provider_ids=tuple(r.provider_id for r in agreeing),
            dissenting_provider_ids=tuple(r.provider_id for r in dissenting),
            reason=(
                f"Responses disagreed: {dict(action_counts)}. "
                f"No single action was recommended by all providers."
            ),
            created_at=_utc_now_iso(),
        )
        return self._finalize(result)

    def _finalize(self, result: ConsensusResult) -> ConsensusResult:
        self._audit.record(
            event_type="ai_coordination.consensus_generated",
            message=f"Consensus status: {result.status.value}.",
            details={
                "consensus_id": result.consensus_id,
                "status": result.status.value,
                "agreed_action": result.agreed_action,
                "contributing_provider_ids": list(result.contributing_provider_ids),
                "dissenting_provider_ids": list(result.dissenting_provider_ids),
            },
        )
        if result.status is ConsensusStatus.CONFLICT:
            self._audit.record(
                event_type="ai_coordination.conflict_detected",
                message=result.reason,
                details={"consensus_id": result.consensus_id},
            )
        return result

    def is_healthy(self) -> bool:
        return True
