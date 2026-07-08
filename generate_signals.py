"""
generate_signals.py

Standalone script — run independently of the Streamlit app, on a schedule,
via GitHub Actions (see .github/workflows/generate_signals.yml, PR 3).

NGSP Phase 0: this is the piece that removes the "signal generation only
happens when a user clicks Run Live Scan" bottleneck. It runs the exact
same scan + scoring path the button already uses (scanner.run_scanner())
and persists actionable signals to the same live_trades table, so the
Streamlit app can eventually just read the latest batch instead of
recomputing it (app.py change — later PR, not this one).

Deliberately follows the same pattern check_signals.py already established
for headless scripts:
  - Talks to research_db.database.ResearchDatabase directly, NOT through
    signal_log.py — signal_log.py imports streamlit (st.cache_data,
    st.warning/error/success inside push_research_db_to_github()), which
    only works inside a real Streamlit session. See signal_log.py's own
    module docstring: "check_signals.py... do NOT import this module...
    talk to research_db directly."
  - Does NOT push to GitHub itself. push_research_db_to_github() (in
    signal_log.py) uses the Contents API specifically because Streamlit
    Community Cloud can't run git commands — GitHub Actions already has a
    git checkout, so the workflow's own `git add/commit/push` steps
    (identical to check_signals.yml's) handle persistence instead.
  - Reads UPSTOX_ACCESS_TOKEN via config.py, which after NGSP Phase 0
    PR 1c falls back to os.environ when st.secrets has nothing to offer
    (i.e. outside a Streamlit runtime) — same secret, same GitHub Actions
    secret name the workflow injects, no new secret to configure.

What this duplicates from signal_log.py.append_new_signals(), and why:
that function's VALIDATION logic (skip if already OPEN for the same
instrument+direction, skip if SL/T1 came back "N/A") is reproduced here
against research_db directly, because the function itself is unreachable
without importing streamlit along with it. The dedupe/skip RULES are
copied verbatim — nothing about what counts as a valid, actionable signal
has changed, only which code path checks it.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from datetime import datetime
from zoneinfo import ZoneInfo

from config import UPSTOX_ACCESS_TOKEN, COMMODITY_DEFINITIONS
from upstox_client import get_commodity_contracts
from scanner import run_scanner
from research_config import settings
from research_db.database import ResearchDatabase
from telegram_notify import send_telegram_message

IST = ZoneInfo("Asia/Kolkata")
TIMESTAMP_FORMAT = "%Y-%m-%d %H:%M:%S"


def resolve_front_month_contracts():
    """
    Headless equivalent of app.py's Settings-tab expiry dropdown. Verified
    (NGSP Phase 0 Q1) against the real behavior: get_commodity_contracts()
    returns contracts "sorted by nearest expiry first" (its own docstring),
    and app.py's st.selectbox is called with no index=, so it defaults to
    element 0 on a fresh session anyway — this just picks that same
    element directly instead of via a dropdown default.
    """
    resolved = {}
    for display_name, symbol_filter in COMMODITY_DEFINITIONS:
        result = get_commodity_contracts(symbol_filter, max_contracts=4)
        if result["error"] or not result["contracts"]:
            # Same soft-skip the UI does (st.warning/st.error there just
            # means that commodity's column is empty) — one missing
            # commodity shouldn't abort the whole scan.
            print(f"{display_name}: {result['error'] or 'no live contracts found'} — skipping.")
            continue
        resolved[display_name] = result["contracts"][0]["key"]
    return resolved


def build_new_signal_record(row):
    """
    Field-for-field identical to the dict signal_log.append_new_signals()
    builds — same keys, same source columns, same "N/A" fallbacks — just
    constructed here so it can be handed straight to
    ResearchDatabase.insert_live_trade() without importing signal_log.py.
    """
    now = datetime.now(IST)
    return {
        "signal_id": f"{row['Instrument']}_{now.strftime('%Y%m%d%H%M%S')}",
        "timestamp": now.strftime(TIMESTAMP_FORMAT),
        "instrument": row["Instrument"],
        "instrument_key": row.get("InstrumentKey", ""),
        "signal": row["Signal"],
        "trend": row["Trend"],
        "confidence": row["Confidence"],
        "score": row["Score"],
        "entry_price": row["Price"],
        "sl": row["SL"],
        "t1": row["T1"],
        "t2": row["T2"],
        "status": "OPEN",
        "closed_price": None,
        "closed_at": None,
        "pnl_pct": None,
        "daily_trend_agree": row.get("DailyTrendAgree", "N/A"),
        "supertrend_agree": row.get("SupertrendAgree", "N/A"),
        "market_trend_agree": row.get("MarketTrendAgree", "N/A"),
        "adx": row.get("ADX", "N/A"),
        "conviction_pct": row.get("ConvictionPct", "N/A"),
        "expected_move_pct": row.get("ExpectedMove%", "N/A"),
        "t2_hit_at": None,
    }


def main():
    if not UPSTOX_ACCESS_TOKEN:
        print("UPSTOX_ACCESS_TOKEN not set — cannot fetch prices. Exiting.")
        return

    commodity_contracts = resolve_front_month_contracts()

    print("Running scan...")
    top5_df, full_df = run_scanner(commodity_contracts)

    if full_df is None or full_df.empty:
        print("No scan results — nothing to persist this run.")
        return

    # Same filter append_new_signals() applies: only BUY/SELL rows are
    # candidates. full_df, not top5_df — top5_df is a top-5-by-score
    # display slice; a real BUY/SELL outside the top 5 would be silently
    # dropped if we persisted from that instead (confirmed, NGSP Phase 0 Q2).
    actionable = full_df[full_df["Signal"].isin(["BUY", "SELL"])]

    if actionable.empty:
        print("Scan complete — no actionable BUY/SELL signals this run.")
        return

    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)

    try:
        # Same dedupe rule as append_new_signals(): skip if this exact
        # instrument+direction already has an OPEN trade.
        open_pairs = {
            (t["instrument"], t["signal"]) for t in db.get_open_live_trades()
        }

        new_records = []
        for _, row in actionable.iterrows():
            if (row["Instrument"], row["Signal"]) in open_pairs:
                continue
            if row["SL"] == "N/A" or row["T1"] == "N/A":
                continue
            new_records.append(build_new_signal_record(row))

        if not new_records:
            print("Scan complete — all actionable signals already OPEN or invalid (SL/T1 N/A).")
            return

        for record in new_records:
            db.insert_live_trade(record)

        db.commit()
        print(f"Persisted {len(new_records)} new signal(s) to live_trades.")

    finally:
        db.close()

    for record in new_records:
        msg = (
            f"🆕 *{record['signal']}* — {record['instrument']}\n"
            f"Entry: {record['entry_price']} | SL: {record['sl']} | "
            f"T1: {record['t1']} | T2: {record['t2']}\n"
            f"Confidence: {record['confidence']} | Score: {record['score']}"
        )
        send_telegram_message(msg)

    print(f"Sent {len(new_records)} Telegram alert(s).")


if __name__ == "__main__":
    main()
