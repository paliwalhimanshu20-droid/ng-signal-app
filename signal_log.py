"""
Signal history persistence and performance analytics.

MIGRATED from CSV to the Research & Learning Database (research_db). This
used to read/write signal_log.csv directly; it now reads/writes the
`live_trades` table in research_learning.db instead (see
research_db/migrations.py's migration_002_add_live_trades_table for the
schema). Streamlit Community Cloud's filesystem is still ephemeral, so the
GitHub-push pattern that used to sync signal_log.csv now syncs the .db file
the same way.

Every function below keeps its EXACT original name/signature/return shape
(a pandas DataFrame with the same columns SIGNAL_LOG_COLUMNS always had) —
app.py and reports.py needed zero changes for this migration. Only the
storage backend changed: load_signal_log()/save_signal_log()/
push_signal_log_to_github() now talk to research_db instead of a CSV file.
compute_performance_summary(), compute_factor_performance(), and
compute_timing_stats() are UNCHANGED — they only ever operated on the
DataFrame, never on the file format underneath it.

check_signals.py (the GitHub Action outcome-checker) and weekly_summary.py
(the weekly Telegram report) do NOT import this module — both are
deliberately dependency-free from the Streamlit app (see their own
docstrings) and talk to research_db directly. They were migrated
separately; see those files.
"""

import os
import base64
import requests
import pandas as pd
import streamlit as st

from config import (
    SIGNAL_LOG_COLUMNS,
    GITHUB_TOKEN, GITHUB_REPO, GITHUB_BRANCH, IST,
)
from research_config import settings as research_settings
from research_db.database import ResearchDatabase

# ================= SIGNAL LOG (now backed by research_db) =================

def _open_db():
    return ResearchDatabase(research_settings.DB_PATH, journal_mode=research_settings.SQLITE_JOURNAL_MODE)


def load_signal_log():
    """
    Reads all live trades from research_db's live_trades table and returns
    them as a DataFrame shaped exactly like the old signal_log.csv read
    (same columns, same order, same OPEN-first-by-timestamp ordering) —
    every downstream analytics function depends on this shape being
    unchanged. Returns an empty, correctly-shaped DataFrame if the DB has
    no trades yet (first run), matching the old CSV behavior.
    """
    try:
        db = _open_db()
        try:
            records = db.get_all_live_trades()
        finally:
            db.close()

        if not records:
            return pd.DataFrame(columns=SIGNAL_LOG_COLUMNS)

        df = pd.DataFrame(records)

        # Guard against any DB rows missing a column the app now expects
        # (e.g. after a future schema addition) — same padding behavior
        # load_signal_log() always had for old CSVs.
        for col in SIGNAL_LOG_COLUMNS:
            if col not in df.columns:
                df[col] = None

        # Drop SQLite's internal surrogate columns (id, created_at) that
        # signal_log.csv never had, so the returned shape matches exactly.
        extra_cols = [c for c in ("id", "created_at") if c in df.columns]
        if extra_cols:
            df = df.drop(columns=extra_cols)

        return df[SIGNAL_LOG_COLUMNS]

    except Exception:
        return pd.DataFrame(columns=SIGNAL_LOG_COLUMNS)


def push_research_db_to_github(commit_message="Update research_learning.db [app]"):
    """
    Pushes research_learning.db to GitHub via the Contents API — the same
    role push_signal_log_to_github() used to play for signal_log.csv.
    Binary file, so it's read and base64-encoded directly rather than
    encoded from a DataFrame like the old CSV push was.

    DEBUG VERSION: shows a visible banner on the dashboard for every
    outcome (missing secrets, GitHub API errors, success), matching the
    old CSV push's behavior.
    """
    if not GITHUB_TOKEN or not GITHUB_REPO:
        st.warning("⚠️ GITHUB_TOKEN or GITHUB_REPO not set in Secrets — push skipped.")
        return

    db_path = research_settings.DB_PATH
    if not os.path.exists(db_path):
        st.warning(f"⚠️ {db_path} doesn't exist locally yet — push skipped.")
        return

    with open(db_path, "rb") as f:
        content_b64 = base64.b64encode(f.read()).decode()

    # Path inside the repo, relative to repo root — DB_PATH is an absolute
    # local path (BASE_DIR/data/research_learning.db), so re-derive the
    # repo-relative form the same way research_config.settings computes it.
    repo_relative_path = os.path.join("data", os.path.basename(db_path))

    api_url = f"https://api.github.com/repos/{GITHUB_REPO}/contents/{repo_relative_path}"
    headers = {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json"
    }

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
            st.success("✅ research_learning.db pushed to GitHub.")
        else:
            st.error(f"❌ GitHub push failed ({r.status_code}): {r.text[:300]}")
    except Exception as e:
        st.error(f"❌ GitHub push request failed: {e}")


def save_signal_log(df):
    """
    Kept for compatibility with append_new_signals() below, which still
    builds a DataFrame and calls this the same way it always did. Instead
    of writing a CSV, this upserts every row into live_trades by
    signal_id (insert if new, update outcome fields if it already
    exists), then pushes the .db file to GitHub.

    In practice this is only ever called with newly-appended OPEN rows
    from append_new_signals() — check_signals.py updates outcomes directly
    via research_db's update_live_trade_outcome()/update_live_trade_t2_hit(),
    not through this function.
    """
    if df.empty:
        return

    db = _open_db()
    try:
        for _, row in df.iterrows():
            existing = db.conn.execute(
                "SELECT id FROM live_trades WHERE signal_id = ?", (row["signal_id"],)
            ).fetchone()

            if existing:
                db.update_live_trade_outcome(
                    row["signal_id"], row["status"], row.get("closed_price"),
                    row.get("closed_at"), row.get("pnl_pct"),
                )
                if pd.notna(row.get("t2_hit_at")):
                    db.update_live_trade_t2_hit(row["signal_id"], row["t2_hit_at"])
            else:
                db.insert_live_trade(row.to_dict())

        db.commit()
    finally:
        db.close()

    push_research_db_to_github()


def append_new_signals(scan_results_df):
    """
    UNCHANGED logic from before — appends newly-generated BUY/SELL signals
    from this scan as new OPEN rows, skipping duplicates (same instrument +
    direction already OPEN) and invalid setups (SL/T1 == "N/A"). Only the
    storage call at the end (save_signal_log) changed what it does
    underneath.
    """
    if scan_results_df.empty:
        return

    log_df = load_signal_log()

    actionable = scan_results_df[scan_results_df["Signal"].isin(["BUY", "SELL"])]

    new_rows = []

    for _, row in actionable.iterrows():
        existing_open = log_df[
            (log_df["instrument"] == row["Instrument"]) &
            (log_df["signal"] == row["Signal"]) &
            (log_df["status"] == "OPEN")
        ]
        if not existing_open.empty:
            continue

        if row["SL"] == "N/A" or row["T1"] == "N/A":
            continue

        new_rows.append({
            "signal_id": f"{row['Instrument']}_{pd.Timestamp.now(tz=IST).strftime('%Y%m%d%H%M%S')}",
            "timestamp": pd.Timestamp.now(tz=IST).strftime("%Y-%m-%d %H:%M:%S"),
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
        })

    if new_rows:
        log_df = pd.concat([log_df, pd.DataFrame(new_rows)], ignore_index=True)
        save_signal_log(log_df)


# ================= ANALYTICS (UNCHANGED — pure DataFrame logic) =================
# Everything below this line is copied verbatim from the pre-migration
# signal_log.py. None of it knows or cares whether load_signal_log() came
# from a CSV or a database — it only ever operated on the DataFrame shape
# SIGNAL_LOG_COLUMNS defines, which the new load_signal_log() still
# guarantees above.

def compute_timing_stats(log_df):
    """
    Historical Timing Engine (roadmap Phase 2, item #1).

    Reports, in hours, how long closed signals actually took to resolve:
      - "t1": timestamp -> closed_at, for rows where status == TARGET_HIT
      - "sl": timestamp -> closed_at, for rows where status == SL_HIT
      - "t2": timestamp -> t2_hit_at, for rows where t2_hit_at is set

    t1/sl work immediately from data the app already had — no schema or
    check_signals.py change needed for those two.

    t2_hit_at is new and PURELY OBSERVATIONAL: it's populated by
    check_signals.py continuing to read-only-poll price for a bounded
    window AFTER a signal has already closed at T1, solely to record
    whether/when price also reached T2. It never reopens the position or
    changes that signal's already-recorded status/pnl_pct — see
    check_signals.py's module docstring and check_t2_touch(). Rows where
    T2 was never reached (or the signal closed at SL, or is still OPEN,
    or the tracking window expired) simply have no t2_hit_at and are
    correctly excluded here, not treated as zero.

    Returns {"t1": stat_or_None, "t2": stat_or_None, "sl": stat_or_None}
    where each stat is {"avg_hours", "median_hours", "n"}.
    """
    empty_result = {"t1": None, "t2": None, "sl": None}
    if log_df.empty:
        return empty_result

    df = log_df.copy()
    df["_start"] = pd.to_datetime(df["timestamp"], errors="coerce")
    df["_closed"] = pd.to_datetime(df["closed_at"], errors="coerce")
    df["_t2"] = pd.to_datetime(df["t2_hit_at"], errors="coerce") if "t2_hit_at" in df.columns else pd.NaT

    def _summary(mask, end_col):
        sub = df[mask & df["_start"].notna() & df[end_col].notna()]
        if sub.empty:
            return None
        hours = (sub[end_col] - sub["_start"]).dt.total_seconds() / 3600
        hours = hours[hours >= 0]  # guard against any bad/clock-skew rows
        if hours.empty:
            return None
        return {
            "avg_hours": round(float(hours.mean()), 1),
            "median_hours": round(float(hours.median()), 1),
            "n": int(len(hours)),
        }

    return {
        "t1": _summary(df["status"] == "TARGET_HIT", "_closed"),
        "sl": _summary(df["status"] == "SL_HIT", "_closed"),
        "t2": _summary(pd.Series(True, index=df.index), "_t2"),
    }


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


def get_admin_kpis():
    df = load_signal_log()

    if df.empty:
        return {
            "total_trades": 0,
            "win_rate": 0,
            "avg_pnl": 0,
            "open_trades": 0,
            "target_hits": 0,
            "sl_hits": 0
        }

    closed = df[df["status"].isin(["TARGET_HIT", "SL_HIT"])]

    total = len(df)
    open_trades = len(df[df["status"] == "OPEN"])
    target_hits = len(df[df["status"] == "TARGET_HIT"])
    sl_hits = len(df[df["status"] == "SL_HIT"])

    win_rate = round((target_hits / len(closed)) * 100, 1) if len(closed) > 0 else 0

    closed = closed.copy()
    closed["pnl_pct"] = pd.to_numeric(closed["pnl_pct"], errors="coerce")
    avg_pnl = round(closed["pnl_pct"].mean(), 2) if not closed.empty else 0

    return {
        "total_trades": total,
        "win_rate": win_rate,
        "avg_pnl": avg_pnl,
        "open_trades": open_trades,
        "target_hits": target_hits,
        "sl_hits": sl_hits
    }
