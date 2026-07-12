"""
jarvis.memory.memory_manager

Sprint-3 Part 1 — Memory Manager.

MemoryManager is the ONLY component allowed to access memory storage.
Every other component (Kernel, Interface Layer, Console, agents) must
communicate ONLY through MemoryManager — never construct a
PersistenceLayer, SessionMemoryStore, etc. directly. This is enforced by
convention plus __all__ (jarvis.memory only exports MemoryManager and the
read-only report/record types), the same enforcement style
jarvis.kernel.ExecutiveKernel already uses for "never talk to agents
directly."

Responsibilities per Part 1: store, retrieve, update, delete, validate,
recover, health monitoring — one method (or small method family) each,
below.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Optional

from jarvis.audit import AuditLedger
from jarvis.memory.conversation_memory import ConversationMemoryStore
from jarvis.memory.health import MemoryHealthReport, run_memory_health_check
from jarvis.memory.knowledge_memory import KnowledgeMemoryPlaceholder
from jarvis.memory.models import (
    ConversationRecord,
    MemoryEventType,
    SessionMemoryRecord,
    utc_now_iso,
)
from jarvis.memory.persistence import PersistenceError, PersistenceLayer
from jarvis.memory.preference_memory import PreferenceMemoryStore
from jarvis.memory.recovery import RecoveryManager, RecoveryReport
from jarvis.memory.session_memory import SessionMemoryStore
from jarvis.memory.working_memory import WorkingMemory

__all__ = ["MemoryManager"]


class MemoryManager:
    """
    Constructed once during Bootstrap (mirrors AuditLedger's construction
    pattern exactly): build, then `connect()`, then it's ready for use by
    everything else in the system for the rest of the process lifetime.
    """

    def __init__(self, storage_dir: Path, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger
        self._persistence = PersistenceLayer(storage_dir)
        self._connected = False

        self.working = WorkingMemory()
        self.sessions = SessionMemoryStore(self._persistence)
        self.conversations = ConversationMemoryStore(self._persistence)
        self.preferences = PreferenceMemoryStore(self._persistence)
        self.knowledge = KnowledgeMemoryPlaceholder(self._persistence)

        self._recovery = RecoveryManager(
            session_memory=self.sessions,
            conversation_memory=self.conversations,
            working_memory=self.working,
            audit_ledger=audit_ledger,
        )
        self.last_recovery_report: Optional[RecoveryReport] = None

    @property
    def is_connected(self) -> bool:
        return self._connected

    def connect(self) -> None:
        """Establish persistence connectivity. Must succeed before any other MemoryManager method is used, per PersistenceLayer.connect()'s same verify-by-write guarantee AuditLedger.connect() uses."""
        self._persistence.connect()
        self._connected = True

    # -- store / retrieve / update / delete -----------------------------------
    # These four are deliberately expressed as the specific, typed
    # operations each memory class actually supports (there is no generic
    # untyped store(key, value) surface for Session/Conversation/
    # Preference — each has a real shape, per Parts 3-5) rather than one
    # flattened dict API, so a caller cannot accidentally write a
    # malformed record past validation the way an untyped dict-store
    # would allow.

    def save_session(self, record: SessionMemoryRecord) -> None:
        """Store/update Session Memory (Part 3)."""
        self.sessions.save(record)
        self._audit.record(
            event_type=MemoryEventType.MEMORY_SAVED.value,
            message="Session memory saved.",
            details={"session_id": record.session_id, "status": record.session_status},
        )

    def load_session(self) -> Optional[SessionMemoryRecord]:
        record = self.sessions.load()
        self._audit.record(
            event_type=MemoryEventType.MEMORY_LOADED.value,
            message="Session memory load attempted.",
            details={"found": record is not None},
        )
        return record

    def clear_session(self) -> None:
        self.sessions.clear()
        self.working.clear()
        self._audit.record(
            event_type=MemoryEventType.MEMORY_DELETED.value,
            message="Session memory cleared.",
        )

    def append_conversation(self, record: ConversationRecord) -> None:
        """Store one Conversation Memory record (Part 4). Append-only — there is no update/delete for conversation history, by design."""
        self.conversations.append(record)
        self._audit.record(
            event_type=MemoryEventType.MEMORY_CREATED.value,
            message="Conversation record appended.",
            details={"conversation_id": record.conversation_id, "session_id": record.session_id},
        )

    def set_preference(self, key: str, value: Any) -> None:
        """Store/update Preference Memory (Part 5)."""
        existed = self.preferences.get_record(key) is not None
        self.preferences.set(key, value)
        self._audit.record(
            event_type=(MemoryEventType.MEMORY_UPDATED if existed else MemoryEventType.MEMORY_CREATED).value,
            message=f"Preference '{key}' {'updated' if existed else 'created'}.",
            details={"key": key},
        )

    def get_preference(self, key: str, default: Any = None) -> Any:
        return self.preferences.get(key, default)

    def delete_preference(self, key: str) -> None:
        self.preferences.delete(key)
        self._audit.record(
            event_type=MemoryEventType.MEMORY_DELETED.value,
            message=f"Preference '{key}' deleted.",
            details={"key": key},
        )

    # -- validate --------------------------------------------------------------
    def validate(self) -> dict[str, bool]:
        """Integrity-check every persisted store without loading full payloads back into callers — the Part 1 'validate' responsibility."""
        return {
            "session_memory": self.sessions.is_healthy(),
            "conversation_memory": self.conversations.is_healthy(),
            "preference_memory": self.preferences.is_healthy(),
            "knowledge_memory": self.knowledge.is_healthy(),
        }

    # -- recover -----------------------------------------------------------------
    def recover(self) -> RecoveryReport:
        """Run Part 8's Recovery sequence. Safe to call at most once per process (Bootstrap does this); calling it again re-runs recovery against whatever is currently persisted."""
        report = self._recovery.recover()
        self.last_recovery_report = report
        return report

    # -- health monitoring -------------------------------------------------------
    def health_check(self) -> MemoryHealthReport:
        return run_memory_health_check(self)
