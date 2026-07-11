"""
jarvis.interface.session

SessionManager: tracks the single active owner session, per this
sprint's explicit "one active session only" requirement.

Design reference: JARVIS-003 §30 (Conversation Model), §36 (Multi-Device
Experience — explicitly out of scope here; this sprint is single-session,
single-console, by design).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Optional
from uuid import uuid4

from jarvis.intake.models import Task


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

    def __init__(self, idle_timeout_seconds: float = 300.0) -> None:
        self._session: Optional[Session] = None
        self._idle_timeout_seconds = idle_timeout_seconds

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
        return self._session

    def close_session(self) -> None:
        if self._session is None:
            raise SessionError("No active session to close.")
        self._session.status = SessionStatus.CLOSED

    def reset_session(self) -> Session:
        """Close (if open) and immediately reopen a fresh session, clearing any current_task."""
        if self._session is not None and self._session.status is not SessionStatus.CLOSED:
            self._session.status = SessionStatus.CLOSED
        return self.open_session()

    def touch(self) -> None:
        """Record activity, resetting idle status back to OPEN if it had gone IDLE."""
        session = self._require_active_session()
        session.last_activity = _utc_now().isoformat()
        if session.status is SessionStatus.IDLE:
            session.status = SessionStatus.OPEN

    def check_idle(self) -> bool:
        """
        Evaluate (and, if crossed, apply) idle status against the
        configured timeout. Returns whether the session is currently idle.
        """
        session = self._require_active_session()
        elapsed = (_utc_now() - datetime.fromisoformat(session.last_activity)).total_seconds()
        if elapsed > self._idle_timeout_seconds:
            session.status = SessionStatus.IDLE
        return session.status is SessionStatus.IDLE

    def set_current_task(self, task: Optional[Task]) -> None:
        session = self._require_active_session()
        session.current_task = task

    def get_current_task(self) -> Optional[Task]:
        session = self._require_active_session()
        return session.current_task

    def _require_active_session(self) -> Session:
        if self._session is None or self._session.status is SessionStatus.CLOSED:
            raise SessionError("No active session. Call open_session() first.")
        return self._session
