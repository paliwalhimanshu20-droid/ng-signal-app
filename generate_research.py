"""
generate_research.py — PR 8, Part 2.

Standalone script — run independently of the Streamlit app, on a
schedule, via GitHub Actions (see .github/workflows/generate_research.yml).
Mirrors generate_signals.py's established pattern exactly: no Streamlit
import, talks to research_db directly, safe DRY_RUN default.

What this does NOT touch: scanner.py, signal_logic.py, live_trades,
scan_snapshots, or anything on the Live Scan / Background Signal
Generation path. This is a fully separate pipeline — strategy_lab in,
research_db out — that happens to reuse the same watchlist and the same
signal_logic indicator functions those other pipelines already use, the
same way strategy_lab.strategies.run_strategy() already does today.

INCREMENTAL: before analyzing an instrument, checks research_db for that
instrument's most recent completed STRATEGY experiment. If one exists
from within FRESHNESS_HOURS, the instrument is skipped this run — so a
scheduled job doesn't redundantly re-run every instrument every time it
fires. First run (or a stale/never-analyzed instrument) always runs.

HONEST SCOPE NOTE: this analyzes whatever history
strategy_lab.backtest.load_history() can fetch today (Upstox 30-min
candles, ~120 calendar days via LOOKBACK_DAYS) — not 10 years. See
strategy_lab/research_engine.py's module docstring for why, and what
would need to change (a populated, scheduled Historical Warehouse) to
extend this further. Nothing here claims a longer history than what's
actually being read.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from datetime import datetime, timedelta, timezone

from watchlist import get_watchlist
from strategy_lab.research_engine import analyze_instrument
from strategy_lab.research_bridge import persist_research_result, apply_confidence_source_column
from research_config import settings
from research_db.database import ResearchDatabase

# Same safe-by-default pattern as generate_signals.py — has to be
# deliberately unchecked via the workflow's dry_run input to persist.
DRY_RUN = os.environ.get("DRY_RUN", "true").strip().lower() == "true"

# How recent a completed STRATEGY experiment for an instrument has to be
# to skip re-analyzing it this run. Research doesn't need intraday
# freshness the way live signals do — daily is a reasonable default for
# a scheduled job; override via env if a future scheduling cadence wants
# something different.
FRESHNESS_HOURS = float(os.environ.get("RESEARCH_FRESHNESS_HOURS", "24"))


def _is_fresh(instrument_key: str, db: ResearchDatabase) -> bool:
    history = db.get_instrument_history(instrument_key, limit=5)
    for row in history:
        if row.get("research_type") != "STRATEGY" or row.get("research_status") != "COMPLETED":
            continue
        try:
            ts = datetime.fromisoformat(row["timestamp"])
        except (KeyError, ValueError):
            continue
        if ts.tzinfo is None:
            ts = ts.replace(tzinfo=timezone.utc)
        age = datetime.now(timezone.utc) - ts
        if age < timedelta(hours=FRESHNESS_HOURS):
            return True
    return False


def main():
    watchlist = get_watchlist(None)
    print(f"Watchlist: {len(watchlist)} instruments.")

    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    try:
        to_analyze = []
        for name, key in watchlist.items():
            if not key:
                continue
            if _is_fresh(key, db):
                print(f"{name}: fresh research already exists within {FRESHNESS_HOURS}h — skipping.")
                continue
            to_analyze.append((name, key))
    finally:
        db.close()

    print(f"{len(to_analyze)} of {len(watchlist)} instruments need research this run.")

    completed, failed, skipped_no_data = 0, 0, 0

    for name, key in to_analyze:
        print(f"Analyzing {name} ({key})...")
        try:
            result = analyze_instrument(name, key)
        except Exception as e:
            print(f"  ERROR analyzing {name}: {e}")
            failed += 1
            continue

        if not result.get("data_available"):
            print(f"  No data available for {name} ({result.get('data_source')}): {result.get('reason')} — skipped.")
            skipped_no_data += 1
            continue

        best = result["best_combination"]
        print(
            f"  Best: '{best['combination_name']}' — win rate {best['metrics']['win_rate']}% "
            f"over {best['metrics']['total_trades']} simulated trades. "
            f"Best regime: {result['best_market_regime']}. "
            f"Research Score: {result['research_score']['score']}/100. "
            f"Strategy family: {result['instrument_dna']['strategy_family']}. "
            f"Data source: {result['data_source']}."
        )

        if DRY_RUN:
            print(f"  [DRY RUN] Would persist this result to research_db — skipped.")
            continue

        run_id = persist_research_result(result)
        apply_confidence_source_column(run_id, result["confidence_source"])
        print(f"  Persisted as run_id={run_id}.")
        completed += 1

    print(
        f"\nDone. {completed} persisted, {failed} failed, {skipped_no_data} skipped "
        f"(no data), {len(watchlist) - len(to_analyze)} skipped (fresh)."
        + (" [DRY RUN — nothing was actually written]" if DRY_RUN else "")
    )


if __name__ == "__main__":
    main()
