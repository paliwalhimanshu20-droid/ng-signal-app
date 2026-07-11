"""
jarvis.orchestrator.intent_processor

Design reference: JARVIS-001 §10 (Intent Processing).

Per §10, a real Intent Processor must produce three outputs for every
owner input: a structured interpretation, a confidence score for that
interpretation, and an explicit ambiguity flag when confidence is
insufficient to proceed. Sprint-0 implements the shape only — see
`process()` below.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class StructuredIntent:
    """
    Placeholder shape for a processed owner instruction.

    A real implementation populates `confidence` from the (not-yet-built)
    Confidence Framework (JARVIS-002 §23) and sets `is_ambiguous` per
    JARVIS-001 §10's tunable, tier-aware thresholds. Sprint-0 always
    returns a maximally uncertain, ambiguous result — see IntentProcessor
    docstring for why this default is deliberate, not lazy.
    """

    raw_input: str
    interpretation: str | None
    confidence: float
    is_ambiguous: bool


class IntentProcessor:
    """
    Converts raw owner input into a StructuredIntent.

    Sprint-0 implementation note: `process()` always returns an ambiguous,
    zero-confidence result. This is intentional, not a stub oversight —
    per JARVIS-001 §10 and Article III, an Intent Processor that cannot
    yet genuinely interpret input must never fabricate a confident
    interpretation just to look functional. Returning "I don't understand
    this yet" honestly is the constitutionally correct behavior for an
    unimplemented component, not a failure state to be hidden.
    """

    def process(self, raw_input: str) -> StructuredIntent:
        return StructuredIntent(
            raw_input=raw_input,
            interpretation=None,
            confidence=0.0,
            is_ambiguous=True,
        )
