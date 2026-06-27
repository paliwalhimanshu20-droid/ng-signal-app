"""
Signal history persistence and performance analytics.

Everything related to signal_log.csv lives here: reading it, writing new
OPEN rows after a scan, pushing it to GitHub (because Streamlit Community
Cloud's filesystem is ephemeral), and the two performance breakdowns shown
on the Performance tab (overall/per-instrument win-rate, and per-factor
win-rate). check_signals.py (the GitHub Action) is the OTHER writer of this
CSV — it flips OPEN rows to TARGET_HIT/SL_HIT once price confirms an outcome.
"""

import os
import base64
import requests
import pandas as pd
import streamlit as st
from datetime import datetime

from config import (
    SIGNAL_LOG_PATH, SIGNAL_LOG_COLUMNS,
    GITHUB_TOKEN, GITHUB_REPO, GITHUB_BRANCH, IST,
)

# ================= SIGNAL LOG =================

def load_signal_log():
    """Read the signal history CSV. Returns an empty, correctly-shaped
    DataFrame if the file doesn't exist yet (first run)."""
    if not os.path.exists(SIGNAL_LOG_PATH):
        return pd.DataFrame(columns=SIGNAL_LOG_COLUMNS)

    try:
        df = pd.read_csv(SIGNAL_LOG_PATH)
        # Guard against a manually-edited/corrupted CSV missing columns
        for col in SIGNAL_LOG_COLUMNS:
            if col not in df.columns:
                df[col] = None
        return df
    except Exception:
        return pd.DataFrame(columns=SIGNAL_LOG_COLUMNS)


def push_signal_log_to_github(df, commit_message="Update signal_log.csv [app]"):
    """
    Pushes the given signal log DataFrame to signal_log.csv in the GitHub
    repo via the Contents API, so new signals logged by the app actually
    persist (and become visible to the check_signals.py GitHub Action)
    instead of only existing on Streamlit's ephemeral local filesystem.

    DEBUG VERSION: shows a visible banner on the dashboard for every
    outcome (missing secrets, GitHub API errors, success) instead of
    silently swallowing failures. Once this is confirmed working, the
    st.warning/st.error/st.success calls below can be removed or reduced
    to logging if the on-screen noise isn't wanted long-term.
    """
    if not GITHUB_TOKEN or not GITHUB_REPO:
        st.warning("⚠️ GITHUB_TOKEN or GITHUB_REPO not set in Secrets — push skipped.")
        return

    content_b64 = base64.b64encode(df.to_csv(index=False).encode()).decode()
    api_url = f"https://api.github.com/repos/{GITHUB_REPO}/contents/{SIGNAL_LOG_PATH}"
    headers = {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json"
    }

    # Need the current file's SHA to update it (GitHub requires this for
    # existing files; omit it entirely for a brand-new file).
    sha = None
    try:
        r = requests.get(api_url, headers=headers, params={"ref": GITHUB_BRANCH}, timeout=10)
        if r.status_code == 200:
            sha = r.json().get("sha")
        elif r.status_code != 404:
            st.warning(f"GitHub GET check returned {r.status_code}: {r.text[:200]}")
    except Exception as e:
        st.warning(f"GitHub GET check failed: {e}")

    payload = {
        "message": commit_message,
        "content": content_b64,
        "branch": GITHUB_BRANCH
    }
    if sha:
        payload["sha"] = sha

    try:
        r = requests.put(api_url, headers=headers, json=payload, timeout=10)
        if r.status_code in (200, 201):
            st.success("✅ signal_log.csv pushed to GitHub.")
        else:
            st.error(f"❌ GitHub push failed ({r.status_code}): {r.text[:300]}")
    except Exception as e:
        st.error(f"❌ GitHub push request failed: {e}")


def save_signal_log(df):
    df.to_csv(SIGNAL_LOG_PATH, index=False)
    push_signal_log_to_github(df)


def append_new_signals(scan_results_df):
    """
    Appends newly-generated BUY/SELL signals from this scan to the log
    as new OPEN rows. Avoids duplicate logging of the same setup by
    checking: same instrument + same signal direction + still OPEN
    already exists -> skip (don't re-log an unchanged open position).

    Only logs actionable signals (BUY/SELL), not WATCH/NO TRADE — those
    aren't real trade calls with a measurable outcome.
    """
    if scan_results_df.empty:
        return

    log_df = load_signal_log()

    actionable = scan_results_df[scan_results_df["Signal"].isin(["BUY", "SELL"])]

    new_rows = []

    for _, row in actionable.iterrows():
        # Skip if there's already an OPEN signal for this instrument + direction
        existing_open = log_df[
            (log_df["instrument"] == row["Instrument"]) &
            (log_df["signal"] == row["Signal"]) &
            (log_df["status"] == "OPEN")
        ]
        if not existing_open.empty:
            continue

        # Skip if SL/T1/T2 are N/A (invalid ATR) — can't measure an outcome
        if row["SL"] == "N/A" or row["T1"] == "N/A":
            continue

        new_rows.append({
            "signal_id": f"{row['Instrument']}_{datetime.now(IST).strftime('%Y%m%d%H%M%S')}",
            "timestamp": datetime.now(IST).strftime("%Y-%m-%d %H:%M:%S"),
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
            # NEW (point #4): factor readings at signal time, for later
            # win-rate/avg-P&L breakdowns per factor — see
            # compute_factor_performance() below.
            "daily_trend_agree": row.get("DailyTrendAgree", "N/A"),
            "supertrend_agree": row.get("SupertrendAgree", "N/A"),
            "market_trend_agree": row.get("MarketTrendAgree", "N/A"),
            "adx": row.get("ADX", "N/A"),
            "conviction_pct": row.get("ConvictionPct", "N/A"),
            "expected_move_pct": row.get("ExpectedMove%", "N/A"),
        })

    if new_rows:
        log_df = pd.concat([log_df, pd.DataFrame(new_rows)], ignore_index=True)
        save_signal_log(log_df)


def compute_performance_summary(log_df):
    """
    Returns a per-instrument and overall summary of closed signal outcomes:
    win rate and average P&L %. Only considers CLOSED (TARGET_HIT/SL_HIT)
    rows — OPEN signals have no outcome yet and are excluded from these stats.
    """
    if log_df.empty:
        return pd.DataFrame(), None

    closed = log_df[log_df["status"].isin(["TARGET_HIT", "SL_HIT"])].copy()

    if closed.empty:
        return pd.DataFrame(), None

    closed["pnl_pct"] = pd.to_numeric(closed["pnl_pct"], errors="coerce")

    per_instrument = closed.groupby("instrument").agg(
        Trades=("signal_id", "count"),
        Wins=("status", lambda s: (s == "TARGET_HIT").sum()),
        AvgPnL_Pct=("pnl_pct", "mean")
    ).reset_index()

    per_instrument["WinRate_Pct"] = round(
        (per_instrument["Wins"] / per_instrument["Trades"]) * 100, 1
    )
    per_instrument["AvgPnL_Pct"] = per_instrument["AvgPnL_Pct"].round(2)

    overall = {
        "total_trades": len(closed),
        "wins": int((closed["status"] == "TARGET_HIT").sum()),
        "win_rate_pct": round((closed["status"] == "TARGET_HIT").sum() / len(closed) * 100, 1),
        "avg_pnl_pct": round(closed["pnl_pct"].mean(), 2)
    }

    return per_instrument, overall


def compute_factor_performance(log_df):
    """
    Point #4 ("close the loop"): breaks down win rate / avg P&L% by each
    scoring factor that was recorded at signal time — daily-trend
    agreement, Supertrend agreement, market(Nifty)-trend agreement, ADX
    strength bucket, and volatility (ExpectedMove%) bucket.

    This is how you find out whether a factor is actually pulling its
    weight instead of assuming it from the hand-tuned scoring weights in
    signal_logic.py. Only CLOSED trades are considered (status
    TARGET_HIT/SL_HIT) — OPEN signals have no outcome yet.

    Returns a dict of {factor_label: DataFrame}. Empty dict if there's
    no closed history yet. Treat any breakdown with fewer than ~20 closed
    trades in a row as too small a sample to act on — it'll show up here
    well before that, but the win rates won't be statistically meaningful
    until you have more.
    """
    if log_df.empty:
        return {}

    closed = log_df[log_df["status"].isin(["TARGET_HIT", "SL_HIT"])].copy()
    if closed.empty:
        return {}

    closed["pnl_pct"] = pd.to_numeric(closed["pnl_pct"], errors="coerce")
    closed["win"] = (closed["status"] == "TARGET_HIT")

    def _breakdown(series, label_col="Factor Value"):
        sub = closed[series.notna() & (series != "N/A")].copy()
        if sub.empty:
            return pd.DataFrame()
        sub["_grp"] = series[sub.index]
        g = sub.groupby("_grp").agg(
            Trades=("win", "count"),
            Wins=("win", "sum"),
            AvgPnL_Pct=("pnl_pct", "mean")
        ).reset_index().rename(columns={"_grp": label_col})
        g["WinRate_Pct"] = round((g["Wins"] / g["Trades"]) * 100, 1)
        g["AvgPnL_Pct"] = g["AvgPnL_Pct"].round(2)
        return g[[label_col, "Trades", "Wins", "WinRate_Pct", "AvgPnL_Pct"]]

    results = {}

    for col, label in [
        ("daily_trend_agree", "Daily Trend Agreement"),
        ("supertrend_agree", "Supertrend Agreement"),
        ("market_trend_agree", "Market (Nifty) Trend Agreement"),
    ]:
        if col in closed.columns:
            df_b = _breakdown(closed[col])
            if not df_b.empty:
                results[label] = df_b

    if "adx" in closed.columns:
        adx_numeric = pd.to_numeric(closed["adx"], errors="coerce")
        bucket = pd.cut(
            adx_numeric, bins=[-1, 20, 25, 1000],
            labels=["Weak (<20)", "Moderate (20-25)", "Strong (25+)"]
        )
        df_b = _breakdown(bucket)
        if not df_b.empty:
            results["ADX Strength Bucket"] = df_b

    if "expected_move_pct" in closed.columns:
        move_numeric = pd.to_numeric(closed["expected_move_pct"], errors="coerce")
        bucket = pd.cut(
            move_numeric, bins=[-1, 0.15, 1.0, 1000],
            labels=["Low (<0.15%)", "Normal (0.15-1%)", "High (>1%)"]
        )
        df_b = _breakdown(bucket)
        if not df_b.empty:
            results["Volatility (ExpectedMove%) Bucket"] = df_b

    return results
