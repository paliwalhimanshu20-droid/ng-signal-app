"""
jarvis.agents.models

Sprint-1B execution-related models: ExecutionResult (structured output of
every agent execution, per this sprint's explicit requirement — "never
return raw strings"), AgentHealthStatus (structured health(), replacing
Sprint-0's bare-bool placeholder concept), and ExecutionStatus.

Design reference: JARVIS-002 §24 (Decision Framework — every agent output
carries structured, gradeable fields, never an opaque string), Article
III (a result's evidence/warnings/errors are explicit fields, not folded
into a free-text message).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any
from uuid import uuid4


class ExecutionStatus(str, Enum):
    SUCCESS = "success"
    FAILED = "failed"


@dataclass(frozen=True)
class AgentHealthStatus:
    """Structured health result every BaseAgent.health() call returns."""

    healthy: bool
    detail: str


@dataclass(frozen=True)
class ExecutionResult:
    """
    Structured output of a single agent's execute() call.

    Every field is mandatory. `evidence`, `warnings`, and `errors` are
    kept as three separate tuples rather than folded into `message` —
    per Article III, a human-readable summary and the discrete evidence
    behind it are different things, and collapsing them loses exactly the
    traceability JARVIS-002 §22's Evidence Framework depends on.

    Frozen and self-validating: __post_init__ rejects structurally invalid
    results (empty result_id/message, negative execution_time) at
    construction time rather than letting an invalid result silently
    propagate into a Task's audit trail.
    """

    result_id: str
    status: ExecutionStatus
    message: str
    executed_by: str
    execution_time: float
    evidence: tuple[str, ...]
    warnings: tuple[str, ...]
    errors: tuple[str, ...]
    metadata: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.result_id or not self.result_id.strip():
            raise ValueError("ExecutionResult.result_id must not be empty.")
        if not self.message or not self.message.strip():
            raise ValueError("ExecutionResult.message must not be empty.")
        if not self.executed_by or not self.executed_by.strip():
            raise ValueError("ExecutionResult.executed_by must not be empty.")
        if self.execution_time < 0:
            raise ValueError("ExecutionResult.execution_time must not be negative.")
        if self.status is ExecutionStatus.FAILED and not self.errors:
            raise ValueError(
                "ExecutionResult.status is FAILED but no errors were recorded — "
                "a failure must always carry at least one error, per Article III "
                "(never report a failure without saying why)."
            )

    @staticmethod
    def new(
        status: ExecutionStatus,
        message: str,
        executed_by: str,
        execution_time: float,
        evidence: tuple[str, ...] = (),
        warnings: tuple[str, ...] = (),
        errors: tuple[str, ...] = (),
        metadata: dict[str, Any] | None = None,
    ) -> "ExecutionResult":
        return ExecutionResult(
            result_id=f"result-{uuid4()}",
            status=status,
            message=message,
            executed_by=executed_by,
            execution_time=execution_time,
            evidence=evidence,
            warnings=warnings,
            errors=errors,
            metadata=metadata or {},
        )

    def to_dict(self) -> dict[str, Any]:
        """Serialize to a plain dict — e.g. for audit details or a future API response."""
        return {
            "result_id": self.result_id,
            "status": self.status.value,
            "message": self.message,
            "executed_by": self.executed_by,
            "execution_time": self.execution_time,
            "evidence": list(self.evidence),
            "warnings": list(self.warnings),
            "errors": list(self.errors),
            "metadata": self.metadata,
        }
