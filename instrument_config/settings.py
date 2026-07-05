"""
instrument_config/settings.py — Instrument Master Database configuration.

Deliberately separate from research_config/settings.py (Research & Learning
Database, NGSP-003A.2) to keep the two modules fully independent.

BUG FIX (this session): this file was previously a byte-for-byte copy of
research_config/settings.py — a leftover from the original config/ folder
split. That meant:
  1. DB_PATH pointed at research_learning.db instead of this module's own
     instrument_master.db, so scripts/run_update.py and
     scripts/query_examples.py would have written/read the Instrument
     Master into the wrong database file.
  2. CLASSIFICATION_RULES_PATH and every watchlist constant used by
     instrument_master/classifier.py (CURRENT_WATCHLIST,
     CORE_COMMODITY_SYMBOLS, MCX_EXCHANGE_NAME, MAJOR_INDICES,
     NIFTY_50_SYMBOLS) and instrument_master/update_engine.py
     (UPSTOX_INSTRUMENTS_URL, DOWNLOAD_TIMEOUT_SECONDS) were missing
     entirely, causing AttributeError on every classification/priority call.
This file restores the correct, distinct content for the Instrument Master
module.
"""

import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
DB_PATH = os.path.join(DATA_DIR, "instrument_master.db")

# "DELETE" = single .db file, matches the git-sync pattern used elsewhere in
# NG Signal Pro (e.g. signal_log.csv). Switch to "WAL" only if this runs as
# a long-lived process with concurrent readers/writers.
SQLITE_JOURNAL_MODE = "DELETE"

# ---- Classification rule set (sector/industry/commodity-group mapping) ----
CLASSIFICATION_RULES_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "classification_rules.json"
)

# ---- Upstox instrument master download ----
UPSTOX_INSTRUMENTS_URL = "https://assets.upstox.com/market-quote/instruments/exchange/complete.json.gz"
DOWNLOAD_TIMEOUT_SECONDS = 30

# ---- Research-priority watchlists (used by classifier.assign_research_priority) ----
# NOTE: mirrors the live NG Signal Pro scanner watchlist in the root
# config.py's SECTOR_MAP as a flat symbol list (classifier only needs
# membership, not sector grouping). If the live watchlist changes, update
# both places until they're unified behind one source of truth.
CURRENT_WATCHLIST = [
    "SBIN", "HDFCBANK", "ICICIBANK", "AXISBANK", "KOTAKBANK", "INDUSINDBK", "BANKBARODA",
    "TCS", "INFY", "WIPRO", "HCLTECH", "TECHM",
    "TATAMOTORS", "MARUTI", "M&M", "BAJAJ-AUTO", "HEROMOTOCO",
    "SUNPHARMA", "DRREDDY", "CIPLA", "DIVISLAB", "APOLLOHOSP",
    "ITC", "HINDUNILVR", "NESTLEIND", "BRITANNIA", "TATACONSUM",
    "RELIANCE", "ONGC", "NTPC", "POWERGRID", "COALINDIA", "BPCL",
    "TATASTEEL", "JSWSTEEL", "HINDALCO", "VEDL", "JINDALSTEL",
]

CORE_COMMODITY_SYMBOLS = ["NATURALGAS"]

MCX_EXCHANGE_NAME = "MCX"

MAJOR_INDICES = ["NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX"]

# NOTE: Nifty 50 constituents change periodically (index reshuffles). This
# list is a reasonable snapshot for research-priority purposes only — it is
# NOT used for any live trading/signal logic, only to rank which instruments
# get research attention first. Verify/refresh periodically if precision
# matters for your use case.
NIFTY_50_SYMBOLS = [
    "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "INFY", "HINDUNILVR", "ITC",
    "SBIN", "BHARTIARTL", "KOTAKBANK", "LT", "AXISBANK", "BAJFINANCE",
    "ASIANPAINT", "MARUTI", "TITAN", "SUNPHARMA", "ULTRACEMCO", "NESTLEIND",
    "WIPRO", "ONGC", "NTPC", "M&M", "TATAMOTORS", "TATASTEEL", "POWERGRID",
    "HCLTECH", "TECHM", "ADANIENT", "ADANIPORTS", "COALINDIA", "BAJAJFINSV",
    "GRASIM", "DRREDDY", "CIPLA", "EICHERMOT", "BRITANNIA", "APOLLOHOSP",
    "DIVISLAB", "HEROMOTOCO", "HINDALCO", "JSWSTEEL", "BPCL", "SBILIFE",
    "HDFCLIFE", "INDUSINDBK", "TATACONSUM", "UPL", "BAJAJ-AUTO", "SHRIRAMFIN",
]
