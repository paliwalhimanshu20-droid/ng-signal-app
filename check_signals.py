"""
check_signals.py

Standalone script — run independently of the Streamlit app, on a schedule,
via GitHub Actions (see .github/workflows/check_signals.yml).

MIGRATED from signal_log.csv to the Research & Learning Database
(research_db). This still runs the exact same outcome-checking logic —
only the storage layer changed (SQLite table instead of a CSV file). It
still intentionally does NOT import anything from the Streamlit app files
(streamlit itself, signal_log.py, app.py, etc.) — research_db and
research_config are both plain Python with zero Streamlit dependency, so
this script can still run in a bare GitHub Actions runner exactly as
before.

What it does each run:
  1. Opens research_learning.db (checked out fresh from the repo by the
     Actions workflow) via research_db.
  2. For every OPEN trade, fetches the current price from Upstox.
  3. Checks if price has hit T1 (target) or SL (stop loss).
  4. Updates that row's status, closed_price, closed_at, pnl_pct.
  5. Historical Timing Engine support (purely observational, see below):
     for trades that already closed at T1 in a PREVIOUS run, keeps
     read-only-checking price (no order ever placed, no change to that
     trade's recorded status/pnl_pct) to record the timestamp at which
     price would also have reached T2, if it does within a bounded
     tracking window. This never manages, exits, or resizes a position —
     it only ever fetches a quote and writes a timestamp.
  6. Commits the DB changes and closes it — the GitHub Actions workflow
     then commits and pushes the updated .db file back to the repo (same
     role it always played for signal_log.csv).
  7. Sends a Telegram message for every trade that closed (TARGET_HIT/
     SL_HIT) in this run. T2 timing observations do NOT send a Telegram
     message — they're silent, dashboard-only data, not a new alert stream.

Secrets required (set as GitHub Actions secrets, NOT hardcoded):
  UPSTOX_ACCESS_TOKEN   - same token your Streamlit app uses
  TELEGRAM_BOT_TOKEN    - your bot's token from @BotFather
  TELEGRAM_CHAT_ID      - the chat id to send alerts to
"""

import os
import sys
import requests
from datetime import datetime
from zoneinfo import ZoneInfo

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from research_config import settings
from research_db.database import ResearchDatabase

IST = ZoneInfo("Asia/Kolkata")
TIMESTAMP_FORMAT = "%Y-%m-%d %H:%M:%S"

# How many days after a signal closes at T1 to keep read-only-checking price
# for a T2 touch, before giving up and leaving t2_hit_at empty for good.
# Empty after this window has elapsed is meaningful ("didn't reach T2 in
# time"), not missing data — signal_log.compute_timing_stats() treats it
# that way.
T2_TRACKING_WINDOW_DAYS = 3
# Maximum number of days an OPEN signal is allowed to remain active.
# After this it will automatically become EXPIRED.
OPEN_SIGNAL_EXPIRY_DAYS = 3

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


def check_outcome(trade, current_price):
    """
    Determine if an OPEN trade has hit its target (T1) or stop loss (SL),
    given the current price. Direction-aware:
      BUY  -> target is ABOVE entry, stop is BELOW entry
      SELL -> target is BELOW entry, stop is ABOVE entry
    Returns (new_status, pnl_pct) or (None, None) if still open.

    UNCHANGED from before — this is the ONLY logic that ever determines a
    trade's official outcome/status/pnl_pct. Nothing below this function
    (the T2-touch tracking) is allowed to alter what this function decides.
    """
    signal = trade["signal"]
    entry = float(trade["entry_price"])
    sl = float(trade["sl"])
    t1 = float(trade["t1"])

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


def check_t2_touch(trade, current_price):
    """
    PURELY OBSERVATIONAL — read-only. Checks whether price has reached T2,
    direction-aware, for a trade that already closed at T1. Does NOT touch
    status, closed_price, or pnl_pct — those stay exactly as recorded when
    T1 first hit. This only ever answers "did price reach T2, and when" so
    the Historical Timing Engine has real T2 data to report, never to make
    or manage a trade decision.

    Returns True if T2 has been reached, else False.
    """
    signal = trade["signal"]
    t2_raw = trade.get("t2")

    if t2_raw is None or t2_raw == "N/A" or t2_raw == "":
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


def _parse_timestamp(ts_str):
    """Returns a naive datetime, or None if unparseable/missing."""
    if not ts_str:
        return None
    try:
        return datetime.strptime(ts_str, TIMESTAMP_FORMAT)
    except (TypeError, ValueError):
        return None


def _is_t2_tracking_expired(closed_at_str):
    """True once a TARGET_HIT trade has been closed for longer than
    T2_TRACKING_WINDOW_DAYS — at that point we stop checking it for a T2
    touch for good (an expired, never-reached T2 is meaningful data, not
    a reason to poll forever)."""
    closed_at = _parse_timestamp(closed_at_str)
    if closed_at is None:
        return True  # unparseable/missing timestamp -> don't keep tracking it

    now_naive = datetime.now(IST).replace(tzinfo=None)
    age_days = (now_naive - closed_at).total_seconds() / 86400
    return age_days > T2_TRACKING_WINDOW_DAYS


def _is_open_signal_expired(timestamp_str):
    """Returns True if an OPEN trade has been active longer than
    OPEN_SIGNAL_EXPIRY_DAYS."""
    opened_at = _parse_timestamp(timestamp_str)
    if opened_at is None:
        return False

    now_naive = datetime.now(IST).replace(tzinfo=None)
    age_days = (now_naive - opened_at).total_seconds() / 86400
    return age_days > OPEN_SIGNAL_EXPIRY_DAYS


def main():
    if not UPSTOX_ACCESS_TOKEN:
        print("UPSTOX_ACCESS_TOKEN not set — cannot fetch prices. Exiting.")
        return

    db = ResearchDatabase(settings.DB_PATH, journal_mode=settings.SQLITE_JOURNAL_MODE)

    try:
        # --------------------------------------------------------
        # Expire old OPEN trades
        # --------------------------------------------------------
        open_trades = db.get_open_live_trades()

        if not open_trades:
            print("No OPEN trades — nothing to check.")
        else:
            expired_count = 0
            still_open = []

            for trade in open_trades:
                if _is_open_signal_expired(trade["timestamp"]):
                    db.mark_live_trade_expired(trade["signal_id"])
                    expired_count += 1
                else:
                    still_open.append(trade)

            if expired_count:
                print(f"{expired_count} OPEN trade(s) expired automatically.")

            # ---- Pass 1: remaining OPEN trades -> check T1/SL ----
            closed_this_run = []

            if not still_open:
                print("No OPEN trades to check.")
            else:
                print(f"Checking {len(still_open)} open trade(s)...")

                for trade in still_open:
                    instrument_key = trade.get("instrument_key")

                    if not instrument_key:
                        print(f"Skipping {trade['instrument']} — no instrument_key stored, cannot fetch price.")
                        continue

                    current_price = get_current_price(instrument_key)

                    if current_price is None:
                        print(f"Could not fetch price for {trade['instrument']} — skipping this run.")
                        continue

                    new_status, pnl_pct = check_outcome(trade, current_price)

                    if new_status:
                        closed_at = datetime.now(IST).strftime(TIMESTAMP_FORMAT)
                        db.update_live_trade_outcome(
                            trade["signal_id"], new_status, current_price, closed_at, pnl_pct
                        )
                        closed_this_run.append(
                            (trade["instrument"], trade["signal"], new_status, pnl_pct, current_price)
                        )
                        print(f"{trade['instrument']} ({trade['signal']}) -> {new_status} at {current_price} ({pnl_pct}%)")
                    else:
                        print(f"{trade['instrument']} ({trade['signal']}) -> still OPEN at {current_price}")

            # ---- Pass 2: trades already TARGET_HIT -> read-only T2 touch check ----
            # PURELY OBSERVATIONAL. See module docstring and check_t2_touch(). This
            # never reopens a position or changes status/pnl_pct for any row here.
            t2_candidates = db.get_t2_candidate_trades()
            t2_touched_this_run = []

            if not t2_candidates:
                print("No TARGET_HIT trades pending T2 observation.")
            else:
                still_in_window = [
                    t for t in t2_candidates if not _is_t2_tracking_expired(t.get("closed_at"))
                ]
                print(f"Observing {len(still_in_window)} closed trade(s) for a T2 touch "
                      f"(of {len(t2_candidates)} total, rest outside the "
                      f"{T2_TRACKING_WINDOW_DAYS}-day tracking window)...")

                for trade in still_in_window:
                    instrument_key = trade.get("instrument_key")
                    if not instrument_key:
                        continue

                    current_price = get_current_price(instrument_key)
                    if current_price is None:
                        continue

                    if check_t2_touch(trade, current_price):
                        t2_hit_at = datetime.now(IST).strftime(TIMESTAMP_FORMAT)
                        db.update_live_trade_t2_hit(trade["signal_id"], t2_hit_at)
                        t2_touched_this_run.append((trade["instrument"], trade["signal"], current_price))
                        print(f"{trade['instrument']} ({trade['signal']}) -> T2 also touched at {current_price} "
                              f"(status/pnl_pct unchanged, recorded purely for timing stats)")

            db.commit()

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

            if expired_count:
                print(f"{expired_count} trade(s) expired this run.")

            if not closed_this_run:
                print("No trades closed this run.")

            if t2_touched_this_run:
                print(f"{len(t2_touched_this_run)} trade(s) also touched T2 this run (logged, no alert sent).")

    finally:
        db.close()


if __name__ == "__main__":
    main()
