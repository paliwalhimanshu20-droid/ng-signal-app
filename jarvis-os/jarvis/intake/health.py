"""
jarvis.intake.health

Structural health check for the Sprint-1A intake pipeline (IntentProcessor
+ TaskPlanner), matching the shape and philosophy of
jarvis.health.health_check.run_core_health_check (Sprint-0) exactly, so
the two reports can be read side by side.

This is a NEW function, not a modification of jarvis.health — per this
sprint's brief, Sprint-0's health framework is not to be changed. main.py
composes both reports together at the presentation layer instead.
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.intake.intent_processor import IntentProcessor
from jarvis.intake.task_planner import TaskPlanner


@dataclass(frozen=True)
class IntakePipelineHealth:
    """Result of a structural intake-pipeline wiring check. Same shape as jarvis.health.CoreHealthReport, by design."""

    healthy: bool
    checks: dict[str, bool]
    detail: dict[str, str]

    def summary(self) -> str:
        status = "HEALTHY" if self.healthy else "UNHEALTHY"
        lines = [f"Intake pipeline health: {status}"]
        for check_name, passed in self.checks.items():
            mark = "OK" if passed else "FAIL"
            lines.append(f"  [{mark}] {check_name}: {self.detail[check_name]}")
        return "\n".join(lines)


def run_intake_health_check(
    intent_processor: IntentProcessor,
    task_planner: TaskPlanner,
) -> IntakePipelineHealth:
    """
    Sprint-1A scope: verifies both pipeline components are present, of the
    correct type, and wired to a connected Audit Ledger. This mirrors
    Sprint-0's Orchestrator.health_check() philosophy exactly: structural
    wiring only, not adversarial testing (JARVIS-001 §22 vs §30 — the
    latter belongs to a later sprint's test suite).
    """
    checks: dict[str, bool] = {}
    detail: dict[str, str] = {}

    intent_processor_ok = isinstance(intent_processor, IntentProcessor)
    checks["intent_processor_present"] = intent_processor_ok
    detail["intent_processor_present"] = (
        "IntentProcessor instance present" if intent_processor_ok else "MISSING or wrong type"
    )

    task_planner_ok = isinstance(task_planner, TaskPlanner)
    checks["task_planner_present"] = task_planner_ok
    detail["task_planner_present"] = (
        "TaskPlanner instance present" if task_planner_ok else "MISSING or wrong type"
    )

    intent_processor_ledger_ok = (
        intent_processor_ok and intent_processor._audit_ledger.is_connected
    )
    checks["intent_processor_audit_wired"] = intent_processor_ledger_ok
    detail["intent_processor_audit_wired"] = (
        "connected" if intent_processor_ledger_ok else "NOT CONNECTED"
    )

    task_planner_ledger_ok = task_planner_ok and task_planner._audit_ledger.is_connected
    checks["task_planner_audit_wired"] = task_planner_ledger_ok
    detail["task_planner_audit_wired"] = (
        "connected" if task_planner_ledger_ok else "NOT CONNECTED"
    )

    healthy = all(checks.values())
    return IntakePipelineHealth(healthy=healthy, checks=checks, detail=detail)
