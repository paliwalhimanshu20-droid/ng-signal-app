"""
NGSP-002 — Market Intelligence Engine: Event Risk Engine
=========================================================

Evaluates upcoming corporate events (earnings, board meetings,
dividends, bonus issues, splits, other corporate actions) and
converts proximity into a risk level, a confidence penalty, and an
optional signal-suppression flag.

Risk model
----------
The nearest future event drives the risk level via configurable
day windows (``EventConfig``):

- within ``high_risk_days``   -> HIGH  (optionally suppresses signals)
- within ``medium_risk_days`` -> MEDIUM
- within ``low_risk_days``    -> LOW
- beyond, or no events        -> NONE

Past events are ignored. When multiple events fall in the windows,
the highest-risk (nearest) event wins; others are mentioned in the
reasons for transparency.

Future extension points
-----------------------
- Per-event-type windows/penalties (earnings stricter than dividends).
- Post-event cool-down windows.
- Macro events (RBI policy, budget day) via a market-level variant.
"""

from __future__ import annotations

from datetime import date
from typing import List, Optional, Tuple

from market_config import EventConfig
from market_models import EventResult, EventType, RiskLevel, UpcomingEvent
from market_utils import days_until


class EventEngine:
    """Evaluates event risk for a single symbol."""

    def __init__(self, config: EventConfig) -> None:
        config.validate()
        self._config = config

    def evaluate(
        self,
        events: List[UpcomingEvent],
        today: Optional[date] = None,
    ) -> EventResult:
        """Evaluate ``events`` against the configured risk windows.

        Parameters
        ----------
        events:
            All known events for the symbol (past events are ignored).
        today:
            Injectable "current date" for deterministic tests.

        Returns
        -------
        EventResult
            Risk level, suppression flag, days remaining, nearest
            event type, penalty, and explanations.
        """
        cfg = self._config
        reference = today or date.today()

        future: List[Tuple[int, UpcomingEvent]] = sorted(
            (
                (days_until(ev.event_date, today=reference), ev)
                for ev in events
                if days_until(ev.event_date, today=reference) >= 0
            ),
            key=lambda pair: pair[0],
        )

        if not future:
            return EventResult(
                risk_level=RiskLevel.NONE,
                suppress_signal=False,
                days_remaining=None,
                event_type=None,
                reasons=["No upcoming corporate events"],
                penalty=cfg.penalty_none,
            )

        days_remaining, nearest = future[0]
        risk_level = self._classify(days_remaining)
        penalty = self._penalty_for(risk_level)
        suppress = risk_level is RiskLevel.HIGH and cfg.suppress_on_high_risk

        reasons = [
            (
                f"{nearest.event_type.value} in {days_remaining} day(s) "
                f"on {nearest.event_date.isoformat()} -> {risk_level.value} risk"
            )
        ]
        if suppress:
            reasons.append(
                f"Signal suppressed: within {cfg.high_risk_days}-day "
                f"high-risk event window"
            )
        if penalty > 0:
            reasons.append(f"Event penalty applied: -{penalty:g} confidence points")

        # Surface additional events inside the low-risk horizon.
        for extra_days, extra in future[1:]:
            if extra_days <= cfg.low_risk_days:
                reasons.append(
                    f"Also upcoming: {extra.event_type.value} in {extra_days} day(s)"
                )

        return EventResult(
            risk_level=risk_level,
            suppress_signal=suppress,
            days_remaining=days_remaining,
            event_type=nearest.event_type,
            reasons=reasons,
            penalty=penalty,
        )

    # ------------------------------------------------------------------

    def _classify(self, days_remaining: int) -> RiskLevel:
        cfg = self._config
        if days_remaining <= cfg.high_risk_days:
            return RiskLevel.HIGH
        if days_remaining <= cfg.medium_risk_days:
            return RiskLevel.MEDIUM
        if days_remaining <= cfg.low_risk_days:
            return RiskLevel.LOW
        return RiskLevel.NONE

    def _penalty_for(self, level: RiskLevel) -> float:
        cfg = self._config
        return {
            RiskLevel.HIGH: cfg.penalty_high,
            RiskLevel.MEDIUM: cfg.penalty_medium,
            RiskLevel.LOW: cfg.penalty_low,
            RiskLevel.NONE: cfg.penalty_none,
        }[level]
