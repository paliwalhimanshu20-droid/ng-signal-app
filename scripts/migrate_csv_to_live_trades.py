"""
scripts/migrate_csv_to_live_trades.py

ONE-TIME migration: imports existing signal_log.csv history into
research_db's live_trades table (added via migrations.py's
migration_002_add_live_trades_table). Run this once after deploying the
live_trades table.

Safe to re-run: skips any signal_id already present in live_trades, so
running it twice (or after new signals have already been logged through
the app into the new table) won't create duplicates or overwrite anything.

Usage (from repo root):
    python scripts/migrate_csv_to_live_trades.py

Or via the one-off GitHub Actions workflow:
.github/workflows/migrate_csv_to_db.yml (Actions tab -> "Migrate CSV to
Live Trades DB (one-time)" -> Run workflow).
"""

import csv
import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, REPO_ROOT)

from research_config import settings
from research_db.database import ResearchDatabase

CSV_PATH = os.path.join(REPO_ROOT, "signal_log.csv")


def _clean(value):
    """CSV empty strings -> None for DB columns that should be NULL when a
    trade is still OPEN or a field was never populated. Non-empty 'N/A'
    text values used by factor-tracking columns (daily_trend_agree, etc.)
    are intentionally preserved as-is, not converted to None — those are
    meaningful values (e.g. "market trend agreement not applicable"), not
    missing data."""
    if value is None:
        return None
    if value.strip() == "":
        return None
    return value


def _clean_numeric(value):
    v = _clean(value)
    if v is None:
        return None
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def main():
    if not os.path.exists(CSV_PATH):
        print(f"No signal_log.csv found at {CSV_PATH} — nothing to migrate.")
        return

    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)

    migrated = 0
    skipped_existing = 0
    skipped_bad_row = 0

    try:
        with open(CSV_PATH, "r", newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)

            for row in reader:
                signal_id = _clean(row.get("signal_id"))
                if not signal_id:
                    skipped_bad_row += 1
                    continue

                existing = db.conn.execute(
                    "SELECT id FROM live_trades WHERE signal_id = ?", (signal_id,)
                ).fetchone()
                if existing:
                    skipped_existing += 1
                    continue

                record = {
                    "signal_id": signal_id,
                    "timestamp": _clean(row.get("timestamp")),
                    "instrument": _clean(row.get("instrument")),
                    "instrument_key": _clean(row.get("instrument_key")),
                    "signal": _clean(row.get("signal")),
                    "trend": _clean(row.get("trend")),
                    "confidence": _clean(row.get("confidence")),
                    "score": _clean_numeric(row.get("score")),
                    "entry_price": _clean_numeric(row.get("entry_price")),
                    "sl": _clean_numeric(row.get("sl")),
                    "t1": _clean_numeric(row.get("t1")),
                    "t2": _clean_numeric(row.get("t2")),
                    "status": _clean(row.get("status")) or "OPEN",
                    "closed_price": _clean_numeric(row.get("closed_price")),
                    "closed_at": _clean(row.get("closed_at")),
                    "pnl_pct": _clean_numeric(row.get("pnl_pct")),
                    "daily_trend_agree": _clean(row.get("daily_trend_agree")),
                    "supertrend_agree": _clean(row.get("supertrend_agree")),
                    "market_trend_agree": _clean(row.get("market_trend_agree")),
                    "adx": _clean_numeric(row.get("adx")),
                    "conviction_pct": _clean_numeric(row.get("conviction_pct")),
                    "expected_move_pct": _clean_numeric(row.get("expected_move_pct")),
                    "t2_hit_at": _clean(row.get("t2_hit_at")),
                }

                if not record["instrument"] or not record["signal"] or not record["timestamp"]:
                    print(f"Skipping {signal_id} — missing required field(s).")
                    skipped_bad_row += 1
                    continue

                db.insert_live_trade(record)
                migrated += 1

        db.commit()
    finally:
        db.close()

    print(f"Migrated {migrated} trade(s) from signal_log.csv into live_trades.")
    if skipped_existing:
        print(f"Skipped {skipped_existing} row(s) already present in live_trades (safe re-run).")
    if skipped_bad_row:
        print(f"Skipped {skipped_bad_row} malformed row(s) — check signal_log.csv for missing fields.")


if __name__ == "__main__":
    main()
