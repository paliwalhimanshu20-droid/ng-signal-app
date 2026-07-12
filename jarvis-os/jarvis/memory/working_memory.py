"""
jarvis.memory.working_memory

Sprint-3 Part 2 — Working Memory.

Deliberately the simplest store in the Foundation: an in-process, never-
persisted holder for runtime state (current task, current workflow,
current agent, pending approval, temporary variables, execution context).
"Automatically cleared when appropriate" is implemented as `clear()`,
called by MemoryManager at the points JARVIS-003's session lifecycle
already defines as clearing points (session close/reset) — Working
Memory itself has no timer or background logic, matching SessionManager's
existing "checked on demand, not by a background thread" precedent.
"""

from __future__ import annotations

from typing import Any, Optional

from jarvis.memory.models import WorkingMemorySnapshot


class WorkingMemory:
    """Runtime-only state. No disk I/O anywhere in this class — that absence is the point."""

    def __init__(self) -> None:
        self._current_task: Optional[dict[str, Any]] = None
        self._current_workflow: Optional[dict[str, Any]] = None
        self._current_agent: Optional[str] = None
        self._pending_approval: Optional[dict[str, Any]] = None
        self._temporary_variables: dict[str, Any] = {}
        self._execution_context: dict[str, Any] = {}

    # -- current task ----------------------------------------------------
    def set_current_task(self, task: Optional[dict[str, Any]]) -> None:
        self._current_task = task

    def get_current_task(self) -> Optional[dict[str, Any]]:
        return self._current_task

    # -- current workflow --------------------------------------------------
    def set_current_workflow(self, workflow: Optional[dict[str, Any]]) -> None:
        self._current_workflow = workflow

    def get_current_workflow(self) -> Optional[dict[str, Any]]:
        return self._current_workflow

    # -- current agent -----------------------------------------------------
    def set_current_agent(self, agent_id: Optional[str]) -> None:
        self._current_agent = agent_id

    def get_current_agent(self) -> Optional[str]:
        return self._current_agent

    # -- pending approval ----------------------------------------------------
    def set_pending_approval(self, approval: Optional[dict[str, Any]]) -> None:
        self._pending_approval = approval

    def get_pending_approval(self) -> Optional[dict[str, Any]]:
        return self._pending_approval

    # -- temporary variables -------------------------------------------------
    def set_variable(self, name: str, value: Any) -> None:
        self._temporary_variables[name] = value

    def get_variable(self, name: str, default: Any = None) -> Any:
        return self._temporary_variables.get(name, default)

    def delete_variable(self, name: str) -> None:
        self._temporary_variables.pop(name, None)

    # -- execution context ------------------------------------------------
    def set_execution_context(self, context: dict[str, Any]) -> None:
        self._execution_context = dict(context)

    def get_execution_context(self) -> dict[str, Any]:
        return dict(self._execution_context)

    # -- lifecycle -----------------------------------------------------------
    def clear(self) -> None:
        """Reset every field. Called on session close/reset — Part 2's 'automatically cleared when appropriate'."""
        self._current_task = None
        self._current_workflow = None
        self._current_agent = None
        self._pending_approval = None
        self._temporary_variables = {}
        self._execution_context = {}

    def snapshot(self) -> WorkingMemorySnapshot:
        return WorkingMemorySnapshot(
            current_task=self._current_task,
            current_workflow=self._current_workflow,
            current_agent=self._current_agent,
            pending_approval=self._pending_approval,
            temporary_variables=dict(self._temporary_variables),
            execution_context=dict(self._execution_context),
        )

    def is_healthy(self) -> bool:
        """Working Memory is always structurally healthy once constructed — no I/O to fail. Kept as a method (not a constant) so MemoryHealthReport's shape is uniform across all six stores."""
        return True
