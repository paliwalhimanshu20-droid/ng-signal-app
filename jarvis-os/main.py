#!/usr/bin/env python3
"""
main.py — JARVIS AI Operating System entry point.

Boots JARVIS Core (Sprint-0), then demonstrates the full pipeline —
Intake (Sprint-1A) -> Routing & Execution (Sprint-1B) -> Permission &
Approval governance (Sprint-1C/1D) — against all three of this sprint's
Acceptance Scenarios in a single run, before shutting down cleanly.

Usage:
    python main.py
    python main.py "some other Tier 0 request"   (overrides Scenario 1's input only)

SPRINT-1A/1B ADDENDA: see prior versions of this file's history for the
Core Task Pipeline and Routing/Execution sections — both are unchanged in
behavior, only reorganized here into a reusable helper so all three
Sprint-1C/1D acceptance scenarios can run in one pass without duplicating
the pipeline-wiring logic (this sprint's own "no duplicated logic"
requirement, applied to this file as much as to the library code).

SPRINT-1C/1D ADDENDUM: every task now also passes through the Permission
Engine and Approval Engine before execution. Scenario 1 (Tier 0) sails
through untouched. Scenarios 2 and 3 (Tier 2/3) correctly STOP at
WAITING_APPROVAL — no execution occurs, per Article V — demonstrating
that governance actually blocks, not just logs.
"""

from __future__ import annotations

import sys

from jarvis.agents.engineering_agent import EngineeringAgent
from jarvis.approval import ApprovalEngine
from jarvis.core import BootstrapError, boot
from jarvis.execution import TaskExecutionWorkflow
from jarvis.execution.health import run_execution_health_check
from jarvis.governance import run_governance_health_check
from jarvis.intake import IntentProcessor, TaskPlanner
from jarvis.intake.health import run_intake_health_check
from jarvis.intake.models import Task, TaskStatus
from jarvis.logging_ import get_logger
from jarvis.permission import PermissionEngine
from jarvis.permission.health import run_permission_health_check
from jarvis.approval.health import run_approval_health_check
from jarvis.registry import AgentLifecycleState, AgentRecord, AgentRegistry
from jarvis.routing import TaskRouter

logger = get_logger("main")

DEFAULT_SCENARIO_1_INPUT = "Analyze GitHub repository"
SCENARIO_2_INPUT = "Deploy production release"
SCENARIO_3_INPUT = "Delete production database"


def _process_one_request(core, workflow, intent_processor, task_planner, label: str, raw_input: str) -> Task | None:
    """Run one input all the way through Intake, then Routing/Permission/Approval/Execution. Returns the final Task, or None if it never left PLANNING."""
    print("\n" + "-" * 70)
    print(f"{label}: {raw_input!r}")
    print("-" * 70)

    intent = intent_processor.process(raw_input)
    task = task_planner.plan(intent)

    print(f"Intent type:       {intent.intent_type.value}")
    print(f"Tier:              {task.tier.name}")
    print(f"Task status:       {task.status.value}")

    if task.status is not TaskStatus.READY_FOR_ROUTING:
        print(f"Held at {task.status.value} — clarification required, not routed this run.")
        return None

    result = workflow.execute(task)

    permission = result.metadata.get("permission_decision", {})
    approval_request = result.metadata.get("approval_request", {})
    execution_result = result.metadata.get("execution_result")

    print(f"Permission allowed: {permission.get('allowed')}  (required_approval={permission.get('required_approval')})")
    print(f"Approval status:    {approval_request.get('status')}")
    if execution_result:
        print(f"Execution status:   {execution_result.get('status')}")
        print(f"Execution message:  {execution_result.get('message')}")
    else:
        print("Execution status:   (not executed — awaiting approval, or blocked)")
    print(f"Final task status:  {result.status.value}")

    return result


def run() -> int:
    print("=" * 70)
    print("JARVIS AI Operating System — Foundation Bootstrap")
    print("=" * 70)

    try:
        core = boot()
    except BootstrapError as exc:
        print(f"\nBOOTSTRAP FAILED: {exc}", file=sys.stderr)
        print(
            "\nJARVIS Core did not reach a ready state. Per JARVIS-001 §7, "
            "this is a fatal condition — no partial or degraded startup "
            "is permitted.",
            file=sys.stderr,
        )
        return 1

    print("\nJARVIS Core booted successfully.\n")

    health_report = core.health_check()
    print(health_report.summary())
    print(f"\nConstitution version: {core.constitution.version}")
    print(f"Ready: {core.ready}")

    # --- Wire the full pipeline, once ------------------------------------------
    intent_processor = IntentProcessor(audit_ledger=core.audit_ledger)
    task_planner = TaskPlanner(audit_ledger=core.audit_ledger)

    engineering_agent = EngineeringAgent()
    core.registry.register(
        AgentRecord(
            agent_id=engineering_agent.agent_id,
            domain=engineering_agent.domain,
            parent_domain=None,
            capabilities=engineering_agent.capabilities(),
            version=engineering_agent.version(),
            instance=engineering_agent,
        )
    )
    core.registry.transition(engineering_agent.agent_id, AgentLifecycleState.REVIEWED)
    core.registry.transition(engineering_agent.agent_id, AgentLifecycleState.PROVISIONED)
    core.registry.transition(engineering_agent.agent_id, AgentLifecycleState.ACTIVE)

    router = TaskRouter(registry=core.registry, audit_ledger=core.audit_ledger)
    permission_engine = PermissionEngine(registry=core.registry, audit_ledger=core.audit_ledger)
    approval_engine = ApprovalEngine(audit_ledger=core.audit_ledger)
    workflow = TaskExecutionWorkflow(
        router=router,
        registry=core.registry,
        audit_ledger=core.audit_ledger,
        permission_engine=permission_engine,
        approval_engine=approval_engine,
    )

    # --- Full Health Dashboard, per this sprint's explicit requirement ---------
    print("\n" + "=" * 70)
    print("Health Dashboard")
    print("=" * 70)
    print(run_intake_health_check(intent_processor, task_planner).summary())
    print()
    print(run_execution_health_check(core.registry, router, workflow).summary())
    print()
    print(run_permission_health_check(permission_engine).summary())
    print()
    print(run_approval_health_check(approval_engine).summary())
    print()
    print(run_governance_health_check(permission_engine, approval_engine, workflow).summary())

    # --- Acceptance Scenario 1: Tier 0, no approval, straight through ----------
    scenario_1_input = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SCENARIO_1_INPUT
    _process_one_request(core, workflow, intent_processor, task_planner, "Scenario 1", scenario_1_input)

    # --- Acceptance Scenario 2: approval requested, execution blocked ----------
    scenario_2_task = _process_one_request(core, workflow, intent_processor, task_planner, "Scenario 2", SCENARIO_2_INPUT)
    if scenario_2_task is not None and scenario_2_task.status is TaskStatus.WAITING_APPROVAL:
        print("No execution occurred — task is correctly WAITING for owner approval.")

    # --- Acceptance Scenario 3: Tier 3, confirmation required, blocked ---------
    scenario_3_task = _process_one_request(core, workflow, intent_processor, task_planner, "Scenario 3", SCENARIO_3_INPUT)
    if scenario_3_task is not None and scenario_3_task.status is TaskStatus.WAITING_APPROVAL:
        approval_info = scenario_3_task.metadata.get("approval_request", {})
        print(f"Execution blocked until explicit confirmation. Reason: {approval_info.get('reason')}")

    print("\nStructured Response Returned.")

    core.shutdown()
    print("\nJARVIS Core shut down cleanly. Run complete.")

    return 0 if health_report.healthy else 1


if __name__ == "__main__":
    sys.exit(run())
