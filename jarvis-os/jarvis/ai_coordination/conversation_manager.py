"""
jarvis.ai_coordination.conversation_manager

Sprint-5 Part 6 — Conversation Manager.

"No provider communication. Only management." — this module never
imports anything from jarvis.ai_coordination that could dispatch or
validate a response; it only tracks the shape of a conversation
(exchanges, parent requests, follow-ups, history) as pure bookkeeping.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

from jarvis.audit import AuditLedger
from jarvis.ai_coordination.models import ConversationRecord, Exchange


class ConversationError(Exception):
    """Raised for any invalid Conversation Manager operation (unknown conversation_id)."""


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class ConversationManager:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger
        self._conversations: dict[str, ConversationRecord] = {}

    def start(self, conversation_id: str, parent_request: Optional[str] = None) -> ConversationRecord:
        if conversation_id in self._conversations:
            return self._conversations[conversation_id]  # idempotent: a session's conversation_id may already exist
        now = _utc_now_iso()
        record = ConversationRecord(
            conversation_id=conversation_id,
            parent_request=parent_request,
            exchanges=[],
            created_at=now,
            updated_at=now,
        )
        self._conversations[conversation_id] = record
        return record

    def add_exchange(self, conversation_id: str, session_id: str, prompt_id: str, response_id: Optional[str] = None) -> Exchange:
        record = self._require(conversation_id)
        exchange = Exchange(
            exchange_id=_new_id("exchange"),
            session_id=session_id,
            prompt_id=prompt_id,
            response_id=response_id,
            timestamp=_utc_now_iso(),
        )
        record.exchanges.append(exchange)
        record.updated_at = _utc_now_iso()
        return exchange

    def link_follow_up(self, conversation_id: str, follow_up_conversation_id: str) -> ConversationRecord:
        """Registers `follow_up_conversation_id` as a new conversation whose parent_request points back at `conversation_id` — the mechanism Part 6's 'Follow-up Questions' requirement uses to keep a chain traceable without merging conversations together."""
        self._require(conversation_id)
        return self.start(follow_up_conversation_id, parent_request=conversation_id)

    def history(self, conversation_id: str) -> tuple[Exchange, ...]:
        return tuple(self._require(conversation_id).exchanges)

    def get(self, conversation_id: str) -> ConversationRecord:
        return self._require(conversation_id)

    def _require(self, conversation_id: str) -> ConversationRecord:
        try:
            return self._conversations[conversation_id]
        except KeyError as exc:
            raise ConversationError(f"No conversation found with id '{conversation_id}'.") from exc

    def is_healthy(self) -> bool:
        return isinstance(self._conversations, dict)

    def __len__(self) -> int:
        return len(self._conversations)
