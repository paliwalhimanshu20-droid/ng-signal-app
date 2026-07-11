"""
jarvis.interface.approval_interface

ApprovalInterface: the owner-facing surface for an in-progress approval —
builds the display content (reason, tier, risk) and, for Tier 3, the
exact confirmation phrase the owner must retype.

This is NOT a new engine. Per this sprint's Part 4 ("This sprint
completes the Approval Engine"), the actual state machine remains
jarvis.approval.ApprovalEngine, untouched from Sprint-1D — this class
only renders that engine's WAITING state for a human and derives the
one new piece of information the console needs: what exact phrase a
Tier 3 confirmation requires.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from jarvis.intake.models import Task
from jarvis.orchestrator.task_planner import Tier

_RISK_LABELS: dict[Tier, str] = {
    Tier.TIER_0_INFORMATIONAL: "none",
    Tier.TIER_1_REVERSIBLE_LOW_STAKES: "low",
    Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE: "moderate",
    Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES: "critical",
}


@dataclass(frozen=True)
class ApprovalPrompt:
    """Everything the console needs to display and validate one pending approval."""

    approval_id: str
    reason: str
    tier: Tier
    risk: str
    requires_confirmation_phrase: bool
    confirmation_phrase: Optional[str]


class ApprovalInterface:
    """Builds an ApprovalPrompt from a Task currently at WAITING_APPROVAL."""

    def build_prompt(self, task: Task) -> ApprovalPrompt:
        approval_request = task.metadata.get("approval_request", {})
        tier = task.tier
        requires_confirmation = tier is Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES

        return ApprovalPrompt(
            approval_id=approval_request.get("approval_id", ""),
            reason=approval_request.get("reason", ""),
            tier=tier,
            risk=_RISK_LABELS.get(tier, "unknown"),
            requires_confirmation_phrase=requires_confirmation,
            confirmation_phrase=self.confirmation_phrase_for(task) if requires_confirmation else None,
        )

    @staticmethod
    def confirmation_phrase_for(task: Task) -> str:
        """
        The exact phrase a Tier 3 confirmation requires: the owner's
        original request, upper-cased. This directly matches this
        sprint's own example ("Delete production database" ->
        "DELETE PRODUCTION DATABASE") without hardcoding any specific
        phrase — the rule is general, not a lookup table of one entry.
        """
        return task.intent.raw_input.strip().upper()
