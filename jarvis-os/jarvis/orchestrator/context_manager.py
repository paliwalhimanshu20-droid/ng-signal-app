"""
jarvis.orchestrator.context_manager

Design reference: JARVIS-001 §14 (Context Management), JARVIS-002 §13
(Context Intelligence).

Per §14, context assembled for a task node must be scoped and provenance-
tagged — every fact traceable to whether it came from the current
instruction, Episodic Memory, the Knowledge Store, or a prior task node's
output. Sprint-0 implements the PROVENANCE-TAGGED SHAPE only; there is no
Memory or Knowledge Store yet to actually pull facts from (both are
JARVIS-002 scope, not Sprint-0).
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class ProvenanceSource(str, Enum):
    """Where a piece of context came from, per JARVIS-001 §14's four sources."""

    CURRENT_INSTRUCTION = "current_instruction"
    EPISODIC_MEMORY = "episodic_memory"
    KNOWLEDGE_STORE = "knowledge_store"
    PRIOR_TASK_OUTPUT = "prior_task_output"


@dataclass(frozen=True)
class ContextItem:
    """A single, provenance-tagged fact. Every field is required — untagged context is not permitted by design."""

    content: str
    source: ProvenanceSource


@dataclass(frozen=True)
class AssembledContext:
    """The full, scoped context assembled for one task node."""

    task_id: str
    items: tuple[ContextItem, ...]


class ContextManager:
    """
    Assembles scoped, provenance-tagged context for a task.

    Sprint-0 implementation note: `assemble()` always returns an empty
    context (no items) — there is no Memory or Knowledge Store to draw
    from yet. Returning empty, honestly, is the correct behavior for an
    unimplemented data source, consistent with the same principle already
    applied in intent_processor.py: an unimplemented component must never
    fabricate content just to appear functional.
    """

    def assemble(self, task_id: str) -> AssembledContext:
        return AssembledContext(task_id=task_id, items=())
