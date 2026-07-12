"""
jarvis.memory

Sprint-3: the Memory Foundation. Public surface is deliberately narrow —
MemoryManager plus the read-only record/report types other layers need
to construct calls or read results. Every other name in this package
(PersistenceLayer, SessionMemoryStore, ConversationMemoryStore, ...) is
an internal collaborator of MemoryManager and must never be imported or
constructed outside jarvis.memory (Part 1: "MemoryManager is the ONLY
component allowed to access memory storage").
"""

from __future__ import annotations

from jarvis.memory.health import MemoryHealthReport, run_memory_health_check
from jarvis.memory.memory_manager import MemoryManager
from jarvis.memory.models import (
    ConversationRecord,
    MemoryAccessError,
    MemoryError,
    MemoryEventType,
    MemoryValidationError,
    PreferenceRecord,
    SessionMemoryRecord,
    WorkingMemorySnapshot,
)
from jarvis.memory.persistence import PersistenceError
from jarvis.memory.recovery import RecoveryReport

__all__ = [
    "ConversationRecord",
    "MemoryAccessError",
    "MemoryError",
    "MemoryEventType",
    "MemoryHealthReport",
    "MemoryManager",
    "MemoryValidationError",
    "PersistenceError",
    "PreferenceRecord",
    "RecoveryReport",
    "SessionMemoryRecord",
    "WorkingMemorySnapshot",
    "run_memory_health_check",
]
