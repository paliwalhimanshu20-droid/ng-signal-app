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
from jarvis.ai_coordination import AICoordinator
from jarvis.constitution import Constitution
from jarvis.intelligence import IntelligenceEngine
from jarvis.memory import MemoryManager
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
    memory: "MemoryManager | None" = None,
    intelligence: "IntelligenceEngine | None" = None,
    ai_coordination: "AICoordinator | None" = None,
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

    # SPRINT-3 ADDITION: Memory Foundation health, folded into Core's own
    # report rather than kept separate — Memory is now a Bootstrap-critical
    # subsystem (Part 8's recovery runs during Bootstrap), so it belongs
    # here alongside Constitution/Ledger/Registry/Orchestrator. Optional
    # parameter so every prior sprint's direct call sites (none exist
    # today, per the grep above, but the signature stays backward
    # compatible regardless) keep working without a MemoryManager.
    if memory is not None:
        memory_health = memory.health_check()
        checks["memory_foundation_healthy"] = memory_health.healthy
        detail["memory_foundation_healthy"] = memory_health.summary()

    # SPRINT-4 ADDITION: Intelligence Layer health, same optional-param
    # pattern Sprint-3 used for Memory — added here rather than kept
    # separate so Core's one health report stays the single place every
    # Bootstrap-constructed subsystem is checked.
    if intelligence is not None:
        intelligence_health = intelligence.health_check()
        checks["intelligence_layer_healthy"] = intelligence_health.healthy
        detail["intelligence_layer_healthy"] = intelligence_health.summary()

    # SPRINT-5 ADDITION: AI Coordination Layer health, same optional-param
    # pattern Sprint-3/4 used for Memory/Intelligence.
    if ai_coordination is not None:
        ai_coordination_health = ai_coordination.health_check()
        checks["ai_coordination_layer_healthy"] = ai_coordination_health.healthy
        detail["ai_coordination_layer_healthy"] = ai_coordination_health.summary()

    overall_healthy = all(checks.values())
    return CoreHealthReport(healthy=overall_healthy, checks=checks, detail=detail)
