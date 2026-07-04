"""
Central configuration: secrets, constants, sector map, and commodity
definitions for the ng-signal-app dashboard.

This is the ONLY file you should usually need to touch when:
  - adding a new MCX commodity to track          -> COMMODITY_DEFINITIONS
  - adding a new sector bucket / re-mapping a symbol -> SECTOR_MAP, SECTOR_ORDER
  - changing where the signal log lives or its columns -> SIGNAL_LOG_*
  - rotating which Streamlit Secrets keys are read  -> the st.secrets.get() calls below

Nothing in this module makes network calls or renders dashboard UI — it's
pure constants/config, safe to import from anywhere. The one side effect is
reading st.secrets and emitting a single st.error() banner if the Upstox
token is missing.
"""

import streamlit as st
from zoneinfo import ZoneInfo

# ================= UPSTOX TOKEN =================
# Token is read from Streamlit's Secrets manager (Settings -> Secrets on
# share.streamlit.io), NOT hardcoded — this repo is public, so a hardcoded
# token here would be visible to anyone who opens the file on GitHub.
#
# To set it up: app dashboard -> Settings -> Secrets -> paste:
#   UPSTOX_ACCESS_TOKEN = "your_actual_token_here"
#
# FIX (previously used try/except around st.secrets[...]): newer Streamlit
# versions can raise a secrets-not-found exception type that isn't
# KeyError/FileNotFoundError, which slipped past the old except clause and
# crashed this module mid-execution — leaving SIGNAL_LOG_PATH, IST,
# GITHUB_TOKEN etc. undefined below and surfacing as a confusing ImportError
# in any file that does `from config import (...)`. st.secrets.get() with a
# default can never raise, so this can't happen again regardless of
# Streamlit version or whether secrets.toml exists at all.
UPSTOX_ACCESS_TOKEN = st.secrets.get("UPSTOX_ACCESS_TOKEN", "")
if not UPSTOX_ACCESS_TOKEN:
    st.error(
        "⚠️ UPSTOX_ACCESS_TOKEN not found in Streamlit secrets. "
        "Go to your app's Settings → Secrets and add it. "
        "The app cannot fetch live data until this is set."
    )

IST = ZoneInfo("Asia/Kolkata")

# ================= SIGNAL LOG =================
# Signal history log — lives in the repo, read by both this dashboard and
# the separate GitHub Actions outcome-checker script (check_signals.py).
SIGNAL_LOG_PATH = "signal_log.csv"
SIGNAL_LOG_COLUMNS = [
    "signal_id", "timestamp", "instrument", "instrument_key", "signal",
    "trend", "confidence", "score", "entry_price", "sl", "t1", "t2",
    "status", "closed_price", "closed_at", "pnl_pct",
    # NEW (factor tracking, point #4 — "close the loop"): the exact factor
    # readings present AT SIGNAL TIME, so compute_factor_performance() can
    # later check which ones actually correlated with wins, instead of
    # trusting the hand-tuned scoring weights on faith. load_signal_log()
    # pads these in as None for any CSV rows written before this change,
    # so no manual migration is needed.
    "daily_trend_agree", "supertrend_agree", "market_trend_agree",
    "adx", "conviction_pct", "expected_move_pct",
    # NEW (Historical Timing Engine — Phase 2 #1): PURELY observational.
    # Populated by check_signals.py *after* a signal has already closed at
    # T1, by continuing to read-only-poll price (no order ever placed, no
    # change to the trade's recorded status/pnl_pct) to see whether/when
    # price would also have reached T2. Stays empty for: signals that
    # closed at SL (T2 tracking never starts), signals still OPEN, and
    # signals where T2 wasn't reached within the tracking window
    # (check_signals.T2_TRACKING_WINDOW_DAYS) — that absence is itself
    # meaningful data, not missing data.
    "t2_hit_at",
]

# ---- GitHub push config ----
# Streamlit Community Cloud's filesystem is ephemeral and is NOT git-connected
# — writing signal_log.csv locally never reaches the GitHub repo on its own.
# These settings let the app push the CSV straight to GitHub via the Contents
# API every time it's updated, so check_signals.py (the GitHub Action) has
# something to read on its next run.
#
# Add to Streamlit Secrets (Settings -> Secrets):
#   GITHUB_TOKEN = "ghp_your_fine_grained_PAT"   (Contents: Read & write on this repo)
#   GITHUB_REPO  = "paliwalhimanshu20-droid/ng-signal-app"
GITHUB_TOKEN = st.secrets.get("GITHUB_TOKEN", "")
GITHUB_REPO = st.secrets.get("GITHUB_REPO", "")
GITHUB_BRANCH = "main"

# ================= INDEX KEY =================
# ⚠ VERIFY before relying on this: Upstox index instrument_keys are
# name-based (no ISIN), and the exact string format can drift. This is
# the commonly documented value for Nifty 50 but has NOT been confirmed
# live against your account from this environment (no network access to
# api.upstox.com here). Run the "Validate Watchlist" pattern against this
# too, or just check the very first live scan's market trend reading
# against what Nifty actually did that day.
NIFTY50_INSTRUMENT_KEY = "NSE_INDEX|Nifty 50"

# ================= COMMODITIES =================
# List of (display_name, MCX symbol filter) for commodities you want
# expiry-selectable in the dashboard. Add more tuples here later
# (e.g. ("Crude Oil", "CRUDEOIL")) — the dropdown logic
# (upstox_client.get_commodity_contracts) is generic.
COMMODITY_DEFINITIONS = [
    ("Natural Gas", "NATURALGAS"),
]

# ================= COMMODITY RISK CALIBRATION =================
# signal_logic.signal_engine()'s live BUY/SELL/WATCH gate (ADX strength
# band + ExpectedMove% sanity band) was tuned against NSE cash-equity
# behavior. MCX commodity futures — Natural Gas especially — have a
# structurally different volatility profile: ExpectedMove% (ATR/price)
# readings well above the equity-tuned 5.0% ceiling are ORDINARY on real
# NG trend days (particularly around the weekly EIA storage report),
# so leaving every instrument on the equity defaults meant the app's own
# volatility sanity filter was penalizing and gating out NG's best,
# most tradeable setups as "extreme" — not a data or math bug, a
# calibration gap.
#
# scanner.py passes this dict (via **kwargs) into signal_engine() only
# for watchlist entries whose display name contains "(MCX)" (the same
# convention watchlist.get_sector() already uses to detect commodities).
# Every NSE equity keeps using signal_logic.py's original module-level
# defaults untouched.
#
# These are calibrated STARTING POINTS based on NG's typical intraday
# range, not a backtested optimum. Re-validate/refine them with
# backtest.py once enough live NG signal history has accumulated in
# signal_log.csv — do not treat these as final.
COMMODITY_RISK_PARAMS = {
    "adx_weak_below": 18,            # commodities trend more persistently than NSE cash equities intraday; a slightly looser floor avoids penalizing genuine trend days as "choppy"
    "adx_strong_at_or_above": 25,    # unchanged from the equity default — no evidence yet this needs to differ
    "min_expected_move_pct": 0.25,   # NG is rarely truly dead; a slightly higher floor filters thin/off-peak-hour noise better than the equity 0.15%
    "max_expected_move_pct": 12.0,   # was silently inherited from the equity default (5.0%) before this fix — NG routinely exceeds that on ordinary trend/news days without it being a "gap spike"
}

# ================= SECTOR MAP =================
# Maps each watchlist symbol to the sector bucket it should appear under in
# the dashboard's "Full Scanned Universe" view. Mirrors the grouping already
# implied by the comments in watchlist.get_watchlist(). Add new symbols here
# whenever the watchlist grows — anything not listed falls into "Other".
SECTOR_MAP = {
    # Banking
    "SBIN": "Banking", "HDFCBANK": "Banking", "ICICIBANK": "Banking",
    "AXISBANK": "Banking", "KOTAKBANK": "Banking", "INDUSINDBK": "Banking",
    "BANKBARODA": "Banking",

    # IT
    "TCS": "IT", "INFY": "IT", "WIPRO": "IT", "HCLTECH": "IT", "TECHM": "IT",

    # Auto
    "TATAMOTORS": "Auto", "MARUTI": "Auto", "M&M": "Auto",
    "BAJAJ-AUTO": "Auto", "HEROMOTOCO": "Auto",

    # Pharma
    "SUNPHARMA": "Pharma", "DRREDDY": "Pharma", "CIPLA": "Pharma",
    "DIVISLAB": "Pharma", "APOLLOHOSP": "Pharma",

    # FMCG
    "ITC": "FMCG", "HINDUNILVR": "FMCG", "NESTLEIND": "FMCG",
    "BRITANNIA": "FMCG", "TATACONSUM": "FMCG",

    # Energy
    "RELIANCE": "Energy", "ONGC": "Energy", "NTPC": "Energy",
    "POWERGRID": "Energy", "COALINDIA": "Energy", "BPCL": "Energy",

    # Metals
    "TATASTEEL": "Metals", "JSWSTEEL": "Metals", "HINDALCO": "Metals",
    "VEDL": "Metals", "JINDALSTEL": "Metals",
}

# Display order for the sector accordion in the UI
SECTOR_ORDER = ["Banking", "IT", "Auto", "Pharma", "FMCG", "Energy", "Metals", "Commodities", "Other"]
