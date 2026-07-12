"""
jarvis.intelligence.goal_analyzer

Deterministic classification of a raw owner request into a GoalCategory
— the "Analyze request" half of Part 1's IntelligenceEngine
responsibilities, and the "Analyze Goal" step of Part 8's pipeline.

Same honesty discipline as jarvis.intake.intent_processor: fixed keyword
vocabularies, no semantic understanding claimed, every confidence figure
traceable to which keywords matched. Kept as its OWN vocabulary rather
than calling into IntentProcessor — see jarvis.intelligence.models'
GoalCategory docstring for why that is a deliberate separation, not
duplicated logic.
"""

from __future__ import annotations

from jarvis.intelligence.models import GoalCategory

CONFIDENCE_THRESHOLD = 0.5

CATEGORY_KEYWORDS: dict[GoalCategory, tuple[str, ...]] = {
    GoalCategory.BUILD: ("build", "create", "implement", "develop", "add a", "make a"),
    GoalCategory.RESEARCH: ("research", "compare", "alternative", "study", "look into", "explore options"),
    GoalCategory.INVESTIGATE: ("debug", "diagnose", "troubleshoot", "investigate", "why is"),
    GoalCategory.STATUS_CHECK: ("status", "health check", "is it working"),
    GoalCategory.REVIEW: ("review", "check", "progress", "how is", "look at", "examine"),
}


def classify(normalized_input: str) -> tuple[GoalCategory, float, str]:
    """Returns (category, confidence, reason). Ties resolve to GENERAL with a low, honestly-stated confidence — same fail-honest posture IntentProcessor already established, never a silent best guess."""
    lowered = normalized_input.lower()
    matches: dict[GoalCategory, list[str]] = {}

    for category, keywords in CATEGORY_KEYWORDS.items():
        hits = [kw for kw in keywords if kw in lowered]
        if hits:
            matches[category] = hits

    if not matches:
        return (
            GoalCategory.GENERAL,
            0.2,
            "No recognized goal-category keywords were found; classified as a general goal "
            "pending further clarification.",
        )

    best_count = max(len(hits) for hits in matches.values())
    tied = [cat for cat, hits in matches.items() if len(hits) == best_count]

    if len(tied) > 1:
        names = ", ".join(sorted(c.value for c in tied))
        return (
            GoalCategory.GENERAL,
            0.35,
            f"Input matches keywords for multiple, equally plausible goal categories "
            f"({names}); classified as general pending clarification.",
        )

    category = tied[0]
    hits = matches[category]
    confidence = min(0.6 + 0.1 * min(len(hits), 3), 0.9)
    reason = (
        f"Matched keyword(s) {hits} for goal category '{category.value}'. "
        f"Confidence scales with the number of matching keywords, capped at 0.9 since "
        f"this is deterministic keyword matching, not semantic understanding."
    )
    return category, confidence, reason
