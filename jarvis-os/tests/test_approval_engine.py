"""
Tests for jarvis.approval.engine.ApprovalEngine: not required, waiting,
approved, rejected, expired.
"""

from __future__ import annotations

import time

import pytest

from jarvis.approval import ApprovalEngine, ApprovalError, ApprovalStatus
from jarvis.audit import AuditLedger
from jarvis.orchestrator.task_planner import Tier
from jarvis.permission.models import PermissionDecision


@pytest.fixture()
def ledger(tmp_path):
    audit_ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    audit_ledger.connect()
    return audit_ledger


class _FakeTask:
    """Minimal stand-in exposing only what ApprovalEngine.evaluate() needs."""

    def __init__(self, task_id="task-1", tier=Tier.TIER_0_INFORMATIONAL):
        self.task_id = task_id
        self.tier = tier


def _not_required_decision(tier=Tier.TIER_0_INFORMATIONAL) -> PermissionDecision:
    return PermissionDecision.new(allowed=True, reason="ok", required_approval=False, required_tier=tier)


def _approval_required_decision(tier: Tier) -> PermissionDecision:
    return PermissionDecision.new(allowed=True, reason="ok", required_approval=True, required_tier=tier)


def test_approval_not_required(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _not_required_decision())

    assert request.status is ApprovalStatus.NOT_REQUIRED


def test_approval_waiting_for_tier2(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE))

    assert request.status is ApprovalStatus.WAITING
    assert request.expires_at is not None


def test_approval_waiting_for_tier3_reason_mentions_confirmation(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES))

    assert request.status is ApprovalStatus.WAITING
    assert "confirmation" in request.reason.lower()


def test_confirm_approves(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE))

    decision = engine.confirm(request.approval_id, approved=True, approved_by="owner")

    assert decision.approved is True
    assert decision.approved_by == "owner"
    assert engine.get(request.approval_id).status is ApprovalStatus.APPROVED


def test_confirm_rejects(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE))

    decision = engine.confirm(request.approval_id, approved=False, approved_by="owner")

    assert decision.approved is False
    assert engine.get(request.approval_id).status is ApprovalStatus.REJECTED


def test_tier3_decision_flags_confirmation_required(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES))

    decision = engine.confirm(request.approval_id, approved=True, approved_by="owner")

    assert decision.confirmation_required is True


def test_tier2_decision_does_not_flag_confirmation_required(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE))

    decision = engine.confirm(request.approval_id, approved=True, approved_by="owner")

    assert decision.confirmation_required is False


def test_confirming_twice_raises(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE))
    engine.confirm(request.approval_id, approved=True, approved_by="owner")

    with pytest.raises(ApprovalError):
        engine.confirm(request.approval_id, approved=True, approved_by="owner")


def test_confirming_unknown_id_raises(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    with pytest.raises(ApprovalError):
        engine.confirm("nonexistent-approval-id", approved=True, approved_by="owner")


def test_expired_request_cannot_be_confirmed(ledger):
    engine = ApprovalEngine(audit_ledger=ledger, default_timeout_seconds=0.05)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE))

    time.sleep(0.1)

    with pytest.raises(ApprovalError):
        engine.confirm(request.approval_id, approved=True, approved_by="owner")

    assert engine.get(request.approval_id).status is ApprovalStatus.EXPIRED


def test_expiry_is_audited(ledger):
    engine = ApprovalEngine(audit_ledger=ledger, default_timeout_seconds=0.05)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE))
    time.sleep(0.1)
    engine.get(request.approval_id)  # triggers lazy expiry check

    event_types = [e.event_type for e in ledger.read_all()]
    assert "approval.expired" in event_types


def test_full_audit_trail_for_approval_lifecycle(ledger):
    engine = ApprovalEngine(audit_ledger=ledger)
    request = engine.evaluate(_FakeTask(), _approval_required_decision(Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE))
    engine.confirm(request.approval_id, approved=True, approved_by="owner")

    event_types = [e.event_type for e in ledger.read_all()]
    assert "approval.requested" in event_types
    assert "approval.granted" in event_types
