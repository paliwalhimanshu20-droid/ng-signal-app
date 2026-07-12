"""
jarvis.memory.conversation_memory

Sprint-3 Part 4 — Conversation Memory.

Every interaction, append-only, via PersistenceLayer.append_line() —
same JSON-Lines shape and same "no summarization, no reasoning" guarantee
as the Audit Ledger. Supports append, retrieve-by-id, full history, and
filtering (by session_id, task_id, agent) — nothing more; any synthesis
of this data (summaries, embeddings, semantic search) is explicitly out
of Sprint-3 scope per the sprint brief.
"""

from __future__ import annotations

from typing import Callable, Optional

from jarvis.memory.models import ConversationRecord, MemoryValidationError
from jarvis.memory.persistence import PersistenceLayer

_STORAGE_KEY = "conversation"


class ConversationMemoryStore:
    """Owned exclusively by MemoryManager."""

    def __init__(self, persistence: PersistenceLayer) -> None:
        self._persistence = persistence

    def append(self, record: ConversationRecord) -> None:
        self._validate(record)
        self._persistence.append_line(_STORAGE_KEY, record.to_dict())

    def history(self, limit: Optional[int] = None) -> list[ConversationRecord]:
        """Full conversation history in append order, optionally capped to the most recent `limit` records."""
        records = [ConversationRecord.from_dict(raw) for raw in self._persistence.read_lines(_STORAGE_KEY)]
        if limit is not None:
            return records[-limit:]
        return records

    def get(self, conversation_id: str) -> Optional[ConversationRecord]:
        for record in self.history():
            if record.conversation_id == conversation_id:
                return record
        return None

    def filter(self, predicate: Callable[[ConversationRecord], bool]) -> list[ConversationRecord]:
        return [record for record in self.history() if predicate(record)]

    def by_session(self, session_id: str) -> list[ConversationRecord]:
        return self.filter(lambda r: r.session_id == session_id)

    def by_task(self, task_id: str) -> list[ConversationRecord]:
        return self.filter(lambda r: r.task_id == task_id)

    def is_healthy(self) -> bool:
        # Append-only log: healthy means readable without raising. A
        # corrupt individual line is already tolerated (skipped) by
        # PersistenceLayer.read_lines(), so reaching this point at all
        # is the health signal.
        try:
            self._persistence.read_lines(_STORAGE_KEY)
            return True
        except Exception:
            return False

    @staticmethod
    def _validate(record: ConversationRecord) -> None:
        if not record.session_id:
            raise MemoryValidationError("ConversationRecord.session_id must not be empty.")
        if not record.user_input:
            raise MemoryValidationError("ConversationRecord.user_input must not be empty.")
