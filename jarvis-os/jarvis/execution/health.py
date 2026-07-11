"""
jarvis.execution.health

Structural health check for the Sprint-1B execution layer: Agent
Registry, Router, Workflow Engine, and the Engineering Agent specifically
— per this sprint's explicit "Health dashboard must now include" list.

Matches the shape and philosophy of jarvis.health.run_core_health_check
(Sprint-0) and jarvis.intake.health.run_intake_health_check (Sprint-1A)
exactly, so all three reports read consistently side by side. A new
function, not a modification of either — same reasoning as both prior
sprints' health checks.
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.execution.workflow import TaskExecutionWorkflow
from jarvis.registry import AgentRegistry
from jarvis.routing import TaskRouter


@dataclass(frozen=True)
class ExecutionLayerHealth:
    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Execution layer health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_execution_health_check(
    registry: AgentRegistry,
    router: TaskRouter,
    workflow: TaskExecutionWorkflow,
) -> ExecutionLayerHealth:
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    registry_ok = isinstance(registry, AgentRegistry)
    checks["agent_registry_present"] = registry_ok
    detail["agent_registry_present"] = "AgentRegistry instance present" if registry_ok else "MISSING"

    router_ok = isinstance(router, TaskRouter)
    checks["router_present"] = router_ok
    detail["router_present"] = "TaskRouter instance present" if router_ok else "MISSING"

    workflow_ok = isinstance(workflow, TaskExecutionWorkflow)
    checks["workflow_engine_present"] = workflow_ok
    detail["workflow_engine_present"] = (
        "TaskExecutionWorkflow instance present" if workflow_ok else "MISSING"
    )

    engineering_records = registry.lookup_by_capability("engineering") if registry_ok else ()
    engineering_registered = len(engineering_records) > 0
    checks["engineering_agent_registered"] = engineering_registered
    detail["engineering_agent_registered"] = (
        f"{len(engineering_records)} agent(s) with 'engineering' capability, ACTIVE"
        if engineering_registered
        else "No ACTIVE agent declares the 'engineering' capability"
    )

    if engineering_registered:
        first = engineering_records[0]
        agent_health = registry.health_status(first.agent_id)
        checks["engineering_agent_healthy"] = agent_health.healthy
        detail["engineering_agent_healthy"] = agent_health.detail
    else:
        checks["engineering_agent_healthy"] = False
        detail["engineering_agent_healthy"] = "Cannot check health: no engineering agent registered"

    healthy = all(checks.values())
    return ExecutionLayerHealth(healthy=healthy, checks=checks, detail=detail)
