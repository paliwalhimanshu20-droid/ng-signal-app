"""
config/settings.py

Central configuration for the Instrument Master Database.
Nothing in instrument_master/*.py should hardcode paths, URLs, or watchlists —
everything lives here so it can be changed without touching module code.
"""

import os

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(BASE_DIR, "data")
DB_PATH = os.path.join(DATA_DIR, "instrument_master.db")
CLASSIFICATION_RULES_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "classification_rules.json"
)

# ---------------------------------------------------------------------------
# Data source
# ---------------------------------------------------------------------------
UPSTOX_INSTRUMENTS_URL = (
    "https://assets.upstox.com/market-quote/instruments/exchange/complete.json.gz"
)
DOWNLOAD_TIMEOUT_SECONDS = 180

# ---------------------------------------------------------------------------
# Research Priority 1 — current NG Signal Pro live instruments.
# Edit this list as your app's watchlist changes. Matched against trading_symbol.
# ---------------------------------------------------------------------------
CURRENT_WATCHLIST = [
    # Equities across your 7 sectors — replace/extend with your actual watchlist.json contents
    # "RELIANCE", "TCS", "HDFCBANK", ...
]

# MCX Natural Gas is always Priority 1 regardless of CURRENT_WATCHLIST membership,
# since it's a core NG Signal Pro instrument by definition.
CORE_COMMODITY_SYMBOLS = ["NATURALGAS", "NATGASMINI"]

# ---------------------------------------------------------------------------
# Research Priority 3 — major indices (exact trading_symbol match)
# ---------------------------------------------------------------------------
MAJOR_INDICES = [
    "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX", "BANKEX",
]

# ---------------------------------------------------------------------------
# Research Priority 4 — top NSE stocks (Nifty 50 constituents, editable)
# ---------------------------------------------------------------------------
NIFTY_50_SYMBOLS = [
    "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "INFY", "HINDUNILVR",
    "ITC", "SBIN", "BHARTIARTL", "KOTAKBANK", "LT", "AXISBANK",
    "BAJFINANCE", "ASIANPAINT", "MARUTI", "TITAN", "SUNPHARMA",
    "ULTRACEMCO", "WIPRO", "NESTLEIND", "ONGC", "NTPC", "POWERGRID",
    "M&M", "TATASTEEL", "TATAMOTORS", "ADANIENT", "ADANIPORTS",
    "COALINDIA", "JSWSTEEL", "HCLTECH", "TECHM", "BAJAJFINSV",
    "GRASIM", "DRREDDY", "CIPLA", "EICHERMOT", "BRITANNIA", "APOLLOHOSP",
    "DIVISLAB", "HEROMOTOCO", "HINDALCO", "INDUSINDBK", "BAJAJ-AUTO",
    "SBILIFE", "HDFCLIFE", "UPL", "SHREECEM", "BPCL", "TATACONSUM",
]

# ---------------------------------------------------------------------------
# SQLite tuning
# ---------------------------------------------------------------------------
# "DELETE" (default) = single .db file, matches the existing GitHub-sync
# pattern used for signal_log.csv (whole file pushed/pulled as one artifact).
# "WAL" = better concurrent read/write throughput, but creates extra
# -wal/-shm side files — only use it if this runs as a long-lived process
# rather than being synced via git as a flat file.
SQLITE_JOURNAL_MODE = "DELETE"

# ---------------------------------------------------------------------------
# MCX commodity segment/exchange identifiers used by the classifier
# ---------------------------------------------------------------------------
MCX_EXCHANGE_NAME = "MCX"
