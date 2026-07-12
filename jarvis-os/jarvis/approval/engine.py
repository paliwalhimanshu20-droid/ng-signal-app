"""
jarvis.approval.engine

ApprovalEngine: receives a PermissionDecision, determines whether owner
approval is required, creates and tracks an ApprovalRequest, and — via a
separate, explicit confirm() call — resolves it into an ApprovalDecision.

Design reference: this sprint's Approval Engine responsibilities
(receive PermissionDecision, determine approval requirement, create
ApprovalRequest, track approval state, validate owner confirmation,
return ApprovalDecision).

Deliberately split into two calls (evaluate() then confirm()) rather than
one blocking call: this sprint builds no live, interactive owner-input
channel (that's an Interface Layer concern, JARVIS-003 Part III, out of
scope here) — evaluate() can only ever produce WAITING for approval-
required tiers, never block for a real answer. confirm() is the
explicit, separate entry point a future Interface Layer (or, in this
sprint's tests, a direct caller) uses once the owner actually responds.
"""

from __future__ import annotations

from typing import Optional

from jarvis.audit import AuditLedger
from jarvis.intake.models import Task
from jarvis.logging_ import get_logger
from jarvis.orchestrator.task_planner import Tier
from jarvis.permission.models import PermissionDecision
from jarvis.approval.models import ApprovalDecision, ApprovalRequest, ApprovalStatus

logger = get_logger(__name__)

DEFAULT_APPROVAL_TIMEOUT_SECONDS = 86400.0  # 24 hours, configurable per instance — never hardcoded elsewhere.


class ApprovalError(Exception):
    """Raised for any invalid Approval Engine operation (unknown approval_id, confirming a non-WAITING request)."""


class ApprovalEngine:
    """
    Tracks and resolves approval requests.

    In-memory only, matching jarvis.registry.AgentRegistry's own Sprint-0
    precedent (JARVIS-001 §6 statelessness — approvals live for the
    duration of a running Core instance; persistence is a future sprint's
    concern once it's needed, not designed speculatively here).
    """

    def __init__(self, audit_ledger: AuditLedger, default_timeout_seconds: float = DEFAULT_APPROVAL_TIMEOUT_SECONDS) -> None:
        self._audit_ledger = audit_ledger
        self._default_timeout_seconds = default_timeout_seconds
        self._requests: dict[str, ApprovalRequest] = {}

    def evaluate(self, task: Task, permission_decision: PermissionDecision) -> ApprovalRequest:
        if not permission_decision.required_approval:
            request = ApprovalRequest.new(
                task_id=task.task_id,
                tier=permission_decision.required_tier or task.tier,
                reason="Approval not required for this tier.",
                status=ApprovalStatus.NOT_REQUIRED,
            )
            self._requests[request.approval_id] = request
            logger.info("Approval Not Required: task_id=%s", task.task_id)
            self._audit_ledger.record(
                event_type="approval.not_required",
                message="Approval not required for this tier.",
                details={"approval_id": request.approval_id, "task_id": task.task_id, "tier": request.tier.name},
            )
            return request

        confirmation_required = permission_decision.required_tier is Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES
        reason = (
            f"{permission_decision.required_tier.name} requires owner approval"  # type: ignore[union-attr]
            + (" and explicit confirmation." if confirmation_required else ".")
        )
        request = ApprovalRequest.new(
            task_id=task.task_id,
            tier=permission_decision.required_tier,  # type: ignore[arg-type]
            reason=reason,
            status=ApprovalStatus.WAITING,
            timeout_seconds=self._default_timeout_seconds,
        )
        self._requests[request.approval_id] = request
        logger.info(
            "Approval Requested: task_id=%s approval_id=%s confirmation_required=%s",
            task.task_id, request.approval_id, confirmation_required,
        )
        self._audit_ledger.record(
            event_type="approval.requested",
            message=reason,
            details={
                "approval_id": request.approval_id,
                "task_id": task.task_id,
                "tier": request.tier.name,
                "confirmation_required": confirmation_required,
                "expires_at": request.expires_at,
            },
        )
        return request

    def confirm(self, approval_id: str, approved: bool, approved_by: str) -> ApprovalDecision:
        request = self._get_and_expire_if_needed(approval_id)

        if request.status is not ApprovalStatus.WAITING:
            raise ApprovalError(
                f"Cannot confirm approval '{approval_id}': status is "
                f"{request.status.value}, expected {ApprovalStatus.WAITING.value}."
            )

        request.status = ApprovalStatus.APPROVED if approved else ApprovalStatus.REJECTED
        confirmation_required = request.tier is Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES

        if approved:
            logger.info("Approval Granted: approval_id=%s approved_by=%s", approval_id, approved_by)
            event_type = "approval.granted"
        else:
            logger.info("Approval Rejected: approval_id=%s approved_by=%s", approval_id, approved_by)
            event_type = "approval.rejected"

        audit_entry = self._audit_ledger.record(
            event_type=event_type,
            message=f"Approval {'granted' if approved else 'rejected'} by {approved_by}.",
            details={"approval_id": approval_id, "task_id": request.task_id, "approved_by": approved_by},
        )

        return ApprovalDecision.new(
            approved=approved,
            approved_by=approved_by,
            confirmation_required=confirmation_required,
            audit_reference=audit_entry.entry_id,
        )

    def get(self, approval_id: str) -> ApprovalRequest:
        return self._get_and_expire_if_needed(approval_id)

    def register_existing(self, request: ApprovalRequest) -> None:
        """
        SPRINT-3: rehydrate a previously-created ApprovalRequest that
        this engine instance never saw (e.g. this process is a fresh
        boot after a restart) so a subsequent confirm() call can resolve
        it. This is the one concession made to this class's own
        documented "in-memory only ... persistence is a future sprint's
        concern once it's needed" design note above — Sprint-3's Session
        Memory (Part 3, "resume paused workflows") is exactly that future
        need. It never overwrites a request this engine already tracks
        for the same process, and it changes no other method's behavior.
        """
        self._requests.setdefault(request.approval_id, request)

    def _get_and_expire_if_needed(self, approval_id: str) -> ApprovalRequest:
        try:
            request = self._requests[approval_id]
        except KeyError as exc:
            raise ApprovalError(f"No approval request found with id '{approval_id}'.") from exc

        if request.status is ApprovalStatus.WAITING and request.is_expired():
            request.status = ApprovalStatus.EXPIRED
            logger.info("Approval Expired: approval_id=%s", approval_id)
            self._audit_ledger.record(
                event_type="approval.expired",
                message="Approval request expired before the owner responded.",
                details={"approval_id": approval_id, "task_id": request.task_id},
            )
        return request
