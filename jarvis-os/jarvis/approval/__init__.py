"""
jarvis.approval

Sprint-1D: the Approval Engine — the second governance gate, downstream
of the Permission Engine. Tracks ApprovalRequest state (NOT_REQUIRED,
WAITING, APPROVED, REJECTED, EXPIRED) and resolves it into an
ApprovalDecision once the owner confirms or rejects.

Design reference: Article V (Approval Before Consequence), JARVIS-001
§15 (Tier 3 requires an explicit, rendered consequence confirmation, not
a generic "yes" — represented here via ApprovalDecision.confirmation_required).
"""

from jarvis.approval.engine import ApprovalEngine, ApprovalError
from jarvis.approval.models import ApprovalDecision, ApprovalRequest, ApprovalStatus

__all__ = [
    "ApprovalDecision",
    "ApprovalEngine",
    "ApprovalError",
    "ApprovalRequest",
    "ApprovalStatus",
]
