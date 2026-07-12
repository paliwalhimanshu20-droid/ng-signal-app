"""
jarvis.intelligence.context_builder

Sprint-4 Part 2 — Context Builder.

Collects context from every memory tier Sprint-3 built (Working, Session,
Conversation, Preference, Knowledge) plus the live Current Task/Workflow/
Approval already tracked in Working Memory. Talks to memory EXCLUSIVELY
through jarvis.memory.MemoryManager's public surface — never constructs
or reaches into a PersistenceLayer directly, honoring Sprint-3's "Memory
Manager is the ONLY entry point" rule from the caller side.

"Never fabricate context": every field is either the real value found in
Memory, or an honest empty/None. There is no default-filling, no
inference, and no synthesis of a plausible-looking value anywhere in
this module.
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intelligence.models import Context, new_id, utc_now_iso
from jarvis.memory import MemoryManager

DEFAULT_CONVERSATION_LIMIT = 5


class ContextBuilder:
    def __init__(self, memory_manager: MemoryManager, audit_ledger: AuditLedger) -> None:
        self._memory = memory_manager
        self._audit = audit_ledger

    def build(self, conversation_limit: int = DEFAULT_CONVERSATION_LIMIT) -> Context:
        working_snapshot = self._memory.working.snapshot()
        session_record = self._memory.load_session()
        conversation_records = self._memory.conversations.history(limit=conversation_limit)

        context = Context(
            context_id=new_id("context"),
            built_at=utc_now_iso(),
            working_memory={
                "current_agent": working_snapshot.current_agent,
                "temporary_variables": working_snapshot.temporary_variables,
                "execution_context": working_snapshot.execution_context,
            },
            conversation_history=tuple(record.to_dict() for record in conversation_records),
            preferences=self._memory.preferences.all(),
            knowledge=self._memory.knowledge.structure(),
            session=session_record.to_dict() if session_record is not None else None,
            current_task=working_snapshot.current_task,
            current_workflow=working_snapshot.current_workflow,
            current_approval=working_snapshot.pending_approval,
        )

        self._audit.record(
            event_type="intelligence.context_built",
            message="Context assembled from Memory Foundation.",
            details={
                "context_id": context.context_id,
                "conversation_records": len(context.conversation_history),
                "has_current_task": context.current_task is not None,
                "has_pending_approval": context.current_approval is not None,
                "preference_count": len(context.preferences),
            },
        )
        return context
