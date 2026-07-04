"""
weekly_summary.py

Runs once a week (see .github/workflows/weekly_summary.yml), independent
of both the Streamlit app and check_signals.py. Reads the Research &
Learning Database's live_trades table, builds a summary of the past 7
days' CLOSED trades (win rate, avg P&L, per-instrument breakdown), and
sends it to Telegram as a readable report.

MIGRATED from signal_log.csv to research_db — this used to read the CSV
directly via its own hardcoded path; it now reads live_trades the same
way check_signals.py does. Still deliberately dependency-free from the
Streamlit app (research_db/research_config have zero Streamlit
dependency) and from pandas — this only ever needed simple aggregation,
which is easy enough directly over the list of dicts research_db returns.

This does NOT modify the database — it's read-only, purely a reporting
script. check_signals.py remains the only thing that writes outcomes.

Secrets required (same GitHub Actions secrets as check_signals.py):
  TELEGRAM_BOT_TOKEN
  TELEGRAM_CHAT_ID
"""

import os
import sys
import requests
from collections import defaultdict
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from research_config import settings
from research_db.database import ResearchDatabase

IST = ZoneInfo("Asia/Kolkata")
TIMESTAMP_FORMAT = "%Y-%m-%d %H:%M:%S"

TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")


def send_telegram_message(text):
    if not TELEGRAM_BOT_TOKEN or not TELEGRAM_CHAT_ID:
        print("Telegram not configured — printing report instead.")
        print(text)
        return

    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    try:
        r = requests.post(
            url,
            data={"chat_id": TELEGRAM_CHAT_ID, "text": text, "parse_mode": "Markdown"},
            timeout=10
        )
        if r.status_code != 200:
            print(f"Telegram send failed: {r.status_code} {r.text[:200]}")
    except Exception as e:
        print(f"Telegram send error: {e}")


def _parse_timestamp(ts_str):
    if not ts_str:
        return None
    try:
        return datetime.strptime(ts_str, TIMESTAMP_FORMAT)
    except (TypeError, ValueError):
        return None


def main():
    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)
    try:
        trades = db.get_all_live_trades()
    finally:
        db.close()

    if not trades:
        send_telegram_message("📊 Weekly Signal Report\n\nSignal log is empty — nothing to report this week.")
        return

    cutoff = datetime.now(IST).replace(tzinfo=None) - timedelta(days=7)

    # Reminder of the known limitation: outcomes are checked on a ~30 min
    # polling cycle by check_signals.py, not tick-by-tick — so closed_at
    # times are "approximately when this was detected," not exact fills.
    recent = [t for t in trades if (_parse_timestamp(t["timestamp"]) or datetime.min) >= cutoff]

    if not recent:
        send_telegram_message("📊 *Weekly Signal Report*\n\nNo signals generated in the past 7 days.")
        return

    closed = [t for t in recent if t["status"] in ("TARGET_HIT", "SL_HIT")]
    still_open = [t for t in recent if t["status"] == "OPEN"]

    lines = ["📊 *Weekly Signal Report*", f"_{datetime.now(IST).strftime('%d-%b-%Y')}_", ""]

    if not closed:
        lines.append(f"No closed trades this week. {len(still_open)} signal(s) still open.")
    else:
        wins = sum(1 for t in closed if t["status"] == "TARGET_HIT")
        total = len(closed)
        win_rate = round(wins / total * 100, 1)
        pnl_values = [t["pnl_pct"] for t in closed if t.get("pnl_pct") is not None]
        avg_pnl = round(sum(pnl_values) / len(pnl_values), 2) if pnl_values else 0

        lines.append(f"*Closed Trades:* {total}")
        lines.append(f"*Win Rate:* {win_rate}% ({wins}W / {total - wins}L)")
        lines.append(f"*Avg P&L per Trade:* {avg_pnl}%")
        lines.append(f"*Still Open:* {len(still_open)}")
        lines.append("")
        lines.append("*Per-Instrument:*")

        per_instrument = defaultdict(lambda: {"trades": 0, "wins": 0, "pnl_sum": 0.0, "pnl_n": 0})
        for t in closed:
            bucket = per_instrument[t["instrument"]]
            bucket["trades"] += 1
            if t["status"] == "TARGET_HIT":
                bucket["wins"] += 1
            if t.get("pnl_pct") is not None:
                bucket["pnl_sum"] += t["pnl_pct"]
                bucket["pnl_n"] += 1

        for instrument, b in per_instrument.items():
            wr = round(b["wins"] / b["trades"] * 100, 0) if b["trades"] else 0
            avg = round(b["pnl_sum"] / b["pnl_n"], 2) if b["pnl_n"] else 0
            lines.append(f"• {instrument}: {b['trades']} trades, {wr}% win, {avg}% avg")

        lines.append("")
        lines.append("*Full Trade List:*")
        for t in sorted(closed, key=lambda x: x["timestamp"]):
            outcome_emoji = "🎯" if t["status"] == "TARGET_HIT" else "🛑"
            pnl = t.get("pnl_pct") or 0
            sign = "+" if pnl >= 0 else ""
            lines.append(f"{outcome_emoji} {t['instrument']} ({t['signal']}) {sign}{pnl}%")

    report = "\n".join(lines)

    # Telegram has a ~4096 character limit per message — truncate safely
    # rather than letting the send silently fail on a long trade list.
    if len(report) > 4000:
        report = report[:3950] + "\n\n_...truncated, see dashboard for full log._"

    send_telegram_message(report)
    print("Weekly report sent.")


if __name__ == "__main__":
    main()
