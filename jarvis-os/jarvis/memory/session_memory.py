"""
jarvis.memory.session_memory

Sprint-3 Part 3 — Session Memory.

Persists exactly the fields Part 3 lists (session id/status, current
task, pending approval, workflow position, last activity, console state,
recovery information) through a PersistenceLayer. Storage key is fixed
("session") — Sprint-3, like Sprint-2's SessionManager, is still single-
session; a future multi-session sprint would key this by session_id
instead.
"""

from __future__ import annotations

from typing import Optional

from jarvis.memory.models import MemoryValidationError, SessionMemoryRecord, utc_now_iso
from jarvis.memory.persistence import PersistenceLayer

_STORAGE_KEY = "session"


class SessionMemoryStore:
    """Owned exclusively by MemoryManager — see jarvis.memory.memory_manager."""

    def __init__(self, persistence: PersistenceLayer) -> None:
        self._persistence = persistence

    def save(self, record: SessionMemoryRecord) -> None:
        self._validate(record)
        self._persistence.write(_STORAGE_KEY, record.to_dict())

    def load(self) -> Optional[SessionMemoryRecord]:
        """Returns None if nothing has ever been saved, or if the stored record fails integrity checks even after backup fallback."""
        payload, result = self._persistence.read(_STORAGE_KEY)
        if not result.ok or payload is None:
            return None
        return SessionMemoryRecord.from_dict(payload)

    def clear(self) -> None:
        self._persistence.delete(_STORAGE_KEY)

    def touch(self, record: SessionMemoryRecord) -> SessionMemoryRecord:
        record.last_activity = utc_now_iso()
        self.save(record)
        return record

    def is_healthy(self) -> bool:
        payload, result = self._persistence.read(_STORAGE_KEY)
        if payload is None and result.reason == "file_not_found":
            return True  # nothing saved yet is a healthy, valid state
        return result.ok

    @staticmethod
    def _validate(record: SessionMemoryRecord) -> None:
        if not record.session_id:
            raise MemoryValidationError("SessionMemoryRecord.session_id must not be empty.")
        if not record.session_status:
            raise MemoryValidationError("SessionMemoryRecord.session_status must not be empty.")
