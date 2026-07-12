#!/usr/bin/env python3
"""
main.py — JARVIS AI Operating System entry point.

SPRINT-2: this is now a genuine interactive console application. Boots
JARVIS Core (Sprint-0), builds the Executive Kernel (Sprint-2) over the
full pipeline from Sprint-1A through 1D, and hands control to the
Console Interface's real command loop reading from real stdin — no
special "demo mode," no scripted inputs baked into this file. Everything
prior sprints demonstrated by running fixed inputs directly is now
something the owner actually types.

Usage:
    python main.py
        Interactive. Type requests, respond to approvals, "help" for
        commands, "exit" to quit.

    echo -e "Analyze GitHub repository\\nexit" | python main.py
        Non-interactive, scripted via piped stdin — the same code path,
        just fed from a pipe instead of a keyboard. This is how this
        sprint's four Acceptance Scenarios are verified without a real
        terminal.
"""

from __future__ import annotations

import sys

from jarvis.core import BootstrapError, boot
from jarvis.interface.approval_interface import ApprovalInterface
from jarvis.interface.command_parser import CommandParser
from jarvis.interface.console import ConsoleInterface
from jarvis.interface.dashboard import SystemDashboard
from jarvis.interface.renderer import ResponseRenderer
from jarvis.interface.session import SessionManager
from jarvis.kernel import ExecutiveKernel
from jarvis.logging_ import get_logger

logger = get_logger("main")


def run() -> int:
    try:
        core = boot()
    except BootstrapError as exc:
        print(f"BOOTSTRAP FAILED: {exc}", file=sys.stderr)
        print(
            "JARVIS Core did not reach a ready state. Per JARVIS-001 §7, "
            "this is a fatal condition — no partial or degraded startup "
            "is permitted.",
            file=sys.stderr,
        )
        return 1

    health_report = core.health_check()
    if not health_report.healthy:
        print("CORE HEALTH CHECK FAILED — refusing to start the Interface Layer.", file=sys.stderr)
        print(health_report.summary(), file=sys.stderr)
        core.shutdown()
        return 1

    kernel = ExecutiveKernel(constitution=core.constitution, registry=core.registry, audit_ledger=core.audit_ledger)

    console = ConsoleInterface(
        core=core,
        kernel=kernel,
        session_manager=SessionManager(memory_manager=core.memory),
        command_parser=CommandParser(),
        renderer=ResponseRenderer(),
        dashboard=SystemDashboard(),
        approval_interface=ApprovalInterface(),
    )

    exit_code = console.run()

    if core.ready:
        core.shutdown()

    return exit_code


if __name__ == "__main__":
    sys.exit(run())
