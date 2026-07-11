#!/usr/bin/env python3
"""
main.py — JARVIS AI Operating System, Sprint-0 entry point.

Boots JARVIS Core via jarvis.core.boot(), prints a health report, and
exits cleanly. This is intentionally the ONLY thing Sprint-0's main.py
does — per the Sprint-0 scope, there is no request loop, no voice/chat
interface, and no agent activity to run yet. A future sprint replaces the
"boot, report, shut down" body below with a real, long-lived request loop
once the Orchestrator's request lifecycle (JARVIS-001 §9) is actually
implemented end-to-end.

Usage:
    python main.py
    python main.py "Analyze GitHub repository"

SPRINT-1A ADDENDUM (added without modifying anything above this note):
after Sprint-0's boot/health-report sequence completes, and before
shutdown, main.py now also runs one input through the Sprint-1A Core Task
Pipeline (jarvis.intake) — Intent Processing -> Task Planning -> a
READY_FOR_ROUTING (or PLANNING-held, if ambiguous) Task. This demonstrates
the acceptance scenario end-to-end. No routing or agent execution occurs;
jarvis.intake explicitly stops at "ready for routing," per Sprint-1A's
scope.
"""

from __future__ import annotations

import sys

from jarvis.core import BootstrapError, boot
from jarvis.intake import IntentProcessor, TaskPlanner
from jarvis.intake.health import run_intake_health_check
from jarvis.intake.models import TaskStatus
from jarvis.logging_ import get_logger

logger = get_logger("main")

DEFAULT_DEMO_INPUT = "Analyze GitHub repository"


def run() -> int:
    """
    Boot JARVIS Core, report health, shut down cleanly.

    Returns a process exit code: 0 if Bootstrap succeeded and the system
    is healthy, 1 otherwise. A non-zero exit code here is the correct,
    fail-closed behavior per JARVIS-001 §3 Principle 4 — Sprint-0 must
    never report success if Bootstrap or the health check did not
    genuinely pass.
    """
    print("=" * 70)
    print("JARVIS AI Operating System — Sprint-0: Foundation Bootstrap")
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
    print(f"Agents registered: {len(core.registry)}")
    print(f"Ready: {core.ready}")

    # --- Sprint-1A: Core Task Pipeline demonstration -------------------------
    # Uses the SAME AuditLedger instance Bootstrap already established
    # (core.audit_ledger) — jarvis.intake never opens its own ledger
    # connection, consistent with Article IV's single, authoritative
    # Ledger per running Core instance.
    demo_input = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DEMO_INPUT

    print("\n" + "=" * 70)
    print("Sprint-1A: Core Task Pipeline Foundation")
    print("=" * 70)
    print(f"Input: {demo_input!r}\n")

    intent_processor = IntentProcessor(audit_ledger=core.audit_ledger)
    task_planner = TaskPlanner(audit_ledger=core.audit_ledger)

    intake_health = run_intake_health_check(intent_processor, task_planner)
    print(intake_health.summary())
    print()

    intent = intent_processor.process(demo_input)
    task = task_planner.plan(intent)

    print(f"\nIntent type:          {intent.intent_type.value}")
    print(f"Confidence:            {intent.confidence:.2f} ({intent.confidence_reason})")
    print(f"Ambiguous:             {intent.is_ambiguous}")
    print(f"Detected entities:     {intent.detected_entities}")
    print(f"\nTask ID:               {task.task_id}")
    print(f"Task status:           {task.status.value}")
    print(f"Priority:              {task.priority.value}")
    print(f"Tier:                  {task.tier.name}")
    if task.execution_plan:
        print(f"Execution strategy:    {task.execution_plan.strategy}")
        print(f"Candidate agents:      {task.execution_plan.candidate_agents or '(none identified)'}")
        print(f"Approval required:     {task.execution_plan.approval_required}")
    print(f"Audit reference:       {task.audit_reference}")

    if task.status is TaskStatus.READY_FOR_ROUTING:
        print("\nTask reached READY_FOR_ROUTING. No routing or execution occurs in Sprint-1A.")
    else:
        print(
            f"\nTask held at {task.status.value} — clarification required before "
            "this request can be planned further. This is expected, honest "
            "behavior for an ambiguous input, not a failure."
        )

    core.shutdown()
    print("\nJARVIS Core shut down cleanly. Sprint-0 run complete.")

    return 0 if health_report.healthy else 1


if __name__ == "__main__":
    sys.exit(run())
