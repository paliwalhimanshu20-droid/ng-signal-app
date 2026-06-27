"""
check_signals.py

Standalone script — run independently of the Streamlit app, on a schedule,
via GitHub Actions (see .github/workflows/check_signals.yml).

What it does each run:
  1. Loads signal_log.csv from the repo.
  2. For every OPEN signal, fetches the current price from Upstox.
  3. Checks if price has hit T1 (target) or SL (stop loss).
  4. Updates that row's status, closed_price, closed_at, pnl_pct.
  5. NEW — Historical Timing Engine support (purely observational, see
     below): for signals that already closed at T1 in a PREVIOUS run, keeps
     read-only-checking price (no order ever placed, no change to that
     signal's recorded status/pnl_pct) to record the timestamp at which
     price would also have reached T2, if it does within a bounded
     tracking window. This never manages, exits, or resizes a position —
     it only ever fetches a quote and writes a timestamp to a CSV column.
  6. Saves signal_log.csv back to disk (the GitHub Actions workflow commits
     and pushes this change back to the repo).
  7. Sends a Telegram message for every signal that closed (TARGET_HIT/
     SL_HIT) in this run. T2 timing observations do NOT send a Telegram
     message — they're silent, dashboard-only data, not a new alert stream.

Secrets required (set as GitHub Actions secrets, NOT hardcoded):
  UPSTOX_ACCESS_TOKEN   - same token your Streamlit app uses
  TELEGRAM_BOT_TOKEN    - your bot's token from @BotFather
  TELEGRAM_CHAT_ID      - the chat id to send alerts to

This script intentionally does NOT import anything from the Streamlit app
files, so it has zero dependency on `streamlit` itself and can run in a
plain GitHub Actions runner.
"""

import os
import requests
import pandas as pd
from datetime import datetime
from zoneinfo import ZoneInfo

IST = ZoneInfo("Asia/Kolkata")
SIGNAL_LOG_PATH = "signal_log.csv"

# How many days after a signal closes at T1 to keep read-only-checking price
# for a T2 touch, before giving up and leaving t2_hit_at empty for good.
# Empty after this window has elapsed is meaningful ("didn't reach T2 in
# time"), not missing data — compute_timing_stats() in signal_log.py treats
# it that way.
T2_TRACKING_WINDOW_DAYS = 3

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

    UNCHANGED from before — this is the ONLY logic that ever determines a
    signal's official outcome/status/pnl_pct. Nothing below this function
    (the T2-touch tracking) is allowed to alter what this function decides.
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


def check_t2_touch(row, current_price):
    """
    PURELY OBSERVATIONAL — read-only. Checks whether price has reached T2,
    direction-aware, for a signal that already closed at T1. Does NOT touch
    status, closed_price, or pnl_pct — those stay exactly as recorded when
    T1 first hit. This only ever answers "did price reach T2, and when" so
    the Historical Timing Engine has real T2 data to report, never to make
    or manage a trade decision.

    Returns True if T2 has been reached, else False.
    """
    signal = row["signal"]
    t2_raw = row.get("t2")

    if pd.isna(t2_raw) or t2_raw == "N/A" or t2_raw == "":
        return False

    try:
        t2 = float(t2_raw)
    except (TypeError, ValueError):
        return False

    if signal == "BUY":
        return current_price >= t2
    elif signal == "SELL":
        return current_price <= t2

    return False


def _is_t2_tracking_expired(closed_at_str):
    """True once a TARGET_HIT signal has been closed for longer than
    T2_TRACKING_WINDOW_DAYS — at that point we stop checking it for a T2
    touch for good (an expired, never-reached T2 is meaningful data, not
    a reason to poll forever)."""
    try:
        closed_at = pd.to_datetime(closed_at_str)
    except Exception:
        return True  # unparseable timestamp -> don't keep tracking it

    now_naive = datetime.now(IST).replace(tzinfo=None)
    age_days = (now_naive - closed_at).total_seconds() / 86400
    return age_days > T2_TRACKING_WINDOW_DAYS


def main():
    if not os.path.exists(SIGNAL_LOG_PATH):
        print("No signal_log.csv found yet — nothing to check.")
        return

    if not UPSTOX_ACCESS_TOKEN:
        print("UPSTOX_ACCESS_TOKEN not set — cannot fetch prices. Exiting.")
        return

    df = pd.read_csv(SIGNAL_LOG_PATH)
    df["status"] = df["status"].astype("object")
    df["closed_at"] = df["closed_at"].astype("object")

    # Backward-compat: pad in t2_hit_at for any CSV written before this
    # column existed, same pattern signal_log.load_signal_log() uses.
    if "t2_hit_at" not in df.columns:
        df["t2_hit_at"] = None
    df["t2_hit_at"] = df["t2_hit_at"].astype("object")

    if df.empty:
        print("Signal log is empty — nothing to check.")
        return

    # ---- Pass 1: OPEN signals -> check T1/SL (UNCHANGED logic) ----
    open_mask = df["status"] == "OPEN"
    open_rows = df[open_mask]

    closed_this_run = []

    if open_rows.empty:
        print("No OPEN signals to check.")
    else:
        print(f"Checking {len(open_rows)} open signal(s)...")

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

    # ---- Pass 2: signals already TARGET_HIT -> read-only T2 touch check ----
    # PURELY OBSERVATIONAL. See module docstring and check_t2_touch(). This
    # never reopens a position or changes status/pnl_pct for any row here.
    t2_candidates_mask = (
        (df["status"] == "TARGET_HIT") &
        (df["t2_hit_at"].isna() | (df["t2_hit_at"] == ""))
    )
    t2_candidates = df[t2_candidates_mask]

    t2_touched_this_run = []

    if t2_candidates.empty:
        print("No TARGET_HIT signals pending T2 observation.")
    else:
        still_in_window = t2_candidates[
            ~t2_candidates["closed_at"].apply(_is_t2_tracking_expired)
        ]
        print(f"Observing {len(still_in_window)} closed signal(s) for a T2 touch "
              f"(of {len(t2_candidates)} total, rest outside the "
              f"{T2_TRACKING_WINDOW_DAYS}-day tracking window)...")

        for idx, row in still_in_window.iterrows():
            instrument_key = row.get("instrument_key", "")

            if not instrument_key or pd.isna(instrument_key):
                continue

            current_price = get_current_price(instrument_key)
            if current_price is None:
                continue

            if check_t2_touch(row, current_price):
                df.at[idx, "t2_hit_at"] = datetime.now(IST).strftime("%Y-%m-%d %H:%M:%S")
                t2_touched_this_run.append((row["instrument"], row["signal"], current_price))
                print(f"{row['instrument']} ({row['signal']}) -> T2 also touched at {current_price} "
                      f"(status/pnl_pct unchanged, recorded purely for timing stats)")

    df.to_csv(SIGNAL_LOG_PATH, index=False)

    # ---- Telegram alerts: only for official T1/SL closes, same as before ----
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
    if t2_touched_this_run:
        print(f"{len(t2_touched_this_run)} signal(s) also touched T2 this run (logged, no alert sent).")


if __name__ == "__main__":
    main()
  
