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
"""

from __future__ import annotations

import sys

from jarvis.core import BootstrapError, boot
from jarvis.logging_ import get_logger

logger = get_logger("main")


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

    core.shutdown()
    print("\nJARVIS Core shut down cleanly. Sprint-0 run complete.")

    return 0 if health_report.healthy else 1


if __name__ == "__main__":
    sys.exit(run())
