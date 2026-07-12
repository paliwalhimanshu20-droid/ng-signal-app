"""
jarvis.memory.recovery

Sprint-3 Part 8 — Recovery.

On startup: load the previous session and restore Pending Approval,
Workflow State, Current Task, Conversation, and Working Memory from it.
If recovery fails, generate an audit entry, report the failure, and
continue safely — a failed recovery is never fatal to Bootstrap the way
a failed Constitution or Audit Ledger load is (JARVIS-001 §7 Steps 1-2),
because Memory Foundation's job is continuity, not constitutional
grounding; losing continuity is degraded, not unsafe.

Never auto-approve, never auto-execute: recovery only rehydrates state
into Working Memory. Whether a recovered `pending_approval` gets acted on
is entirely up to the Interface Layer showing it to the owner again and
waiting for a real response — this module contains no code path that
could resolve an approval or resume execution on its own.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Optional

from jarvis.audit import AuditLedger
from jarvis.memory.models import MemoryEventType
from jarvis.memory.session_memory import SessionMemoryStore
from jarvis.memory.working_memory import WorkingMemory

if TYPE_CHECKING:
    from jarvis.memory.conversation_memory import ConversationMemoryStore


@dataclass(frozen=True)
class RecoveryReport:
    """Outcome of one recovery attempt, always produced — a report is generated whether recovery succeeds or fails (Part 8)."""

    succeeded: bool
    session_restored: bool
    session_id: Optional[str]
    pending_approval_restored: bool
    current_task_restored: bool
    conversation_records_found: int
    failure_reason: Optional[str]

    def summary(self) -> str:
        if not self.succeeded:
            return f"Recovery FAILED: {self.failure_reason}. Continuing with a fresh session."
        if not self.session_restored:
            return "Recovery: no previous session found. Starting fresh."
        parts = [f"Recovery OK: session {self.session_id} restored."]
        if self.current_task_restored:
            parts.append("Current task restored.")
        if self.pending_approval_restored:
            parts.append("Pending approval restored — awaiting owner response, nothing was auto-approved.")
        parts.append(f"{self.conversation_records_found} conversation record(s) available.")
        return " ".join(parts)


class RecoveryManager:
    """Owned exclusively by MemoryManager."""

    def __init__(
        self,
        session_memory: SessionMemoryStore,
        conversation_memory: "ConversationMemoryStore",
        working_memory: WorkingMemory,
        audit_ledger: AuditLedger,
    ) -> None:
        self._sessions = session_memory
        self._conversations = conversation_memory
        self._working = working_memory
        self._audit = audit_ledger

    def recover(self) -> RecoveryReport:
        self._audit.record(
            event_type=MemoryEventType.RECOVERY_STARTED.value,
            message="Memory recovery started.",
        )

        try:
            record = self._sessions.load()
        except Exception as exc:  # noqa: BLE001 - recovery must never crash Bootstrap
            report = RecoveryReport(
                succeeded=False,
                session_restored=False,
                session_id=None,
                pending_approval_restored=False,
                current_task_restored=False,
                conversation_records_found=0,
                failure_reason=str(exc),
            )
            self._audit.record(
                event_type=MemoryEventType.RECOVERY_FAILED.value,
                message="Memory recovery failed; continuing with a fresh session.",
                details={"reason": report.failure_reason},
            )
            return report

        if record is None:
            report = RecoveryReport(
                succeeded=True,
                session_restored=False,
                session_id=None,
                pending_approval_restored=False,
                current_task_restored=False,
                conversation_records_found=0,
                failure_reason=None,
            )
            self._audit.record(
                event_type=MemoryEventType.RECOVERY_COMPLETED.value,
                message="Memory recovery completed: no previous session found.",
            )
            return report

        # Restore Working Memory from the persisted Session record. This
        # rehydrates state only — it triggers no approval or execution
        # logic (see module docstring).
        self._working.set_current_task(record.current_task)
        self._working.set_pending_approval(record.pending_approval)
        self._working.set_current_workflow(record.workflow_position)

        conversation_count = len(self._conversations.by_session(record.session_id))

        report = RecoveryReport(
            succeeded=True,
            session_restored=True,
            session_id=record.session_id,
            pending_approval_restored=record.pending_approval is not None,
            current_task_restored=record.current_task is not None,
            conversation_records_found=conversation_count,
            failure_reason=None,
        )
        self._audit.record(
            event_type=MemoryEventType.RECOVERY_COMPLETED.value,
            message="Memory recovery completed: previous session restored.",
            details={
                "session_id": record.session_id,
                "pending_approval_restored": report.pending_approval_restored,
                "current_task_restored": report.current_task_restored,
                "conversation_records_found": conversation_count,
            },
        )
        return report
