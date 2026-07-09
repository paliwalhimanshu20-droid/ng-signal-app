"""
scan_snapshot_reader.py — NGSP Phase 0, PR 6b.

Reads the most recent scan_snapshots batch (written by generate_signals.py,
PR 6a) and reshapes it back into the exact (top5_df, full_df) tuple shape
scanner.run_scanner() returns — same column names, same dtypes-by-convention
("N/A" sentinels, not NaN), same top5 derivation rule. This lets app.py
display the last background-generated scan on page load without calling
run_scanner() (and therefore without hitting the Upstox API) until the user
explicitly clicks "Run Live Scan".

Deliberately a standalone module, not added to research_db/: research_db is
documented as backend-only ("stores knowledge only"), and this module's
CamelCase-column reconstruction is a UI-layer concern specific to how
scanner.py/app.py already shape data. Keeps that decoupling intact.
"""

import pandas as pd

from research_config import settings
from research_db.database import ResearchDatabase

# scan_snapshots column (snake_case, as written by generate_signals.py's
# build_scan_snapshot_record) -> full_df column (CamelCase / spaced, as
# produced by scanner.run_scanner()). Order doesn't matter here; this is
# just the rename map.
_COLUMN_MAP = {
    "instrument": "Instrument",
    "instrument_key": "InstrumentKey",
    "sector": "Sector",
    "signal": "Signal",
    "confidence": "Confidence",
    "trend": "Trend",
    "daily_trend": "DailyTrend",
    "market_trend": "MarketTrend",
    "supertrend": "Supertrend",
    "supertrend_value": "SupertrendValue",
    "regime": "Regime",
    "adx": "ADX",
    "conviction_pct": "ConvictionPct",
    "daily_trend_agree": "DailyTrendAgree",
    "supertrend_agree": "SupertrendAgree",
    "market_trend_agree": "MarketTrendAgree",
    "score": "Score",
    "prob_pct": "Prob%",
    "rsi": "RSI",
    "volume_ratio": "Volume Ratio",
    "volume_label": "Volume",
    "expected_move_pct": "ExpectedMove%",
    "rr": "RR",
    "price": "Price",
    "sl": "SL",
    "t1": "T1",
    "t2": "T2",
    "reason": "Reason",
}


def load_latest_scan_snapshot():
    """
    Returns (top5_df, full_df, scanned_at) from the most recent persisted
    scan batch. scanned_at is the IST timestamp string generate_signals.py
    stamped on that batch, or None if scan_snapshots is empty (e.g. the
    workflow hasn't run yet on a fresh deploy).

    top5_df/full_df match scanner.run_scanner()'s output shape exactly —
    same columns, same "Score >= 7 and Signal in BUY/SELL/WATCH, head(5)"
    derivation — so every downstream consumer in app.py (opportunity
    cards, position sizing, the Full Scanned Universe table, chart
    selection) works unmodified against either source.
    """
    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    try:
        rows = db.get_latest_scan_snapshot()
    finally:
        db.close()

    if not rows:
        return pd.DataFrame(), pd.DataFrame(), None

    scanned_at = rows[0]["scanned_at"]

    full_df = pd.DataFrame(rows).rename(columns=_COLUMN_MAP)
    full_df = full_df.drop(columns=["id", "scanned_at", "created_at"], errors="ignore")
    full_df = full_df.sort_values(["Score", "Prob%"], ascending=False).reset_index(drop=True)

    top5_df = full_df[
        (full_df["Score"] >= 7) & (full_df["Signal"].isin(["BUY", "SELL", "WATCH"]))
    ].head(5)

    return top5_df, full_df, scanned_at
