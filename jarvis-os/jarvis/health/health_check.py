"""
jarvis.health.health_check

Core self-health check: are the Bootstrap-critical subsystems (Constitution,
Audit Ledger, Agent Registry, Orchestrator) present and in a good state.

Design reference: JARVIS-001 §22, Step 5 of JARVIS-001 §7's Bootstrap
sequence ("run a self-health check covering every dependency touched in
steps 1-4").
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.audit import AuditLedger
from jarvis.constitution import Constitution
from jarvis.orchestrator import Orchestrator
from jarvis.registry import AgentRegistry


@dataclass(frozen=True)
class CoreHealthReport:
    """Result of a full Core self-health check."""

    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Core self-health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_core_health_check(
    constitution: Constitution,
    audit_ledger: AuditLedger,
    registry: AgentRegistry,
    orchestrator: Orchestrator,
) -> CoreHealthReport:
    """
    Run every Sprint-0 health check and aggregate the result.

    Per JARVIS-001 §22, this check should run at the end of Bootstrap
    (Step 5) and be re-runnable on demand thereafter — it takes already-
    constructed subsystem references rather than constructing them itself,
    so it can be called repeatedly against the live, running system.
    """
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    constitution_ok = len(constitution.articles) == 7
    checks["constitution_loaded"] = constitution_ok
    detail["constitution_loaded"] = (
        f"version={constitution.version}, articles={len(constitution.articles)}/7"
    )

    ledger_ok = audit_ledger.is_connected
    checks["audit_ledger_connected"] = ledger_ok
    detail["audit_ledger_connected"] = (
        "connected" if ledger_ok else "NOT CONNECTED — fatal per JARVIS-001 §7"
    )

    # Registry health at Sprint-0 scope: the Registry object exists and is
    # queryable. An empty Registry (zero agents) is healthy — Sprint-0
    # deliberately registers no concrete agents. A registry health check
    # in a later sprint should also verify no agent is stuck in an
    # unreviewed state indefinitely (JARVIS-001 §7 Step 4) — not yet
    # meaningful with zero agents registered.
    registry_ok = isinstance(len(registry), int)
    checks["agent_registry_queryable"] = registry_ok
    detail["agent_registry_queryable"] = f"{len(registry)} agent(s) registered"

    orchestrator_health = orchestrator.health_check()
    checks["orchestrator_pipeline_wired"] = orchestrator_health.healthy
    detail["orchestrator_pipeline_wired"] = orchestrator_health.detail

    overall_healthy = all(checks.values())
    return CoreHealthReport(healthy=overall_healthy, checks=checks, detail=detail)
