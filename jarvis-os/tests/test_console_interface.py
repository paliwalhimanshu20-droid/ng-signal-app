"""
Integration tests for jarvis.interface.console.ConsoleInterface: the full
command loop, driven with scripted input exactly as `main.py` drives it
with real stdin — no special "test mode" exists in the production code,
this is the same `run()` method.
"""

from __future__ import annotations

import pytest

from jarvis.interface.approval_interface import ApprovalInterface
from jarvis.interface.command_parser import CommandParser
from jarvis.interface.console import ConsoleInterface
from jarvis.interface.dashboard import SystemDashboard
from jarvis.interface.renderer import ResponseRenderer
from jarvis.interface.session import SessionManager
from jarvis.kernel import ExecutiveKernel


class ScriptedInput:
    """Feeds a fixed sequence of inputs, like a scripted stdin, and raises EOFError when exhausted."""

    def __init__(self, lines: list[str]):
        self._iter = iter(lines)

    def __call__(self, prompt: str = "") -> str:
        try:
            return next(self._iter)
        except StopIteration:
            raise EOFError


def _build_console(core, lines: list[str]):
    kernel = ExecutiveKernel(constitution=core.constitution, registry=core.registry, audit_ledger=core.audit_ledger)
    output: list[str] = []
    console = ConsoleInterface(
        core=core,
        kernel=kernel,
        session_manager=SessionManager(),
        command_parser=CommandParser(),
        renderer=ResponseRenderer(),
        dashboard=SystemDashboard(),
        approval_interface=ApprovalInterface(),
        input_fn=ScriptedInput(lines),
        output_fn=output.append,
    )
    return console, kernel, output


# --- Acceptance Scenario 1: startup, dashboard, ready ------------------------

def test_startup_shows_dashboard_and_ready(booted_core):
    console, kernel, output = _build_console(booted_core, ["exit"])
    console.run()

    joined = "\n".join(output)
    assert "JARVIS SYSTEM DASHBOARD" in joined
    assert "Ready." in joined
    assert "Goodbye." in joined


# --- Acceptance Scenario 2: Tier 0 request, straight through -----------------

def test_tier0_request_executes_and_returns_to_ready(booted_core):
    console, kernel, output = _build_console(booted_core, ["Analyze GitHub repository", "exit"])
    console.run()

    joined = "\n".join(output)
    assert "SUCCESS" in joined
    assert "Ready for next command." in joined


# --- Acceptance Scenario 3: approval requested, owner approves ---------------

def test_approval_flow_yes_resumes_and_completes(booted_core):
    console, kernel, output = _build_console(
        booted_core,
        ["Deploy production release", "yes", "exit"],
    )
    console.run()

    joined = "\n".join(output)
    assert "APPROVAL REQUIRED" in joined
    assert "APPROVAL GRANTED" in joined or "SUCCESS" in joined


def test_approval_flow_no_rejects(booted_core):
    console, kernel, output = _build_console(
        booted_core,
        ["Deploy production release", "no", "exit"],
    )
    console.run()

    joined = "\n".join(output)
    assert "APPROVAL REQUIRED" in joined
    assert "APPROVAL REJECTED" in joined or "FAILURE" in joined


# --- Acceptance Scenario 4: Tier 3 confirmation phrase -----------------------

def test_confirmation_flow_exact_phrase_resumes_and_completes(booted_core):
    console, kernel, output = _build_console(
        booted_core,
        ["Delete production database", "DELETE PRODUCTION DATABASE", "exit"],
    )
    console.run()

    joined = "\n".join(output)
    assert "APPROVAL REQUIRED" in joined
    assert "Type the exact phrase" in joined
    assert "SUCCESS" in joined


def test_confirmation_flow_cancel_rejects(booted_core):
    console, kernel, output = _build_console(
        booted_core,
        ["Delete production database", "cancel", "exit"],
    )
    console.run()

    joined = "\n".join(output)
    assert "APPROVAL REJECTED" in joined or "FAILURE" in joined


# --- Command handling ---------------------------------------------------------

def test_help_command(booted_core):
    console, kernel, output = _build_console(booted_core, ["help", "exit"])
    console.run()
    assert any("Available commands" in line for line in output)


def test_status_command_shows_dashboard_again(booted_core):
    console, kernel, output = _build_console(booted_core, ["status", "exit"])
    console.run()
    dashboard_occurrences = sum("JARVIS SYSTEM DASHBOARD" in line for line in output)
    assert dashboard_occurrences >= 2  # once at startup, once for `status`


def test_history_command_shows_audit_entries(booted_core):
    console, kernel, output = _build_console(booted_core, ["Analyze GitHub repository", "history", "exit"])
    console.run()
    assert any("AUDIT HISTORY" in line for line in output)


def test_unknown_command_requests_clarification_not_a_guess(booted_core):
    # While awaiting approval, an unrecognized reply must not be silently
    # interpreted as yes or no.
    console, kernel, output = _build_console(
        booted_core,
        ["Deploy production release", "maybe later", "no", "exit"],
    )
    console.run()

    joined = "\n".join(output)
    assert "I didn't understand" in joined


# --- Workflow resume correctness ---------------------------------------------

def test_resume_never_creates_a_new_task(booted_core):
    console, kernel, output = _build_console(
        booted_core,
        ["Deploy production release", "yes", "exit"],
    )
    console.run()

    # The completed task's audit_reference (set at creation) should be
    # traceable through a single, continuous audit trail — not two
    # separate task-creation events for the same request.
    entries = kernel.audit_ledger.read_all()
    task_created_events = [e for e in entries if e.event_type == "task.created"]
    assert len(task_created_events) == 1


# --- Audit: every interaction logged -----------------------------------------

def test_every_interaction_type_is_audited(booted_core):
    console, kernel, output = _build_console(
        booted_core,
        ["Deploy production release", "yes", "exit"],
    )
    console.run()

    event_types = [e.event_type for e in kernel.audit_ledger.read_all()]
    required = (
        "interface.owner_connected",
        "interface.command_received",
        "interface.approval_requested",
        "interface.approval_granted",
        "interface.workflow_resumed",
        "interface.session_closed",
    )
    for event in required:
        assert event in event_types, f"Missing interface audit event: {event}"


def test_rejection_is_audited_distinctly(booted_core):
    console, kernel, output = _build_console(
        booted_core,
        ["Deploy production release", "no", "exit"],
    )
    console.run()

    event_types = [e.event_type for e in kernel.audit_ledger.read_all()]
    assert "interface.approval_rejected" in event_types
