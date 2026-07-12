"""
jarvis.memory.models

Data models shared across the Memory Foundation. Design reference:
Sprint-3 Parts 2-6 (field lists per memory class) and Part 10 (audit
event vocabulary).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional
from uuid import uuid4


def new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class MemoryEventType(str, Enum):
    """Part 10's audit event vocabulary, plus the store-level verbs (created/updated/deleted) each store emits for its own records."""

    MEMORY_CREATED = "memory.created"
    MEMORY_UPDATED = "memory.updated"
    MEMORY_DELETED = "memory.deleted"
    MEMORY_SAVED = "memory.saved"
    MEMORY_LOADED = "memory.loaded"
    RECOVERY_STARTED = "memory.recovery_started"
    RECOVERY_COMPLETED = "memory.recovery_completed"
    RECOVERY_FAILED = "memory.recovery_failed"


class MemoryError(Exception):
    """Base class for all Memory Foundation errors."""


class MemoryValidationError(MemoryError):
    """Raised when a memory record fails validation before it is stored."""


class MemoryAccessError(MemoryError):
    """
    Raised when something outside jarvis.memory attempts to reach storage
    directly instead of going through MemoryManager, or when a memory
    operation is attempted against an uninitialized store.
    """


@dataclass
class WorkingMemorySnapshot:
    """
    Part 2 — Working Memory's field list, captured as a plain snapshot for
    inspection/health/testing. The live WorkingMemory class (working_memory.py)
    holds these as mutable attributes; this dataclass is what `.snapshot()`
    returns — never persisted, since Working Memory is explicitly runtime-only.
    """

    current_task: Optional[dict[str, Any]]
    current_workflow: Optional[dict[str, Any]]
    current_agent: Optional[str]
    pending_approval: Optional[dict[str, Any]]
    temporary_variables: dict[str, Any]
    execution_context: dict[str, Any]


@dataclass
class SessionMemoryRecord:
    """Part 3's persisted session fields, exactly."""

    session_id: str
    session_status: str
    current_task: Optional[dict[str, Any]]
    pending_approval: Optional[dict[str, Any]]
    workflow_position: Optional[dict[str, Any]]
    last_activity: str
    console_state: dict[str, Any]
    recovery_information: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "session_id": self.session_id,
            "session_status": self.session_status,
            "current_task": self.current_task,
            "pending_approval": self.pending_approval,
            "workflow_position": self.workflow_position,
            "last_activity": self.last_activity,
            "console_state": self.console_state,
            "recovery_information": self.recovery_information,
        }

    @staticmethod
    def from_dict(data: dict[str, Any]) -> "SessionMemoryRecord":
        return SessionMemoryRecord(
            session_id=data["session_id"],
            session_status=data["session_status"],
            current_task=data.get("current_task"),
            pending_approval=data.get("pending_approval"),
            workflow_position=data.get("workflow_position"),
            last_activity=data["last_activity"],
            console_state=data.get("console_state", {}),
            recovery_information=data.get("recovery_information", {}),
        )


@dataclass(frozen=True)
class ConversationRecord:
    """Part 4's per-interaction record. Append-only, immutable once written — no summarization, no reasoning applied to it, ever."""

    conversation_id: str
    timestamp: str
    session_id: str
    user_input: str
    intent: Optional[str]
    task_id: Optional[str]
    agent: Optional[str]
    response: Optional[str]
    audit_reference: Optional[str]

    def to_dict(self) -> dict[str, Any]:
        return {
            "conversation_id": self.conversation_id,
            "timestamp": self.timestamp,
            "session_id": self.session_id,
            "user_input": self.user_input,
            "intent": self.intent,
            "task_id": self.task_id,
            "agent": self.agent,
            "response": self.response,
            "audit_reference": self.audit_reference,
        }

    @staticmethod
    def from_dict(data: dict[str, Any]) -> "ConversationRecord":
        return ConversationRecord(
            conversation_id=data["conversation_id"],
            timestamp=data["timestamp"],
            session_id=data["session_id"],
            user_input=data["user_input"],
            intent=data.get("intent"),
            task_id=data.get("task_id"),
            agent=data.get("agent"),
            response=data.get("response"),
            audit_reference=data.get("audit_reference"),
        )

    @staticmethod
    def new(
        session_id: str,
        user_input: str,
        intent: Optional[str] = None,
        task_id: Optional[str] = None,
        agent: Optional[str] = None,
        response: Optional[str] = None,
        audit_reference: Optional[str] = None,
    ) -> "ConversationRecord":
        return ConversationRecord(
            conversation_id=new_id("conv"),
            timestamp=utc_now_iso(),
            session_id=session_id,
            user_input=user_input,
            intent=intent,
            task_id=task_id,
            agent=agent,
            response=response,
            audit_reference=audit_reference,
        )


@dataclass(frozen=True)
class PreferenceRecord:
    """Part 5's owner preference record — one key/value pair, namespaced (e.g. 'language', 'theme', 'notifications.enabled')."""

    key: str
    value: Any
    updated_at: str

    def to_dict(self) -> dict[str, Any]:
        return {"key": self.key, "value": self.value, "updated_at": self.updated_at}

    @staticmethod
    def from_dict(data: dict[str, Any]) -> "PreferenceRecord":
        return PreferenceRecord(key=data["key"], value=data["value"], updated_at=data["updated_at"])
