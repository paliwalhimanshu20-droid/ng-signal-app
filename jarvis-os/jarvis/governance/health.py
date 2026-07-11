"""
jarvis.governance.health

The composite "Governance Layer" health check: verifies the full
Permission -> Approval -> Workflow chain is wired together end to end,
distinct from (and composed from) the individual Permission Engine and
Approval Engine health checks.
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.approval.engine import ApprovalEngine
from jarvis.execution.workflow import TaskExecutionWorkflow
from jarvis.permission.engine import PermissionEngine


@dataclass(frozen=True)
class GovernanceLayerHealth:
    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Governance Layer health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_governance_health_check(
    permission_engine: PermissionEngine,
    approval_engine: ApprovalEngine,
    workflow: TaskExecutionWorkflow,
) -> GovernanceLayerHealth:
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    workflow_wired_to_permission = workflow._permission_engine is permission_engine
    checks["workflow_wired_to_permission_engine"] = workflow_wired_to_permission
    detail["workflow_wired_to_permission_engine"] = (
        "wired" if workflow_wired_to_permission else "MISMATCHED OR MISSING"
    )

    workflow_wired_to_approval = workflow._approval_engine is approval_engine
    checks["workflow_wired_to_approval_engine"] = workflow_wired_to_approval
    detail["workflow_wired_to_approval_engine"] = (
        "wired" if workflow_wired_to_approval else "MISMATCHED OR MISSING"
    )

    same_audit_ledger = (
        permission_engine._audit_ledger is approval_engine._audit_ledger
    )
    checks["single_audit_ledger_across_governance"] = same_audit_ledger
    detail["single_audit_ledger_across_governance"] = (
        "single, shared Audit Ledger" if same_audit_ledger
        else "MISMATCH: Permission and Approval engines use different Ledgers"
    )

    healthy = all(checks.values())
    return GovernanceLayerHealth(healthy=healthy, checks=checks, detail=detail)
