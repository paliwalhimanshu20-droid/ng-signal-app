"""
strategy_lab/research_score.py — PR 8 continuation, Requirement 1.

Research Score: a single 0-100 number summarizing "how much should a
trader trust this strategy for this instrument," built from four
documented components (weights in strategy_lab/scoring_config.py, not
here — see that module for why each component exists and what each
weight means).

FORMULA (exact, no hidden steps):

  score = (win_rate/100 * WIN_RATE_WEIGHT)
        + (min(total_trades/SAMPLE_SIZE_TARGET, 1.0) * SAMPLE_SIZE_WEIGHT)
        + (min(profit_factor/PROFIT_FACTOR_TARGET, 1.0) * PROFIT_FACTOR_WEIGHT)
        + (regime_consistency_ratio * REGIME_CONSISTENCY_WEIGHT)

  regime_consistency_ratio = (number of tested regimes with win_rate > 50%)
                              / (number of regimes tested)
                              — 0 if no regime breakdown exists yet.

Each term is capped at its own weight (a component can't contribute
more than its configured share), so the total is mathematically bounded
to WIN_RATE_WEIGHT + SAMPLE_SIZE_WEIGHT + PROFIT_FACTOR_WEIGHT +
REGIME_CONSISTENCY_WEIGHT, which scoring_config.py's weights sum to 100
by construction (asserted below at import time, so a misconfigured
weight set fails loudly instead of silently producing a score that
isn't out of 100).
"""

from strategy_lab import scoring_config as cfg

_TOTAL_WEIGHT = (
    cfg.WIN_RATE_WEIGHT + cfg.SAMPLE_SIZE_WEIGHT
    + cfg.PROFIT_FACTOR_WEIGHT + cfg.REGIME_CONSISTENCY_WEIGHT
)
assert _TOTAL_WEIGHT == 100, (
    f"strategy_lab/scoring_config.py weights must sum to 100, got {_TOTAL_WEIGHT}. "
    "Fix the weights there, not here."
)


def compute_research_score(metrics: dict, regime_breakdown: dict) -> dict:
    """
    metrics: a combo_metrics-shaped dict (win_rate, total_trades,
    profit_factor — same shape strategy_lab.metrics.compute_metrics()
    plus research_engine.py's additions produces).
    regime_breakdown: {regime_type: metrics_dict, ...} — same shape
    research_engine.py's per-combination regime_breakdown already has.

    Returns {"score": float, "breakdown": {...}} — breakdown exposes
    every component's raw value AND its weighted contribution, so the
    score is auditable, not a black box.
    """
    win_rate = metrics.get("win_rate") or 0.0
    total_trades = metrics.get("total_trades") or 0
    profit_factor = metrics.get("profit_factor") or 0.0
    if profit_factor == float("inf"):
        # No losing trades at all in the sample — cap at the target
        # rather than letting infinity dominate the score.
        profit_factor = cfg.PROFIT_FACTOR_TARGET

    win_rate_component = (win_rate / 100) * cfg.WIN_RATE_WEIGHT
    sample_size_ratio = min(total_trades / cfg.SAMPLE_SIZE_TARGET, 1.0)
    sample_size_component = sample_size_ratio * cfg.SAMPLE_SIZE_WEIGHT
    profit_factor_ratio = min(profit_factor / cfg.PROFIT_FACTOR_TARGET, 1.0)
    profit_factor_component = profit_factor_ratio * cfg.PROFIT_FACTOR_WEIGHT

    regimes_tested = len(regime_breakdown)
    if regimes_tested:
        regimes_with_edge = sum(
            1 for m in regime_breakdown.values() if (m.get("win_rate") or 0) > 50
        )
        regime_consistency_ratio = regimes_with_edge / regimes_tested
    else:
        regime_consistency_ratio = 0.0
    regime_consistency_component = regime_consistency_ratio * cfg.REGIME_CONSISTENCY_WEIGHT

    score = round(
        win_rate_component + sample_size_component
        + profit_factor_component + regime_consistency_component,
        2,
    )

    return {
        "score": score,
        "breakdown": {
            "win_rate": {"raw": win_rate, "weight": cfg.WIN_RATE_WEIGHT, "contribution": round(win_rate_component, 2)},
            "sample_size": {"raw": total_trades, "target": cfg.SAMPLE_SIZE_TARGET, "weight": cfg.SAMPLE_SIZE_WEIGHT, "contribution": round(sample_size_component, 2)},
            "profit_factor": {"raw": profit_factor, "target": cfg.PROFIT_FACTOR_TARGET, "weight": cfg.PROFIT_FACTOR_WEIGHT, "contribution": round(profit_factor_component, 2)},
            "regime_consistency": {"raw": regime_consistency_ratio, "regimes_tested": regimes_tested, "weight": cfg.REGIME_CONSISTENCY_WEIGHT, "contribution": round(regime_consistency_component, 2)},
        },
    }
