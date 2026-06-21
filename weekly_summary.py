"""
weekly_summary.py

Runs once a week (see .github/workflows/weekly_summary.yml), independent
of both the Streamlit app and check_signals.py. Reads signal_log.csv,
builds a summary of the past 7 days' CLOSED trades (win rate, avg P&L,
per-instrument breakdown), and sends it to Telegram as a readable report.

This does NOT modify signal_log.csv — it's read-only, purely a reporting
script. check_signals.py remains the only thing that writes outcomes.

Secrets required (same GitHub Actions secrets as check_signals.py):
  TELEGRAM_BOT_TOKEN
  TELEGRAM_CHAT_ID
"""

import os
import requests
import pandas as pd
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

IST = ZoneInfo("Asia/Kolkata")
SIGNAL_LOG_PATH = "signal_log.csv"

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


def main():
    if not os.path.exists(SIGNAL_LOG_PATH):
        send_telegram_message("📊 Weekly Signal Report\n\nNo signal_log.csv found yet — nothing to report.")
        return

    df = pd.read_csv(SIGNAL_LOG_PATH)

    if df.empty:
        send_telegram_message("📊 Weekly Signal Report\n\nSignal log is empty — nothing to report this week.")
        return

    df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")
    cutoff = datetime.now(IST) - timedelta(days=7)

    # Reminder of the known limitation: outcomes are checked on a ~30 min
    # polling cycle by check_signals.py, not tick-by-tick — so closed_at
    # times are "approximately when this was detected," not exact fills.
    recent = df[df["timestamp"] >= cutoff.replace(tzinfo=None)]

    if recent.empty:
        send_telegram_message("📊 *Weekly Signal Report*\n\nNo signals generated in the past 7 days.")
        return

    closed = recent[recent["status"].isin(["TARGET_HIT", "SL_HIT"])].copy()
    still_open = recent[recent["status"] == "OPEN"]

    lines = ["📊 *Weekly Signal Report*", f"_{datetime.now(IST).strftime('%d-%b-%Y')}_", ""]

    if closed.empty:
        lines.append(f"No closed trades this week. {len(still_open)} signal(s) still open.")
    else:
        closed["pnl_pct"] = pd.to_numeric(closed["pnl_pct"], errors="coerce")
        wins = (closed["status"] == "TARGET_HIT").sum()
        total = len(closed)
        win_rate = round(wins / total * 100, 1)
        avg_pnl = round(closed["pnl_pct"].mean(), 2)

        lines.append(f"*Closed Trades:* {total}")
        lines.append(f"*Win Rate:* {win_rate}% ({wins}W / {total - wins}L)")
        lines.append(f"*Avg P&L per Trade:* {avg_pnl}%")
        lines.append(f"*Still Open:* {len(still_open)}")
        lines.append("")
        lines.append("*Per-Instrument:*")

        per_instrument = closed.groupby("instrument").agg(
            trades=("signal_id", "count"),
            wins=("status", lambda s: (s == "TARGET_HIT").sum()),
            avg_pnl=("pnl_pct", "mean")
        ).reset_index()

        for _, row in per_instrument.iterrows():
            wr = round(row["wins"] / row["trades"] * 100, 0)
            lines.append(
                f"• {row['instrument']}: {int(row['trades'])} trades, "
                f"{wr}% win, {round(row['avg_pnl'], 2)}% avg"
            )

        lines.append("")
        lines.append("*Full Trade List:*")
        for _, row in closed.sort_values("timestamp").iterrows():
            outcome_emoji = "🎯" if row["status"] == "TARGET_HIT" else "🛑"
            sign = "+" if row["pnl_pct"] >= 0 else ""
            lines.append(
                f"{outcome_emoji} {row['instrument']} ({row['signal']}) "
                f"{sign}{row['pnl_pct']}%"
            )

    report = "\n".join(lines)

    # Telegram has a ~4096 character limit per message — truncate safely
    # rather than letting the send silently fail on a long trade list.
    if len(report) > 4000:
        report = report[:3950] + "\n\n_...truncated, see dashboard for full log._"

    send_telegram_message(report)
    print("Weekly report sent.")


if __name__ == "__main__":
    main()
                                                    
