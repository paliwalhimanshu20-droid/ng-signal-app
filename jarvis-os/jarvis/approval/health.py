"""
jarvis.approval.health

Structural health check for the Approval Engine, matching the shape and
philosophy of every prior sprint's health check.
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.approval.engine import ApprovalEngine


@dataclass(frozen=True)
class ApprovalEngineHealth:
    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Approval Engine health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_approval_health_check(approval_engine: ApprovalEngine) -> ApprovalEngineHealth:
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    present = isinstance(approval_engine, ApprovalEngine)
    checks["approval_engine_present"] = present
    detail["approval_engine_present"] = "ApprovalEngine instance present" if present else "MISSING"

    audit_wired = present and approval_engine._audit_ledger.is_connected
    checks["audit_wired"] = audit_wired
    detail["audit_wired"] = "connected" if audit_wired else "NOT CONNECTED"

    timeout_configured = present and approval_engine._default_timeout_seconds > 0
    checks["timeout_configured"] = timeout_configured
    detail["timeout_configured"] = (
        f"{approval_engine._default_timeout_seconds}s" if timeout_configured else "NOT CONFIGURED"
    )

    healthy = all(checks.values())
    return ApprovalEngineHealth(healthy=healthy, checks=checks, detail=detail)
