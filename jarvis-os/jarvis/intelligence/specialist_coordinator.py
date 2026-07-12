"""
jarvis.intelligence.specialist_coordinator

Sprint-4 Part 6 — Specialist Coordinator.

Deterministically decides WHO (which domain) should perform a goal —
never calls anyone, never routes a live task. This is deliberately a
different concern from jarvis.routing.TaskRouter (Sprint-1B): the Router
matches an already-tiered Task against the live Agent Registry to find a
real, ACTIVE agent instance to execute against; the Specialist
Coordinator matches a Goal against a fixed domain vocabulary to advise
which specialist a future prompt/recommendation should be aimed at, long
before anything is routed or executed. Reusing TaskRouter here would
conflate "who could route this today" with "who should conceptually own
this" — the latter is meaningful even when Sprint-4 has no live
'research', 'trading', 'calendar', 'github', or 'projectos' agent
registered at all.

Multi-word phrases are checked first and take precedence over
single-word keyword collisions (e.g. "ng signal pro" must resolve to
ENGINEERING, not TRADING, even though "signal" alone could suggest
trading) — see the phrase-priority ordering below.
"""

from __future__ import annotations

from jarvis.intelligence.models import SpecialistDomain

# Checked in this exact order, first match wins — multi-word / more
# specific phrases are listed before shorter, more collision-prone
# single words within the same domain, and domains likely to collide
# with each other are ordered deliberately (ENGINEERING before TRADING,
# so "ng signal pro" resolves correctly).
DOMAIN_KEYWORDS: tuple[tuple[SpecialistDomain, tuple[str, ...]], ...] = (
    (
        SpecialistDomain.PROJECTOS,
        ("projectos", "project os", "ng projectos"),
    ),
    (
        SpecialistDomain.GITHUB,
        ("github", "pull request", "repository", "repo", "commit"),
    ),
    (
        SpecialistDomain.CALENDAR,
        ("calendar", "schedule a", "meeting", "appointment"),
    ),
    (
        SpecialistDomain.ENGINEERING,
        (
            "ng signal pro",
            "build",
            "implement",
            "develop",
            "engine",
            "code",
            "architecture",
            "bug",
            "debug",
            "android",
            "voice interface",
            "memory optimization",
        ),
    ),
    (
        SpecialistDomain.RESEARCH,
        ("research", "alternative", "compare", "sqlite", "study", "look into"),
    ),
    (
        SpecialistDomain.TRADING,
        ("trade", "trading", "market", "mcx", "natural gas", "buy", "sell"),
    ),
)


class SpecialistCoordinator:
    def __init__(self, audit_ledger) -> None:
        self._audit = audit_ledger

    def select(self, goal_text: str) -> tuple[SpecialistDomain, str]:
        """Returns (domain, reason). Falls back to GENERAL, honestly, when nothing matches — never guesses a specific domain without a keyword hit."""
        lowered = goal_text.lower()

        for domain, keywords in DOMAIN_KEYWORDS:
            hits = [kw for kw in keywords if kw in lowered]
            if hits:
                reason = f"Matched keyword(s) {hits} for domain '{domain.value}'."
                self._audit.record(
                    event_type="intelligence.specialist_selected",
                    message=reason,
                    details={"domain": domain.value, "matched_keywords": hits},
                )
                return domain, reason

        reason = "No domain-specific keywords matched; defaulting to the general specialist."
        self._audit.record(
            event_type="intelligence.specialist_selected",
            message=reason,
            details={"domain": SpecialistDomain.GENERAL.value, "matched_keywords": []},
        )
        return SpecialistDomain.GENERAL, reason

    def is_healthy(self) -> bool:
        return len(DOMAIN_KEYWORDS) == len(SpecialistDomain) - 1  # every domain except GENERAL has a keyword list
