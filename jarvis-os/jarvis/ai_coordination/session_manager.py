"""
jarvis.ai_coordination.session_manager

Sprint-5 Part 5 — AI Session Manager.

Named AISessionManager (not SessionManager) to avoid any confusion with
jarvis.interface.session.SessionManager (Sprint-2's owner console
session) — these track two entirely different kinds of session and must
never be conflated, even though the class shapes rhyme structurally.

In-memory only, same explicit, documented scope decision Sprint-4 made
for GoalManager: Part 5's own "Future-ready for persistent conversations"
phrasing is a forward-looking design property (conversation_id as a
grouping key), not a requirement that this sprint actually persist
sessions.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Optional
from uuid import uuid4

from jarvis.audit import AuditLedger
from jarvis.ai_coordination.models import AISession, AISessionStatus, ProviderResponse
from jarvis.intelligence.models import StructuredPrompt


class AISessionError(Exception):
    """Raised for any invalid AI Session operation (unknown session_id, illegal status transition)."""


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class AISessionManager:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger
        self._sessions: dict[str, AISession] = {}

    def create_session(
        self,
        prompt: StructuredPrompt,
        conversation_id: Optional[str] = None,
        context: Optional[dict[str, Any]] = None,
    ) -> AISession:
        now = _utc_now_iso()
        session = AISession(
            session_id=_new_id("aisession"),
            conversation_id=conversation_id or _new_id("conversation"),
            provider_id=None,
            prompt=prompt,
            response=None,
            context=dict(context or {}),
            retry_count=0,
            status=AISessionStatus.CREATED,
            created_at=now,
            updated_at=now,
        )
        self._sessions[session.session_id] = session

        self._audit.record(
            event_type="ai_coordination.session_created",
            message="AI session created.",
            details={"session_id": session.session_id, "conversation_id": session.conversation_id},
        )
        return session

    def get(self, session_id: str) -> AISession:
        try:
            return self._sessions[session_id]
        except KeyError as exc:
            raise AISessionError(f"No AI session found with id '{session_id}'.") from exc

    def assign_provider(self, session_id: str, provider_id: str) -> AISession:
        session = self.get(session_id)
        session.provider_id = provider_id
        session.updated_at = _utc_now_iso()
        return session

    def set_status(self, session_id: str, status: AISessionStatus) -> AISession:
        session = self.get(session_id)
        session.status = status
        session.updated_at = _utc_now_iso()
        return session

    def record_response(self, session_id: str, response: ProviderResponse) -> AISession:
        session = self.get(session_id)
        session.response = response
        session.status = AISessionStatus.RESPONDED
        session.updated_at = _utc_now_iso()
        return session

    def increment_retry(self, session_id: str) -> AISession:
        session = self.get(session_id)
        session.retry_count += 1
        session.status = AISessionStatus.RETRYING
        session.updated_at = _utc_now_iso()
        return session

    def fail(self, session_id: str, reason: str) -> AISession:
        session = self.get(session_id)
        session.status = AISessionStatus.FAILED
        session.updated_at = _utc_now_iso()
        self._audit.record(
            event_type="ai_coordination.session_failed",
            message=reason,
            details={"session_id": session_id, "retry_count": session.retry_count},
        )
        return session

    def list_by_conversation(self, conversation_id: str) -> tuple[AISession, ...]:
        return tuple(s for s in self._sessions.values() if s.conversation_id == conversation_id)

    def is_healthy(self) -> bool:
        return isinstance(self._sessions, dict)

    def __len__(self) -> int:
        return len(self._sessions)
