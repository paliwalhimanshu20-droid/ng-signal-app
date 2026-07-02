"""
NGSP-002 — Market Intelligence Engine: Fundamental Quality Gate
================================================================

Monthly-refresh quality filter. Scores a symbol's fundamentals
against configurable thresholds and decides whether the quality
gate passes. This module only evaluates quality — it never
generates trading signals.

Scoring model
-------------
Each metric contributes a configured number of points when it meets
its threshold (see ``FundamentalConfig``). Missing metrics score 0
points with an explanatory reason. The gate passes when the total
score is at or above ``min_pass_score``.

Staleness
---------
Data older than ``refresh_days`` is flagged ``is_stale``. Behaviour
for stale data is configurable: pass-through with a warning
(default) or hard fail.

Future extension points
-----------------------
- Graded (partial-credit) scoring per metric instead of pass/fail.
- Sector-relative thresholds (e.g. banks judged on different D/E).
- Piotroski-style composite scores.
"""

from __future__ import annotations

from datetime import date
from typing import List, Optional

from market_config import FundamentalConfig
from market_models import FundamentalResult, FundamentalSnapshot
from market_utils import is_stale


class FundamentalEngine:
    """Evaluates fundamental quality for a single symbol."""

    def __init__(self, config: FundamentalConfig) -> None:
        config.validate()
        self._config = config

    def evaluate(
        self,
        snapshot: Optional[FundamentalSnapshot],
        today: Optional[date] = None,
    ) -> FundamentalResult:
        """Score ``snapshot`` and apply the quality gate.

        Parameters
        ----------
        snapshot:
            Fundamental metrics for the symbol, or ``None`` when the
            provider has no data at all.
        today:
            Injectable "current date" for deterministic tests.

        Returns
        -------
        FundamentalResult
            Score (0–100), pass/fail, reasons, freshness metadata.
        """
        cfg = self._config

        if snapshot is None:
            return FundamentalResult(
                score=0.0,
                passed=cfg.stale_data_passes,
                reasons=["No fundamental data available for symbol"],
                last_updated=None,
                is_stale=True,
            )

        score = 0.0
        reasons: List[str] = []

        score += self._score_metric(
            value=snapshot.roe,
            passes=lambda v: v >= cfg.min_roe,
            points=cfg.points_roe,
            name="ROE",
            good=f"ROE {snapshot.roe}% >= {cfg.min_roe}% (healthy return on equity)",
            bad=f"ROE {snapshot.roe}% below {cfg.min_roe}% threshold",
            reasons=reasons,
        )
        score += self._score_metric(
            value=snapshot.debt_to_equity,
            passes=lambda v: v <= cfg.max_debt_to_equity,
            points=cfg.points_debt_to_equity,
            name="Debt/Equity",
            good=(
                f"Debt/Equity {snapshot.debt_to_equity} <= "
                f"{cfg.max_debt_to_equity} (low leverage)"
            ),
            bad=(
                f"Debt/Equity {snapshot.debt_to_equity} above "
                f"{cfg.max_debt_to_equity} (elevated leverage)"
            ),
            reasons=reasons,
        )
        score += self._score_metric(
            value=snapshot.eps_growth,
            passes=lambda v: v >= cfg.min_eps_growth,
            points=cfg.points_eps_growth,
            name="EPS growth",
            good=f"EPS growth {snapshot.eps_growth}% >= {cfg.min_eps_growth}%",
            bad=f"EPS growth {snapshot.eps_growth}% below {cfg.min_eps_growth}%",
            reasons=reasons,
        )
        score += self._score_metric(
            value=snapshot.sales_growth,
            passes=lambda v: v >= cfg.min_sales_growth,
            points=cfg.points_sales_growth,
            name="Sales growth",
            good=f"Sales growth {snapshot.sales_growth}% >= {cfg.min_sales_growth}%",
            bad=f"Sales growth {snapshot.sales_growth}% below {cfg.min_sales_growth}%",
            reasons=reasons,
        )
        score += self._score_metric(
            value=snapshot.profit_growth,
            passes=lambda v: v >= cfg.min_profit_growth,
            points=cfg.points_profit_growth,
            name="Profit growth",
            good=f"Profit growth {snapshot.profit_growth}% >= {cfg.min_profit_growth}%",
            bad=f"Profit growth {snapshot.profit_growth}% below {cfg.min_profit_growth}%",
            reasons=reasons,
        )

        stale = is_stale(snapshot.last_updated, cfg.refresh_days, today=today)
        passed = score >= cfg.min_pass_score

        if stale:
            reasons.append(
                f"Fundamental data stale (older than {cfg.refresh_days} days)"
            )
            if cfg.stale_data_passes:
                # Do not block on stale data; keep score-based decision.
                reasons.append("Stale data allowed by config (pass-through)")
            else:
                passed = False
                reasons.append("Stale data fails gate per config")

        reasons.append(
            f"Fundamental score {score:.1f}/100 "
            f"({'passes' if passed else 'fails'} gate at {cfg.min_pass_score})"
        )

        return FundamentalResult(
            score=round(score, 2),
            passed=passed,
            reasons=reasons,
            last_updated=snapshot.last_updated,
            is_stale=stale,
        )

    # ------------------------------------------------------------------

    @staticmethod
    def _score_metric(
        value: Optional[float],
        passes,
        points: float,
        name: str,
        good: str,
        bad: str,
        reasons: List[str],
    ) -> float:
        """Score one metric; append the matching explanation."""
        if value is None:
            reasons.append(f"{name} unavailable (0 points)")
            return 0.0
        if passes(value):
            reasons.append(good)
            return points
        reasons.append(bad)
        return 0.0
