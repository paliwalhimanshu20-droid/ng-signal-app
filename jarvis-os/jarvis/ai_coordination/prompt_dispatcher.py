"""
jarvis.ai_coordination.prompt_dispatcher

Sprint-5 Part 7 — Prompt Dispatcher.

Receives a StructuredPrompt (Sprint-4's output) and prepares a
ProviderRequest — a provider-neutral shape naming no specific vendor
format. "Do NOT call external APIs": this module has no network import,
no SDK import, and its single public method returns data, never a
response.
"""

from __future__ import annotations

from datetime import datetime, timezone
from uuid import uuid4

from jarvis.audit import AuditLedger
from jarvis.ai_coordination.models import Capability, ProviderRequest
from jarvis.intelligence.models import StructuredPrompt


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class PromptDispatcher:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger

    def prepare(self, prompt: StructuredPrompt, session_id: str, capability: Capability) -> ProviderRequest:
        request = ProviderRequest(
            request_id=_new_id("preq"),
            session_id=session_id,
            capability=capability,
            prompt_summary=prompt.goal_summary,
            context_references=prompt.memory_references,
            constraints=prompt.constraints,
            prepared_at=_utc_now_iso(),
        )
        self._audit.record(
            event_type="ai_coordination.prompt_prepared",
            message="Provider-neutral request prepared. No external call made.",
            details={"request_id": request.request_id, "session_id": session_id, "capability": capability.value},
        )
        return request

    def is_healthy(self) -> bool:
        return True
