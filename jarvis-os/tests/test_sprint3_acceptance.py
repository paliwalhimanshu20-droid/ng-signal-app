"""
Sprint-3 Acceptance Scenarios 1-4, run as real end-to-end tests: two
independent `boot()` calls sharing the same on-disk storage directory,
simulating a real process restart (as opposed to unit tests against
MemoryManager alone, already covered in test_memory_manager.py).
"""

from __future__ import annotations

import json

import pytest

from jarvis.constitution.loader import REQUIRED_ARTICLE_IDS
from jarvis.core.bootstrap import boot
from jarvis.interface.approval_interface import ApprovalInterface
from jarvis.interface.command_parser import CommandParser
from jarvis.interface.console import ConsoleInterface
from jarvis.interface.dashboard import SystemDashboard
from jarvis.interface.renderer import ResponseRenderer
from jarvis.interface.session import SessionManager
from jarvis.kernel import ExecutiveKernel


class ScriptedInput:
    def __init__(self, lines: list[str]):
        self._iter = iter(lines)

    def __call__(self, prompt: str = "") -> str:
        try:
            return next(self._iter)
        except StopIteration:
            raise EOFError


@pytest.fixture()
def restartable_env(tmp_path, monkeypatch):
    """Sets up the env vars every boot() call in this test will share, WITHOUT booting yet — each test controls its own boot()/shutdown() sequence to simulate restarts."""
    constitution_path = tmp_path / "constitution.json"
    constitution_path.write_text(
        json.dumps(
            {
                "constitution_version": "1.0.0",
                "articles": [
                    {"id": article_id, "name": f"Article {article_id}", "summary": "Test."}
                    for article_id in REQUIRED_ARTICLE_IDS
                ],
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setenv("JARVIS_CONSTITUTION_PATH", str(constitution_path))
    monkeypatch.setenv("JARVIS_REGISTRY_STATE_PATH", str(tmp_path / "registry_state.json"))
    monkeypatch.setenv("JARVIS_MEMORY_STORAGE_PATH", str(tmp_path / "memory"))
    monkeypatch.chdir(tmp_path)
    return tmp_path


def _run_console(core, lines: list[str]) -> tuple[list[str], object]:
    kernel = ExecutiveKernel(constitution=core.constitution, registry=core.registry, audit_ledger=core.audit_ledger)
    output: list[str] = []
    console = ConsoleInterface(
        core=core,
        kernel=kernel,
        session_manager=SessionManager(memory_manager=core.memory),
        command_parser=CommandParser(),
        renderer=ResponseRenderer(),
        dashboard=SystemDashboard(),
        approval_interface=ApprovalInterface(),
        input_fn=ScriptedInput(lines),
        output_fn=output.append,
    )
    console.run()
    return output, kernel


# --- Acceptance Scenario 1: start, create task, exit, restart, session restored ----


def test_scenario_1_session_restored_after_restart(restartable_env):
    core1 = boot()
    output1, _ = _run_console(core1, ["Analyze GitHub repository", "exit"])
    session_id_1 = None
    for blob in output1:
        for line in blob.splitlines():
            if line.strip().startswith("Session:"):
                session_id_1 = line.split()[1]
                break
        if session_id_1:
            break
    core1.shutdown()

    core2 = boot()
    assert core2.recovery_report is not None
    assert core2.recovery_report.session_restored
    assert core2.recovery_report.session_id == session_id_1

    output2, _ = _run_console(core2, ["exit"])
    assert any("MEMORY RECOVERY" in line for line in output2)
    core2.shutdown()


# --- Acceptance Scenario 2: approval requested, terminate, restart, still waiting --


def test_scenario_2_pending_approval_survives_restart_then_resumes(restartable_env):
    core1 = boot()
    # Simulate the process being TERMINATED, not gracefully exited: no
    # "exit" is sent, so no clean close_session() runs — this is the
    # crash case Part 8 is actually meant to cover.
    output1, _ = _run_console(core1, ["Deploy production release"])
    assert any("APPROVAL REQUIRED" in line for line in output1)
    # No core1.shutdown() — deliberately, to simulate a kill -9.

    core2 = boot()
    assert core2.recovery_report.session_restored
    assert core2.recovery_report.pending_approval_restored

    output2, _ = _run_console(core2, ["yes", "exit"])
    joined = "\n".join(output2)
    assert "APPROVAL GRANTED" in joined or "SUCCESS" in joined
    core2.shutdown()


# --- Acceptance Scenario 3: conversation restored across restart -------------------


def test_scenario_3_conversation_restored_after_restart(restartable_env):
    core1 = boot()
    _run_console(core1, ["hello", "Analyze GitHub repository", "exit"])
    core1.shutdown()

    core2 = boot()
    output2, _ = _run_console(core2, ["conversation", "exit"])
    joined = "\n".join(output2)
    assert "CONVERSATION HISTORY" in joined
    assert "hello" in joined
    assert "Analyze GitHub repository" in joined
    core2.shutdown()


# --- Acceptance Scenario 4: preference persists across restart ---------------------


def test_scenario_4_preference_persists_after_restart(restartable_env):
    core1 = boot()
    core1.memory.set_preference("interface.theme", "dark")
    core1.shutdown()

    core2 = boot()
    assert core2.memory.get_preference("interface.theme") == "dark"
    core2.shutdown()


# --- Never restart a completed task -------------------------------------------------


def test_completed_task_is_not_reexecuted_on_restart(restartable_env):
    core1 = boot()
    _run_console(core1, ["Analyze GitHub repository", "exit"])
    core1.shutdown()

    core2 = boot()
    kernel2 = ExecutiveKernel(constitution=core2.constitution, registry=core2.registry, audit_ledger=core2.audit_ledger)
    # A restored COMPLETED task must never be silently re-submitted for
    # execution — Recovery only rehydrates Working Memory, it never
    # calls kernel.submit_request() or kernel.resume() on its own. The
    # audit trail shares one continuous Ledger across both boots (same
    # storage path), so exactly one task.created event must exist total
    # — not a second one created by Recovery/restart itself.
    task_created_events = [e for e in core2.audit_ledger.read_all() if e.event_type == "task.created"]
    assert len(task_created_events) == 1
    core2.shutdown()
