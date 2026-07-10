"""
research_snapshot_reader.py — PR 8, Part 3, extended in the PR 8
continuation for Requirements 1-3.

Reads the most recent completed research for an instrument (written by
generate_research.py via strategy_lab/research_bridge.py) and reshapes it
into the exact field names PR 8's Research Contract asks for, PLUS the
continuation's three additions: Indicator Reliability (per-indicator,
not the old combination-delta approximation), Instrument DNA, and
Research Score. This is the ONLY interface the frontend uses to reach
research_db — app.py never queries research_db directly.

get_research_contract() keeps returning the original flat Signal-Card-
adjacent fields for backward compatibility with the Research tab built
in the first PR 8 pass. get_full_research_profile() is the new, richer
call the extended Research tab uses for Indicator Reliability/Instrument
DNA/Research Score — kept separate rather than bloating the original
function's return shape.
"""

import json

from research_config import settings
from research_db.database import ResearchDatabase


def _parse_extra_metrics(metrics_row: dict) -> dict:
    raw = metrics_row.get("extra_metrics")
    if not raw:
        return {}
    try:
        return json.loads(raw)
    except (TypeError, ValueError):
        return {}


def _load_summary(instrument_key: str) -> dict:
    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    try:
        return db.get_latest_research_summary(instrument_key)
    finally:
        db.close()


def _best_metrics_and_strategy(summary: dict):
    overall_metrics = summary["overall_metrics"]
    strategy_results = summary["strategy_results"]

    # The research_score_and_dna marker row (empty metrics dict, only
    # extra_metrics populated) must not be picked as a "best" strategy
    # row — filter it out before ranking.
    candidate_rows = [
        m for m in overall_metrics
        if _parse_extra_metrics(m).get("record_type") != "research_score_and_dna"
    ]

    best_metrics_row = None
    best_extra = {}
    for m in candidate_rows:
        extra = _parse_extra_metrics(m)
        if extra.get("strategy_rank") == 1:
            best_metrics_row = m
            best_extra = extra
            break
    if best_metrics_row is None and candidate_rows:
        best_metrics_row = candidate_rows[0]
        best_extra = _parse_extra_metrics(best_metrics_row)

    best_strategy_name = best_extra.get("combination_name")
    best_strategy_row = next(
        (s for s in strategy_results if s["strategy_name"] == best_strategy_name),
        strategy_results[0] if strategy_results else {},
    )
    return best_metrics_row, best_extra, best_strategy_row


def get_research_contract(instrument_key: str) -> dict:
    """
    Original PR 8 Research Contract fields — unchanged shape from the
    first pass, so the existing Research tab keeps working unmodified.
    """
    summary = _load_summary(instrument_key)
    if not summary:
        return {"research_available": False, "instrument_key": instrument_key}

    experiment = summary["experiment"]
    regime_metrics = summary["regime_metrics"]
    notes = summary["notes"]

    best_metrics_row, best_extra, best_strategy_row = _best_metrics_and_strategy(summary)
    if best_metrics_row is None:
        return {"research_available": False, "instrument_key": instrument_key}

    best_regime = None
    if regime_metrics:
        best_regime_row = max(regime_metrics, key=lambda r: r.get("win_rate") or 0)
        best_regime = best_regime_row.get("regime_type")

    indicator_success_frequency = 0.0
    baseline_row = next(
        (m for m in summary["overall_metrics"]
         if _parse_extra_metrics(m).get("combination_name", "").endswith("(baseline)")),
        None,
    )
    if baseline_row and best_metrics_row is not baseline_row:
        best_wr = best_metrics_row.get("win_rate") or 0
        base_wr = baseline_row.get("win_rate") or 0
        indicator_success_frequency = round(best_wr - base_wr, 2)

    why_text = notes[-1]["note_text"] if notes else None

    return {
        "research_available": True,
        "instrument_key": instrument_key,
        "last_updated": experiment.get("timestamp"),
        "best_strategy": best_extra.get("combination_name") or best_strategy_row.get("strategy_name"),
        "historical_win_rate": best_metrics_row.get("win_rate"),
        "backtested_trades": best_metrics_row.get("total_trades"),
        "expected_holding": best_metrics_row.get("avg_holding_time_minutes"),
        "market_regime": best_regime,
        "strategy_rank": best_extra.get("strategy_rank"),
        "average_return": best_metrics_row.get("average_trade"),
        "average_drawdown": best_metrics_row.get("max_drawdown"),
        "confidence_source": best_strategy_row.get("confidence_source") or best_extra.get("confidence_source"),
        "indicator_success_frequency": indicator_success_frequency,
        "research_explanation": why_text,
        "why_this_strategy": why_text,
    }


def get_full_research_profile(instrument_key: str) -> dict:
    """
    PR 8 continuation. Returns Indicator Reliability, Instrument DNA, and
    Research Score for an instrument, alongside everything
    get_research_contract() already returns — one call for the extended
    Research tab, still 100% read-only, no computation.

    research_available=False (with a reason, when known) if nothing has
    been persisted yet for this instrument — a real, expected state on a
    fresh deploy or before generate_research.py's first run for it.
    """
    contract = get_research_contract(instrument_key)
    if not contract.get("research_available"):
        return {**contract, "indicator_reliability": {}, "instrument_dna": {}, "research_score": None}

    summary = _load_summary(instrument_key)

    dna_row = next(
        (m for m in summary["overall_metrics"]
         if _parse_extra_metrics(m).get("record_type") == "research_score_and_dna"),
        None,
    )
    dna_extra = _parse_extra_metrics(dna_row) if dna_row else {}

    indicator_reliability = {}
    for row in summary["indicator_results"]:
        try:
            indicator_reliability[row["indicator_name"]] = json.loads(row["result"]) if row.get("result") else {}
        except (TypeError, ValueError):
            indicator_reliability[row["indicator_name"]] = {}

    return {
        **contract,
        "indicator_reliability": indicator_reliability,
        "instrument_dna": dna_extra.get("instrument_dna", {}),
        "research_score": dna_extra.get("research_score"),
    }
