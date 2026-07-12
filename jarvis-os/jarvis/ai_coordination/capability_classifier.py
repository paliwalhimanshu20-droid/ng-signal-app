"""
jarvis.ai_coordination.capability_classifier

Deterministic classification of a StructuredPrompt into a Capability —
the "Select capability" half of Part 1's AICoordinator responsibilities.
Same fixed-keyword-vocabulary discipline as
jarvis.intake.intent_processor and jarvis.intelligence.goal_analyzer, and
deliberately its OWN vocabulary rather than reusing GoalCategory: a Goal
category (Sprint-4) answers "what kind of goal is this," a Capability
(Sprint-5) answers "what kind of AI skill would help with it" — related
but not the same question (a REVIEW goal might need ARCHITECTURE
capability if what's being reviewed is a system design, or REVIEW
capability if it's a code review; the goal category alone doesn't
determine which).
"""

from __future__ import annotations

from jarvis.ai_coordination.models import Capability

CAPABILITY_KEYWORDS: dict[Capability, tuple[str, ...]] = {
    Capability.ARCHITECTURE: ("design", "architecture", "structure", "blueprint"),
    Capability.IMPLEMENTATION: ("implement", "build", "create", "develop", "write code"),
    Capability.RESEARCH: ("research", "compare", "alternative", "study", "look into"),
    Capability.PLANNING: ("plan", "roadmap", "schedule", "break down"),
    Capability.REVIEW: ("review", "evaluate", "assess", "critique"),
    Capability.TESTING: ("test", "verify", "validate the", "qa"),
    Capability.DOCUMENTATION: ("document", "write docs", "readme", "explain"),
    Capability.DEBUGGING: ("debug", "diagnose", "troubleshoot", "fix the bug"),
    Capability.OPTIMIZATION: ("optimize", "speed up", "improve performance", "reduce cost"),
    Capability.TRANSLATION: ("translate", "convert language", "localize"),
}


def classify(text: str) -> tuple[Capability, float, str]:
    """Returns (capability, confidence, reason). Falls back to IMPLEMENTATION with low confidence when nothing matches — a genuinely neutral default (most requests, even unclear ones, tend to end in something being built) rather than an unrepresentable 'no capability' state, but the low confidence keeps that fallback honest."""
    lowered = text.lower()
    matches: dict[Capability, list[str]] = {}

    for capability, keywords in CAPABILITY_KEYWORDS.items():
        hits = [kw for kw in keywords if kw in lowered]
        if hits:
            matches[capability] = hits

    if not matches:
        return (
            Capability.IMPLEMENTATION,
            0.2,
            "No recognized capability keywords were found; defaulting to IMPLEMENTATION "
            "with low confidence pending clarification.",
        )

    best_count = max(len(hits) for hits in matches.values())
    tied = [cap for cap, hits in matches.items() if len(hits) == best_count]

    if len(tied) > 1:
        # Deterministic tie-break: Capability enum declaration order,
        # never arbitrary/random selection among tied candidates.
        ordered = [c for c in Capability if c in tied]
        capability = ordered[0]
        hits = matches[capability]
        reason = (
            f"Multiple capabilities matched equally ({[c.value for c in tied]}); "
            f"resolved deterministically to '{capability.value}' by declaration order."
        )
        confidence = 0.5
        return capability, confidence, reason

    capability = tied[0]
    hits = matches[capability]
    confidence = min(0.6 + 0.1 * min(len(hits), 3), 0.9)
    reason = f"Matched keyword(s) {hits} for capability '{capability.value}'."
    return capability, confidence, reason
