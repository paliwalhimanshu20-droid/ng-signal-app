"""
check_signals.py

Standalone script — run independently of the Streamlit app, on a schedule,
via GitHub Actions (see .github/workflows/check_signals.yml).

What it does each run:
  1. Loads signal_log.csv from the repo.
  2. For every OPEN signal, fetches the current price from Upstox.
  3. Checks if price has hit T1 (target) or SL (stop loss).
  4. Updates that row's status, closed_price, closed_at, pnl_pct.
  5. Saves signal_log.csv back to disk (the GitHub Actions workflow commits
     and pushes this change back to the repo).
  6. Sends a Telegram message for every signal that closed in this run.

Secrets required (set as GitHub Actions secrets, NOT hardcoded):
  UPSTOX_ACCESS_TOKEN   - same token your Streamlit app uses
  TELEGRAM_BOT_TOKEN    - your bot's token from @BotFather
  TELEGRAM_CHAT_ID      - the chat id to send alerts to

This script intentionally does NOT import anything from the Streamlit app
file, so it has zero dependency on `streamlit` itself and can run in a
plain GitHub Actions runner.
"""

import os
import requests
import pandas as pd
from datetime import datetime
from zoneinfo import ZoneInfo

IST = ZoneInfo("Asia/Kolkata")
SIGNAL_LOG_PATH = "signal_log.csv"

UPSTOX_ACCESS_TOKEN = os.environ.get("UPSTOX_ACCESS_TOKEN", "")
TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")


def send_telegram_message(text):
    if not TELEGRAM_BOT_TOKEN or not TELEGRAM_CHAT_ID:
        print("Telegram not configured — skipping notification.")
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


def get_current_price(instrument_key):
    """Fetch latest price from Upstox LTP endpoint. Returns None on failure."""
    if not instrument_key:
        return None

    url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={instrument_key}"
    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json",
        "Api-Version": "2.0"
    }

    try:
        r = requests.get(url, headers=headers, timeout=10)
        if r.status_code != 200:
            print(f"Price fetch failed for {instrument_key}: status {r.status_code}")
            return None
        data = r.json()
        k = list(data["data"].keys())[0]
        return data["data"][k]["last_price"]
    except Exception as e:
        print(f"Price fetch error for {instrument_key}: {e}")
        return None


def check_outcome(row, current_price):
    """
    Determine if an OPEN signal has hit its target (T1) or stop loss (SL),
    given the current price. Direction-aware:
      BUY  -> target is ABOVE entry, stop is BELOW entry
      SELL -> target is BELOW entry, stop is ABOVE entry
    Returns (new_status, pnl_pct) or (None, None) if still open.
    """
    signal = row["signal"]
    entry = float(row["entry_price"])
    sl = float(row["sl"])
    t1 = float(row["t1"])

    if signal == "BUY":
        if current_price >= t1:
            pnl_pct = round((current_price - entry) / entry * 100, 2)
            return "TARGET_HIT", pnl_pct
        if current_price <= sl:
            pnl_pct = round((current_price - entry) / entry * 100, 2)
            return "SL_HIT", pnl_pct

    elif signal == "SELL":
        if current_price <= t1:
            pnl_pct = round((entry - current_price) / entry * 100, 2)
            return "TARGET_HIT", pnl_pct
        if current_price >= sl:
            pnl_pct = round((entry - current_price) / entry * 100, 2)
            return "SL_HIT", pnl_pct

    return None, None


def main():
    if not os.path.exists(SIGNAL_LOG_PATH):
        print("No signal_log.csv found yet — nothing to check.")
        return

    if not UPSTOX_ACCESS_TOKEN:
        print("UPSTOX_ACCESS_TOKEN not set — cannot fetch prices. Exiting.")
        return

    df = pd.read_csv(SIGNAL_LOG_PATH)
    df["status"] = df["status"].astype("object")
    df["closed_at"] = df["closed_at"].astype("object")")

    if df.empty:
        print("Signal log is empty — nothing to check.")
        return

    open_mask = df["status"] == "OPEN"
    open_rows = df[open_mask]

    if open_rows.empty:
        print("No OPEN signals to check.")
        return

    print(f"Checking {len(open_rows)} open signal(s)...")

    closed_this_run = []

    for idx, row in open_rows.iterrows():
        instrument_key = row.get("instrument_key", "")

        if not instrument_key or pd.isna(instrument_key):
            print(f"Skipping {row['instrument']} — no instrument_key stored, cannot fetch price.")
            continue

        current_price = get_current_price(instrument_key)

        if current_price is None:
            print(f"Could not fetch price for {row['instrument']} — skipping this run.")
            continue

        new_status, pnl_pct = check_outcome(row, current_price)

        if new_status:
            df.at[idx, "status"] = new_status
            df.at[idx, "closed_price"] = current_price
            df.at[idx, "closed_at"] = datetime.now(IST).strftime("%Y-%m-%d %H:%M:%S")
            df.at[idx, "pnl_pct"] = pnl_pct
            closed_this_run.append((row["instrument"], row["signal"], new_status, pnl_pct, current_price))
            print(f"{row['instrument']} ({row['signal']}) -> {new_status} at {current_price} ({pnl_pct}%)")
        else:
            print(f"{row['instrument']} ({row['signal']}) -> still OPEN at {current_price}")

    df.to_csv(SIGNAL_LOG_PATH, index=False)

    for instrument, signal, status, pnl_pct, price in closed_this_run:
        emoji = "🎯" if status == "TARGET_HIT" else "🛑"
        label = "TARGET HIT" if status == "TARGET_HIT" else "STOP LOSS HIT"
        sign = "+" if pnl_pct >= 0 else ""

        msg = (
            f"{emoji} *{label}*\n"
            f"Instrument: {instrument}\n"
            f"Signal: {signal}\n"
            f"Closed Price: {price}\n"
            f"P&L: {sign}{pnl_pct}%"
        )
        send_telegram_message(msg)

    if not closed_this_run:
        print("No signals closed this run.")


if __name__ == "__main__":
    main()
