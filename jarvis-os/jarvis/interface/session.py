"""
jarvis.interface.session

SessionManager: tracks the single active owner session, per this
sprint's explicit "one active session only" requirement.

Design reference: JARVIS-003 §30 (Conversation Model), §36 (Multi-Device
Experience — explicitly out of scope here; this sprint is single-session,
single-console, by design).

SPRINT-3 UPDATE: SessionManager now optionally persists every session
mutation through a MemoryManager (Session Memory, Part 3), and can
rehydrate a live Session from a recovered SessionMemoryRecord
(`resume_session()`) instead of always starting fresh. This is additive
— constructing a SessionManager with no MemoryManager (as every prior
sprint's tests already do) behaves exactly as before: purely in-memory,
nothing persisted. Only main.py's real boot path passes a MemoryManager.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from typing import TYPE_CHECKING, Optional
from uuid import uuid4

from jarvis.intake.models import Task

if TYPE_CHECKING:
    from jarvis.memory import MemoryManager, SessionMemoryRecord


def _new_id() -> str:
    return f"session-{uuid4()}"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


class SessionStatus(str, Enum):
    OPEN = "open"
    IDLE = "idle"
    CLOSED = "closed"


class SessionError(Exception):
    """Raised for any invalid session operation (opening a second session, acting on a closed one)."""


@dataclass
class Session:
    """
    A single owner session.

    `current_task` holds the live Task object a paused workflow is
    waiting on, if any — the exact mechanism that lets the Interface
    Layer resume "the" paused workflow when the owner responds, per this
    sprint's Part 5 requirement ("resume exactly where execution
    paused," never a new Task).
    """

    session_id: str
    created_at: str
    last_activity: str
    current_task: Optional[Task]
    status: SessionStatus


class SessionManager:
    """
    Manages the single active session's full lifecycle: open, close,
    idle detection, reset.

    Idle detection is checked on demand (`check_idle()`), not enforced by
    a background timer — a console REPL blocks on `input()` between
    commands, so there is no moment for a background thread to act on
    idleness anyway; checking at the start of each loop iteration is the
    honest, correctly-scoped implementation for a synchronous console
    interface (see jarvis.interface.console).
    """

    def __init__(self, memory_manager: Optional["MemoryManager"] = None, idle_timeout_seconds: float = 300.0) -> None:
        self._session: Optional[Session] = None
        self._idle_timeout_seconds = idle_timeout_seconds
        self._memory = memory_manager

    @property
    def current(self) -> Optional[Session]:
        return self._session

    def open_session(self) -> Session:
        if self._session is not None and self._session.status is not SessionStatus.CLOSED:
            raise SessionError(
                "A session is already active. Per this sprint's requirement, only "
                "one active session is permitted at a time — close it first."
            )
        now = _utc_now().isoformat()
        self._session = Session(
            session_id=_new_id(),
            created_at=now,
            last_activity=now,
            current_task=None,
            status=SessionStatus.OPEN,
        )
        self._persist()
        return self._session

    def resume_session(self, record: "SessionMemoryRecord") -> Session:
        """
        Sprint-3 Part 8: rebuild a live Session from a recovered
        SessionMemoryRecord instead of starting fresh — same session_id,
        same current_task (if any, restored via task_serialization),
        status reset to OPEN (a restarted process picking the session
        back up IS the owner reconnecting, per JARVIS-003 §30 — the prior
        status only reflected what was true at the moment of the last
        save, not a decision that continuity should end there).

        A restored current_task's own status (e.g. WAITING_APPROVAL) is
        left exactly as it was — this call restores state, it never
        advances or resolves it (Part 8: never auto-approve, never
        auto-execute).
        """
        if self._session is not None and self._session.status is not SessionStatus.CLOSED:
            raise SessionError("A session is already active; cannot resume over it.")

        current_task: Optional[Task] = None
        if record.current_task is not None:
            from jarvis.interface.task_serialization import task_from_dict

            current_task = task_from_dict(record.current_task)

        self._session = Session(
            session_id=record.session_id,
            created_at=record.console_state.get("created_at", record.last_activity),
            last_activity=_utc_now().isoformat(),
            current_task=current_task,
            status=SessionStatus.OPEN,
        )
        self._persist()
        return self._session

    def close_session(self) -> None:
        if self._session is None:
            raise SessionError("No active session to close.")
        self._session.status = SessionStatus.CLOSED
        self._persist()

    def reset_session(self) -> Session:
        """Close (if open) and immediately reopen a fresh session, clearing any current_task."""
        if self._session is not None and self._session.status is not SessionStatus.CLOSED:
            self._session.status = SessionStatus.CLOSED
        if self._memory is not None:
            self._memory.clear_session()
        return self.open_session()

    def touch(self) -> None:
        """Record activity, resetting idle status back to OPEN if it had gone IDLE."""
        session = self._require_active_session()
        session.last_activity = _utc_now().isoformat()
        if session.status is SessionStatus.IDLE:
            session.status = SessionStatus.OPEN
        self._persist()

    def check_idle(self) -> bool:
        """
        Evaluate (and, if crossed, apply) idle status against the
        configured timeout. Returns whether the session is currently idle.
        """
        session = self._require_active_session()
        elapsed = (_utc_now() - datetime.fromisoformat(session.last_activity)).total_seconds()
        if elapsed > self._idle_timeout_seconds:
            session.status = SessionStatus.IDLE
            self._persist()
        return session.status is SessionStatus.IDLE

    def set_current_task(self, task: Optional[Task]) -> None:
        session = self._require_active_session()
        session.current_task = task
        self._persist()

    def get_current_task(self) -> Optional[Task]:
        session = self._require_active_session()
        return session.current_task

    def _require_active_session(self) -> Session:
        if self._session is None or self._session.status is SessionStatus.CLOSED:
            raise SessionError("No active session. Call open_session() first.")
        return self._session

    def _persist(self) -> None:
        """Save the live Session to Memory (Part 3), if a MemoryManager was wired in. No-op otherwise — see module docstring."""
        if self._memory is None or self._session is None:
            return

        from jarvis.memory import SessionMemoryRecord

        current_task_dict = None
        pending_approval = None
        if self._session.current_task is not None:
            from jarvis.interface.task_serialization import task_to_dict

            current_task_dict = task_to_dict(self._session.current_task)
            if self._session.current_task.status.value == "waiting_approval":
                pending_approval = self._session.current_task.metadata.get("approval_request")

        record = SessionMemoryRecord(
            session_id=self._session.session_id,
            session_status=self._session.status.value,
            current_task=current_task_dict,
            pending_approval=pending_approval,
            workflow_position={"task_status": self._session.current_task.status.value}
            if self._session.current_task is not None
            else None,
            last_activity=self._session.last_activity,
            console_state={"created_at": self._session.created_at},
        )
        self._memory.save_session(record)
