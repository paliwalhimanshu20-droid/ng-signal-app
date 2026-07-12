"""
jarvis.intelligence.health

Sprint-4 Part 9 — Intelligence Health.

Matches the shape and philosophy of every prior sprint's health check
(jarvis.health.CoreHealthReport, jarvis.memory.MemoryHealthReport):
healthy / checks / detail / summary().
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class IntelligenceHealthReport:
    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Intelligence Layer health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_intelligence_health_check(engine: "IntelligenceEngine") -> IntelligenceHealthReport:  # type: ignore[name-defined]  # noqa: F821
    """Part 9 requires: Intelligence Engine, Context Builder, Goal Manager, Planning Engine, Decision Engine, Specialist Coordinator, Prompt Builder, Reasoning Pipeline, Overall Health. All nine are checked here."""
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    checks["intelligence_engine"] = engine is not None
    detail["intelligence_engine"] = "constructed"

    checks["context_builder"] = engine.context_builder is not None
    detail["context_builder"] = "present"

    checks["goal_manager"] = engine.goal_manager.is_healthy()
    detail["goal_manager"] = f"{len(engine.goal_manager)} goal(s) tracked"

    checks["planning_engine"] = engine.planning_engine.is_healthy()
    detail["planning_engine"] = "task templates loaded"

    checks["decision_engine"] = engine.decision_engine.is_healthy()
    detail["decision_engine"] = "deterministic rules loaded"

    checks["specialist_coordinator"] = engine.specialist_coordinator.is_healthy()
    detail["specialist_coordinator"] = "domain keyword tables loaded"

    checks["prompt_builder"] = engine.prompt_builder is not None
    detail["prompt_builder"] = "present"

    checks["reasoning_pipeline"] = engine.reasoning_pipeline is not None
    detail["reasoning_pipeline"] = "present"

    overall_healthy = all(checks.values())
    return IntelligenceHealthReport(healthy=overall_healthy, checks=checks, detail=detail)
