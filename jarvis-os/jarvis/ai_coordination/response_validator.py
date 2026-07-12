"""
jarvis.ai_coordination.response_validator

Sprint-5 Part 8 — Response Validator.

Validates a ProviderResponse's structure, completeness, confidence, and
internal consistency, deterministically — every flag is traceable to a
specific, named check, never a vague "looks wrong." This module never
constructs a ProviderResponse itself and never talks to a provider; it
only judges responses handed to it (by tests today, by a future real
adapter eventually).
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.ai_coordination.models import ProviderResponse, ValidationFlag, ValidationResult

LOW_CONFIDENCE_THRESHOLD = 0.4
INCOMPLETE_THRESHOLD = 0.6


class ResponseValidator:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger

    def validate(self, response: ProviderResponse) -> ValidationResult:
        flags: list[ValidationFlag] = []
        reasons: list[str] = []

        if not response.recommended_action or not response.content:
            flags.append(ValidationFlag.INVALID_STRUCTURE)
            reasons.append("Missing required field(s): recommended_action and/or content.")

        if not (0.0 <= response.confidence <= 1.0):
            flags.append(ValidationFlag.INVALID_STRUCTURE)
            reasons.append(f"confidence {response.confidence} is outside the valid [0.0, 1.0] range.")

        if response.completeness < INCOMPLETE_THRESHOLD:
            flags.append(ValidationFlag.INCOMPLETE)
            reasons.append(f"completeness {response.completeness:.2f} is below the {INCOMPLETE_THRESHOLD} threshold.")

        if response.confidence < LOW_CONFIDENCE_THRESHOLD:
            flags.append(ValidationFlag.LOW_CONFIDENCE)
            reasons.append(f"confidence {response.confidence:.2f} is below the {LOW_CONFIDENCE_THRESHOLD} threshold.")

        # Consistency check: a response claiming high confidence while
        # also reporting itself as substantially incomplete is
        # internally inconsistent — a provider adapter shouldn't be able
        # to claim near-certainty about an answer it also says is
        # unfinished.
        if response.confidence >= 0.8 and response.completeness < 0.5:
            flags.append(ValidationFlag.INCONSISTENT)
            reasons.append(
                f"confidence ({response.confidence:.2f}) is high but completeness "
                f"({response.completeness:.2f}) is low — internally inconsistent."
            )

        valid = ValidationFlag.INVALID_STRUCTURE not in flags
        reason = "; ".join(reasons) if reasons else "No issues found."

        result = ValidationResult(response_id=response.response_id, valid=valid, flags=tuple(flags), reason=reason)
        self._audit.record(
            event_type="ai_coordination.response_validated",
            message=f"Response '{response.response_id}' validated.",
            details={"response_id": response.response_id, "valid": valid, "flags": [f.value for f in flags]},
        )
        return result

    def is_healthy(self) -> bool:
        return True
