"""
jarvis.permission.health

Structural health check for the Permission Engine, matching the shape
and philosophy of every prior sprint's health check.
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.permission.engine import PermissionEngine


@dataclass(frozen=True)
class PermissionEngineHealth:
    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Permission Engine health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_permission_health_check(permission_engine: PermissionEngine) -> PermissionEngineHealth:
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    present = isinstance(permission_engine, PermissionEngine)
    checks["permission_engine_present"] = present
    detail["permission_engine_present"] = "PermissionEngine instance present" if present else "MISSING"

    registry_wired = present and permission_engine._registry is not None
    checks["registry_wired"] = registry_wired
    detail["registry_wired"] = "wired" if registry_wired else "NOT WIRED"

    audit_wired = present and permission_engine._audit_ledger.is_connected
    checks["audit_wired"] = audit_wired
    detail["audit_wired"] = "connected" if audit_wired else "NOT CONNECTED"

    healthy = all(checks.values())
    return PermissionEngineHealth(healthy=healthy, checks=checks, detail=detail)
