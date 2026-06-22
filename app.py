import streamlit as st
import requests
import pandas as pd
import json
import os
import base64
import plotly.graph_objects as go
from plotly.subplots import make_subplots
from datetime import datetime
from zoneinfo import ZoneInfo

# ================= CONFIG =================

# Token is read from Streamlit's Secrets manager (Settings -> Secrets on
# share.streamlit.io), NOT hardcoded — this repo is public, so a hardcoded
# token here would be visible to anyone who opens the file on GitHub.
#
# To set it up: app dashboard -> Settings -> Secrets -> paste:
#   UPSTOX_ACCESS_TOKEN = "your_actual_token_here"
try:
    UPSTOX_ACCESS_TOKEN = st.secrets["UPSTOX_ACCESS_TOKEN"]
except (KeyError, FileNotFoundError):
    UPSTOX_ACCESS_TOKEN = ""
    st.error(
        "⚠️ UPSTOX_ACCESS_TOKEN not found in Streamlit secrets. "
        "Go to your app's Settings → Secrets and add it. "
        "The app cannot fetch live data until this is set."
    )
IST = ZoneInfo("Asia/Kolkata")

# Signal history log — lives in the repo, read by both this dashboard and
# the separate GitHub Actions outcome-checker script (check_signals.py).
SIGNAL_LOG_PATH = "signal_log.csv"
SIGNAL_LOG_COLUMNS = [
    "signal_id", "timestamp", "instrument", "instrument_key", "signal",
    "trend", "confidence", "score", "entry_price", "sl", "t1", "t2",
    "status", "closed_price", "closed_at", "pnl_pct"
]

# ---- GitHub push config (NEW) ----
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

    Silently no-ops if GITHUB_TOKEN/GITHUB_REPO aren't configured in
    Streamlit Secrets, and silently swallows push failures (network issues,
    bad token, etc.) so a GitHub hiccup never crashes the dashboard itself.
    """
    if not GITHUB_TOKEN or not GITHUB_REPO:
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
    except Exception:
        pass

    payload = {
        "message": commit_message,
        "content": content_b64,
        "branch": GITHUB_BRANCH
    }
    if sha:
        payload["sha"] = sha

    try:
        requests.put(api_url, headers=headers, json=payload, timeout=10)
    except Exception:
        # Don't let a GitHub push failure take down the dashboard —
        # the local copy still saved fine for this session.
        pass


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
            "pnl_pct": None
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



def safe_get(url, headers=None):
    try:
        r = requests.get(url, headers=headers, timeout=10)

        if r.status_code != 200:
            return None

        return r.json()

    except Exception as e:
        # FIX: narrowed from bare except so real errors aren't silently swallowed.
        # Currently just suppressed here; surfaced to the caller as None.
        return None

# ================= INSTRUMENT MASTER =================

@st.cache_data(ttl=86400)
def load_instrument_master():
    url = "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json"
    try:
        return requests.get(url, timeout=20).json()
    except Exception as e:
        st.error(f"Instrument Master Error: {e}")
        return []


def get_commodity_contracts(name_filter, max_contracts=4):
    """
    Generic resolver for any MCX commodity (Natural Gas, Crude Oil, Gold, etc).

    Uses Upstox's Instruments Search API (v2/instruments/search) instead of
    downloading a static master file. The static MCX.json approach was
    dropped because:
      1. Upstox's MCX file is only reliably available gzipped (MCX.json.gz),
         not as plain .json — requesting the plain URL returns a non-JSON
         body (404/HTML), which is what caused the
         "Expecting value: line 1 column 1" error.
      2. Even when downloaded, MCX naming is inconsistent (duplicate/odd
         entries per Upstox community reports), making local filtering fragile.

    The search API instead lets Upstox do the filtering server-side and
    returns clean, current contracts only.

    name_filter: symbol text to search for, e.g. "NATURALGAS", "CRUDEOIL".
    Returns {"error": str|None, "contracts": [...]}. On success, "contracts"
    is a list of dicts: [{"label": "...", "key": "...", "expiry": datetime}, ...]
    sorted by nearest expiry first, limited to max_contracts. If "error" is
    set, something went wrong with the API call itself (auth/network) —
    distinct from a successful call that simply found no matching contracts.
    """
    # CONFIRMED against official Upstox docs (upstox.com/developer/api-documentation/instrument-search):
    # - Endpoint is /v2/instruments/search (NOT /v1/ - that was an earlier
    #   incorrect fix that caused a 404 "Resource not Found" error).
    # - segments value for commodities is "COMM" (NOT "COM").
    # - instrument_types value for futures is "FUT" (confirmed correct).
    url = "https://api.upstox.com/v2/instruments/search"

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json",
        "Content-Type": "application/json"
    }

    params = {
        "query": name_filter,
        "exchanges": "MCX",
        "segments": "COMM",   # MCX commodity segment (confirmed value: COMM)
        "instrument_types": "FUT",
        "page_number": 1,
        "records": 30          # API max per docs is 30
    }

    try:
        r = requests.get(url, headers=headers, params=params, timeout=15)
        if r.status_code != 200:
            # Upstox typically returns a JSON error body with details —
            # surface it so issues are diagnosable from the dashboard
            # instead of just seeing a bare status code.
            try:
                err_body = r.json()
                err_detail = err_body.get("errors", err_body)
            except Exception:
                err_detail = r.text[:200]
            return {"error": f"Search API returned status {r.status_code}: {err_detail}", "contracts": []}
        payload = r.json()
    except Exception as e:
        return {"error": f"Search API request failed: {e}", "contracts": []}

    rows = payload.get("data", []) if isinstance(payload, dict) else []

    matches = []

    for row in rows:
        try:
            symbol = (row.get("trading_symbol") or row.get("name") or "").upper()
            instrument_type = (row.get("instrument_type") or "").upper()

            if name_filter.upper() not in symbol:
                continue

            # Only futures — skip options (CE/PE) chains, this app trades direction not options
            if instrument_type and instrument_type not in ("FUT",):
                continue

            expiry_raw = row.get("expiry")
            if not expiry_raw:
                continue

            if isinstance(expiry_raw, (int, float)):
                expiry_dt = datetime.fromtimestamp(expiry_raw / 1000, tz=IST)
            else:
                expiry_dt = datetime.fromisoformat(str(expiry_raw)).replace(tzinfo=IST)

            if expiry_dt.date() < datetime.now(IST).date():
                continue

            key = row.get("instrument_key")
            if not key:
                continue

            matches.append({
                "label": f"{symbol} (exp {expiry_dt.strftime('%d-%b-%Y')})",
                "key": key,
                "expiry": expiry_dt
            })

        except Exception:
            continue

    matches.sort(key=lambda x: x["expiry"])
    return {"error": None, "contracts": matches[:max_contracts]}

def load_instrument_file():
    try:
        return pd.read_csv("instruments.csv")
    except Exception:
        return pd.DataFrame()

def get_instrument_key(symbol):
    df = load_instrument_file()
    if df.empty:
        return None

    match = df[df["trading_symbol"] == symbol]
    if match.empty:
        return None

    return match.iloc[0]["instrument_key"]

# ================= WATCHLIST =================

# List of (display_name, MCX symbol filter) for commodities you want
# expiry-selectable in the dashboard. Add more tuples here later
# (e.g. ("Crude Oil", "CRUDEOIL")) — the dropdown logic is generic.
COMMODITY_DEFINITIONS = [
    ("Natural Gas", "NATURALGAS"),
]

def get_watchlist(commodity_contracts=None):
    """
    commodity_contracts: dict mapping display_name -> selected instrument_key,
    e.g. {"Natural Gas": "MCX_FO|NATURALGAS26JUNFUT"}. Built from the
    dashboard dropdown. If None or empty, commodities are simply excluded
    from this scan (rather than guessing a key).
    """
    watchlist = {
        # ---- Existing core watchlist ----
        "ITC": "NSE_EQ|INE154A01025",
        "RELIANCE": "NSE_EQ|INE002A01018",
        "SBIN": "NSE_EQ|INE062A01020",
        "HDFCBANK": "NSE_EQ|INE040A01034",
        "ICICIBANK": "NSE_EQ|INE090A01021",
        "TCS": "NSE_EQ|INE467B01029",
        "INFY": "NSE_EQ|INE009A01021",
        "WIPRO": "NSE_EQ|INE075A01022",
        "ONGC": "NSE_EQ|INE213A01029",
        "NTPC": "NSE_EQ|INE733E01010",
        "POWERGRID": "NSE_EQ|INE752E01010",
        "TATAMOTORS": "NSE_EQ|INE155A01022",

        # ---- Banking (top 5) ----
        "AXISBANK": "NSE_EQ|INE238A01034",
        "KOTAKBANK": "NSE_EQ|INE237A01028",
        "INDUSINDBK": "NSE_EQ|INE095A01012",
        "BANKBARODA": "NSE_EQ|INE028A01039",

        # ---- IT (top 5, TCS/INFY/WIPRO already above) ----
        "HCLTECH": "NSE_EQ|INE860A01027",
        "TECHM": "NSE_EQ|INE669C01036",

        # ---- Auto (top 5, TATAMOTORS already above) ----
        "MARUTI": "NSE_EQ|INE585B01010",
        "M&M": "NSE_EQ|INE101A01026",
        "BAJAJ-AUTO": "NSE_EQ|INE917I01010",
        "HEROMOTOCO": "NSE_EQ|INE158A01026",

        # ---- Pharma (top 5) ----
        "SUNPHARMA": "NSE_EQ|INE044A01036",
        "DRREDDY": "NSE_EQ|INE089A01023",
        "CIPLA": "NSE_EQ|INE059A01026",
        "DIVISLAB": "NSE_EQ|INE361B01024",
        "APOLLOHOSP": "NSE_EQ|INE437A01024",

        # ---- FMCG (top 5, ITC already above) ----
        "HINDUNILVR": "NSE_EQ|INE030A01027",
        "NESTLEIND": "NSE_EQ|INE239A01016",
        "BRITANNIA": "NSE_EQ|INE216A01030",
        "TATACONSUM": "NSE_EQ|INE192A01025",

        # ---- Energy (top 5, ONGC/NTPC/POWERGRID already above) ----
        "COALINDIA": "NSE_EQ|INE522F01014",
        "BPCL": "NSE_EQ|INE029A01011",

        # ---- Metals (top 5) ----
        "TATASTEEL": "NSE_EQ|INE081A01012",
        "JSWSTEEL": "NSE_EQ|INE019A01038",
        "HINDALCO": "NSE_EQ|INE038A01020",
        "VEDL": "NSE_EQ|INE205A01025",
        "JINDALSTEL": "NSE_EQ|INE749A01030",
    }

    # ---- Commodities (MCX F&O), expiry chosen via dashboard dropdown ----
    if commodity_contracts:
        for display_name, key in commodity_contracts.items():
            if key:
                watchlist[f"{display_name} (MCX)"] = key

    return watchlist

# ================= SECTOR MAP (NEW) =================

# Maps each watchlist symbol to the sector bucket it should appear under in
# the dashboard's "Full Scanned Universe" view. Mirrors the grouping already
# implied by the comments in get_watchlist() above. Add new symbols here
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


def get_sector(instrument_name):
    """
    Resolves an instrument's display name (as used in get_watchlist/run_scanner)
    to its sector bucket. MCX commodity entries are named "<Display> (MCX)" by
    get_watchlist(), so those are caught explicitly before the lookup.
    """
    if "(MCX)" in instrument_name:
        return "Commodities"
    return SECTOR_MAP.get(instrument_name, "Other")

# ================= MARKET DATA =================

def get_price(key):
    url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={key}"

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json",
        "Api-Version": "2.0"
    }

    data = safe_get(url, headers)

    if not data:
        return None

    try:
        k = list(data["data"].keys())[0]
        return data["data"][k]["last_price"]
    except Exception:
        return None


def get_candles(key):
    # FIX: original code called .get() directly on safe_get()'s return value,
    # which crashes with AttributeError when safe_get returns None
    # (timeouts, holidays, no data, expired token, rate limits).
    today = datetime.now(IST).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/{today}"

    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"})

    if not data:
        return None

    return data.get("data", {}).get("candles", None)


def get_candles_range(key, days_back=5):
    """
    Fetches multiple days of 30-minute candles for the chart view, using
    Upstox's historical-candle range endpoint (from_date/to_date variant)
    rather than the single-day endpoint used by the scanner above.

    This is intentionally a SEPARATE function from get_candles() — the
    scanner's signal generation continues to use today-only data so this
    chart feature can't accidentally change scan/signal behavior.

    days_back: calendar days to look back (weekends/holidays included in
    the count, so 5 calendar days back generally covers ~3-4 trading days,
    not 5 full trading days. Increase if you want strictly more sessions.)
    """
    to_date = datetime.now(IST).strftime("%Y-%m-%d")
    from_date = (datetime.now(IST) - pd.Timedelta(days=days_back)).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/{to_date}/{from_date}"

    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"})

    if not data:
        return None

    return data.get("data", {}).get("candles", None)

# ================= INDICATORS =================

def ema(prices, period):
    m = 2 / (period + 1)
    e = prices[0]
    for p in prices[1:]:
        e = (p - e) * m + e
    return e


def atr(candles):
    # FIX: original took sum(trs[:14]) which is the OLDEST 14 true ranges
    # once trs is built in chronological order (since candles here is the
    # raw, newest-first Upstox response — trs ends up oldest->newest reversed
    # depending on indexing). We now explicitly use the most recent 14
    # true ranges so ATR reflects current volatility, not stale data.
    trs = []
    for i in range(1, len(candles)):
        h, l, pc = candles[i][1], candles[i][2], candles[i-1][4]
        trs.append(max(h - l, abs(h - pc), abs(l - pc)))

    if not trs:
        return 0

    recent = trs[-14:] if len(trs) >= 14 else trs
    return sum(recent) / len(recent)


def rsi(prices, period=14):
    # NEW: standard Wilder's RSI, computed from the same closes list
    # you already build in run_scanner(). No extra API calls.
    if len(prices) < period + 1:
        return None

    gains, losses = [], []
    for i in range(1, len(prices)):
        change = prices[i] - prices[i - 1]
        gains.append(max(change, 0))
        losses.append(max(-change, 0))

    avg_gain = sum(gains[-period:]) / period
    avg_loss = sum(losses[-period:]) / period

    if avg_loss == 0:
        return 100.0

    rs = avg_gain / avg_loss
    return round(100 - (100 / (1 + rs)), 2)


def volume_signal(candles):
    # NEW: compares the latest candle's volume to the average volume of the
    # rest of the session so far. Flags unusual participation behind a move.
    # Upstox 30min candle format: [timestamp, open, high, low, close, volume, oi]
    if not candles or len(candles) < 3:
        return None, "N/A"

    # candles are newest-first from the API
    vols = [c[5] for c in candles if len(c) > 5]
    if len(vols) < 3:
        return None, "N/A"

    latest_vol = vols[0]
    avg_vol = sum(vols[1:]) / len(vols[1:]) if len(vols) > 1 else 0

    if avg_vol == 0:
        return None, "N/A"

    ratio = round(latest_vol / avg_vol, 2)

    if ratio >= 1.5:
        tag = "High"
    elif ratio >= 0.8:
        tag = "Normal"
    else:
        tag = "Low"

    return ratio, tag

# ================= CHART =================

def build_instrument_chart(instrument_name, candles):
    """
    Builds a 3-panel Plotly chart for a single instrument:
      1. Candlestick price with EMA20/EMA50 overlay
      2. RSI(14) with 30/70 reference lines
      3. Volume bars

    candles: raw Upstox candle list (newest-first), same format used
    elsewhere in this app: [timestamp, open, high, low, close, volume, oi]

    Returns a Plotly Figure, or None if there isn't enough data to chart.
    """
    if not candles or len(candles) < 15:
        return None

    # Candles arrive newest-first from Upstox — reverse to chronological
    # order for charting (oldest on the left, newest on the right).
    ordered = list(reversed(candles))

    timestamps = [pd.to_datetime(c[0]) for c in ordered]
    opens = [c[1] for c in ordered]
    highs = [c[2] for c in ordered]
    lows = [c[3] for c in ordered]
    closes = [c[4] for c in ordered]
    volumes = [c[5] if len(c) > 5 else 0 for c in ordered]

    # EMA series across the full chronological close history (for the overlay line)
    def ema_series(prices, period):
        if len(prices) < period:
            return [None] * len(prices)
        m = 2 / (period + 1)
        out = [None] * (period - 1)
        e = sum(prices[:period]) / period
        out.append(e)
        for p in prices[period:]:
            e = (p - e) * m + e
            out.append(e)
        return out

    ema20_series = ema_series(closes, 20)
    ema50_series = ema_series(closes, 50)

    # RSI series (rolling, point-by-point) for the RSI subplot
    def rsi_series(prices, period=14):
        out = [None] * len(prices)
        if len(prices) < period + 1:
            return out
        for i in range(period, len(prices)):
            window = prices[i - period:i + 1]
            gains = [max(window[j] - window[j - 1], 0) for j in range(1, len(window))]
            losses = [max(window[j - 1] - window[j], 0) for j in range(1, len(window))]
            avg_gain = sum(gains) / period
            avg_loss = sum(losses) / period
            if avg_loss == 0:
                out[i] = 100.0
            else:
                rs = avg_gain / avg_loss
                out[i] = round(100 - (100 / (1 + rs)), 2)
        return out

    rsi_vals = rsi_series(closes, 14)

    fig = make_subplots(
        rows=3, cols=1,
        shared_xaxes=True,
        row_heights=[0.55, 0.2, 0.25],
        vertical_spacing=0.04,
        subplot_titles=(f"{instrument_name} — Price & EMA", "RSI (14)", "Volume")
    )

    # --- Panel 1: Candlestick + EMA overlay ---
    fig.add_trace(go.Candlestick(
        x=timestamps, open=opens, high=highs, low=lows, close=closes,
        name="Price", showlegend=False
    ), row=1, col=1)

    fig.add_trace(go.Scatter(
        x=timestamps, y=ema20_series, mode="lines", name="EMA20",
        line=dict(color="#3498db", width=1.5)
    ), row=1, col=1)

    fig.add_trace(go.Scatter(
        x=timestamps, y=ema50_series, mode="lines", name="EMA50",
        line=dict(color="#e67e22", width=1.5)
    ), row=1, col=1)

    # --- Panel 2: RSI ---
    fig.add_trace(go.Scatter(
        x=timestamps, y=rsi_vals, mode="lines", name="RSI",
        line=dict(color="#9b59b6", width=1.5), showlegend=False
    ), row=2, col=1)

    fig.add_hline(y=70, line_dash="dot", line_color="red", row=2, col=1)
    fig.add_hline(y=30, line_dash="dot", line_color="green", row=2, col=1)

    # --- Panel 3: Volume ---
    bar_colors = [
        "#2ecc71" if closes[i] >= opens[i] else "#e74c3c"
        for i in range(len(closes))
    ]
    fig.add_trace(go.Bar(
        x=timestamps, y=volumes, name="Volume", marker_color=bar_colors,
        showlegend=False
    ), row=3, col=1)

    fig.update_layout(
        height=650,
        margin=dict(l=10, r=10, t=40, b=10),
        xaxis_rangeslider_visible=False,
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1)
    )

    return fig



@st.cache_data(ttl=3600)  # cached for 1 hour — daily trend doesn't change intraday,
# this avoids re-fetching daily candles on every single scan click, which
# would otherwise roughly double the API calls per scan (38 instruments x
# 1 extra call each). Cache key includes the date implicitly via ttl reset.
def get_daily_trend(key):
    """
    Fetches recent DAILY candles (not 30-min) and computes EMA20/EMA50 on
    that higher timeframe, to check whether the broader trend agrees with
    the 30-min signal. This is the "higher-timeframe filter" — trading
    WITH the bigger trend reduces (does not eliminate) false counter-trend
    signals, a well-established practice, not a guarantee.

    Returns "Bullish", "Bearish", or None if not enough daily data exists
    (e.g. a newly-listed instrument, or fewer than 50 trading days available).
    """
    to_date = datetime.now(IST).strftime("%Y-%m-%d")
    from_date = (datetime.now(IST) - pd.Timedelta(days=120)).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{key}/day/{to_date}/{from_date}"

    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"})

    if not data:
        return None

    candles = data.get("data", {}).get("candles", None)

    if not candles or len(candles) < 50:
        return None

    closes = [c[4] for c in reversed(candles)]  # chronological order

    daily_ema20 = ema(closes, 20)
    daily_ema50 = ema(closes, 50)

    return "Bullish" if daily_ema20 > daily_ema50 else "Bearish"


# ================= REGIME =================

def detect_regime(ema20, ema50, price):
    gap = abs(ema20 - ema50)

    if gap > price * 0.01:
        return "TRENDING"
    elif gap > price * 0.005:
        return "BREAKOUT"
    else:
        return "RANGING"

# ================= SIGNAL ENGINE =================

def signal_engine(price, ema20, ema50, atr_val, daily_trend=None):
    """
    daily_trend: "Bullish", "Bearish", or None (unavailable). When provided
    and it DISAGREES with the 30-min trend, the signal is downgraded —
    BUY/SELL becomes WATCH at most, since we're now fighting the bigger
    trend. This does not block WATCH/NO TRADE signals, only caps how
    confident an actionable (BUY/SELL) call can be.
    """

    score = 4
    reasons = ["Base Score"]

    trend = "Bullish" if ema20 > ema50 else "Bearish"
    regime = detect_regime(ema20, ema50, price)

    expected_move = round((atr_val / price) * 100, 2)

    if (ema20 > ema50 and price > ema20) or (ema20 < ema50 and price < ema20):
        score += 2
        reasons.append("Trend confirmation")

    if atr_val and abs(price - ema20) < atr_val * 1.5:
        score += 2
        reasons.append("Valid volatility zone")

    if ema20 > ema50 and price > ema50:
        score += 2
        reasons.append("Momentum bullish")

    if ema20 < ema50 and price < ema50:
        score += 2
        reasons.append("Momentum bearish")

    # NEW: higher-timeframe agreement check
    daily_agrees = (daily_trend is None) or (daily_trend == trend)

    if daily_trend is not None:
        if daily_agrees:
            score += 1
            reasons.append(f"Daily trend agrees ({daily_trend})")
        else:
            score -= 2
            reasons.append(f"⚠ Daily trend disagrees ({daily_trend}) — downgraded")

    score = max(0, min(score, 10))
    probability = int((score / 10) * 100)

    if score >= 8 and daily_agrees:
        signal = "BUY" if trend == "Bullish" else "SELL"
    elif score >= 6:
        signal = "WATCH"
    else:
        signal = "NO TRADE"

    return signal, score, probability, trend, regime, expected_move, reasons

# ================= LEVELS =================

def levels(price, atr_val, signal, trend, regime="TRENDING"):
    """
    regime: "TRENDING", "BREAKOUT", or "RANGING" — from detect_regime().
    Position sizing now adapts to regime instead of using one fixed
    multiplier for every market condition:
      - TRENDING: wider targets (price has room to run)
      - BREAKOUT: standard sizing
      - RANGING: tighter targets/stops (price isn't trending far, so
        wide targets in a RANGING market are unrealistic and wide stops
        risk more than the setup justifies)
    """
    # FIX: previously, when atr_val was 0 (or None) — which happens for
    # instruments with too few/flat candles — risk became 0, so SL/T1/T2
    # all collapsed to exactly `price`. That looked like "missing" data in
    # the table. Now we explicitly return None so the UI can show
    # "N/A" instead of a misleading repeated price.
    if not atr_val or atr_val <= 0:
        return None, None, None

    # Regime-based risk multiplier and target stretch
    if regime == "RANGING":
        risk_mult = 1.0
        t1_mult, t2_mult = 1.5, 2.0
    elif regime == "BREAKOUT":
        risk_mult = 1.5
        t1_mult, t2_mult = 2.0, 3.0
    else:  # TRENDING
        risk_mult = 1.5
        t1_mult, t2_mult = 2.5, 4.0

    risk = atr_val * risk_mult

    if trend == "Bullish":
        return (
            round(price - risk, 2),
            round(price + risk * t1_mult, 2),
            round(price + risk * t2_mult, 2)
        )
    else:
        return (
            round(price + risk, 2),
            round(price - risk * t1_mult, 2),
            round(price - risk * t2_mult, 2)
        )

# ================= SCANNER =================

def run_scanner(commodity_contracts=None):
    """
    Returns a tuple: (top5_df, full_df)
    full_df now includes EVERY stock that returned valid data, with RSI
    and Volume columns added, regardless of score — so the dashboard can
    show the full scanned universe with filters, not just the top 5.

    commodity_contracts: dict of display_name -> instrument_key, built from
    the dashboard's expiry dropdowns. Passed straight through to get_watchlist().
    """

    watchlist = get_watchlist(commodity_contracts)
    all_results = []

    for name, key in watchlist.items():

        if not key:
            continue

        instrument_key = key

        try:
            candles = get_candles(instrument_key)

            if not candles:
                continue

            closes = [c[4] for c in reversed(candles)]

            if len(closes) < 50:
                continue

            price = get_price(instrument_key)

            if not price:
                continue

            ema20 = ema(closes, 20)
            ema50 = ema(closes, 50)
            atr_val = atr(candles)

            # NEW indicators
            rsi_val = rsi(closes, 14)
            vol_ratio, vol_tag = volume_signal(candles)

            # NEW: higher-timeframe (daily) trend filter — cached, so this
            # doesn't multiply API calls on every scan within the same day.
            daily_trend = get_daily_trend(instrument_key)

            signal, score, prob, trend, regime, expected_move, reasons = signal_engine(
                price,
                ema20,
                ema50,
                atr_val,
                daily_trend
            )

            sl, t1, t2 = levels(price, atr_val, signal, trend, regime)

            # FIX: SL/T1/T2 can now be None (invalid ATR) — guard RR calc
            if sl is None:
                rr = None
            else:
                risk = abs(price - sl)
                reward = abs(t1 - price)
                rr = round(reward / risk, 2) if risk > 0 else 0

            confidence = (
                "High" if score >= 9
                else "Medium" if score >= 7
                else "Low"
            )

            all_results.append({
                "Instrument": name,
                "InstrumentKey": instrument_key,
                "Sector": get_sector(name),
                "DailyTrend": daily_trend if daily_trend else "N/A",
                "Signal": signal,
                "Confidence": confidence,
                "Trend": trend,
                "Regime": regime,
                "Score": score,
                "Prob%": prob,
                "RSI": rsi_val,
                "Volume Ratio": vol_ratio,
                "Volume": vol_tag,
                "ExpectedMove%": expected_move,
                "RR": rr if rr is not None else "N/A",
                "Price": round(price, 2),
                "SL": sl if sl is not None else "N/A",
                "T1": t1 if t1 is not None else "N/A",
                "T2": t2 if t2 is not None else "N/A",
                "Reason": " | ".join(reasons)
            })

        except Exception as e:
            st.error(f"{name} Error: {e}")
            continue

    full_df = pd.DataFrame(all_results)

    if not full_df.empty:
        full_df = full_df.sort_values(["Score", "Prob%"], ascending=False)

    # Top 5 strong setups only (same filter logic as before: score >= 7, actionable signal)
    if not full_df.empty:
        top5_df = full_df[
            (full_df["Score"] >= 7) & (full_df["Signal"].isin(["BUY", "SELL", "WATCH"]))
        ].head(5)
    else:
        top5_df = full_df

    return top5_df, full_df

# ================= UI =================

st.title("📊 Production Trading System v1")

# =========================
# COMMODITY EXPIRY SELECTION (NEW)
# =========================

st.subheader("⚙️ Commodity Contract Selection")

commodity_contracts = {}

if not COMMODITY_DEFINITIONS:
    st.caption("No commodities configured.")
else:
    cols = st.columns(len(COMMODITY_DEFINITIONS))

    for idx, (display_name, symbol_filter) in enumerate(COMMODITY_DEFINITIONS):
        with cols[idx]:
            result = get_commodity_contracts(symbol_filter, max_contracts=4)
            contracts = result["contracts"]

            if result["error"]:
                st.error(f"{display_name}: {result['error']}")
                continue

            if not contracts:
                st.warning(f"No live {display_name} futures contracts found.")
                continue

            chosen_label = st.selectbox(
                f"{display_name} expiry",
                options=[c["label"] for c in contracts],
                key=f"expiry_{symbol_filter}"
            )

            # Map the chosen label back to its instrument key
            chosen = next(c for c in contracts if c["label"] == chosen_label)
            commodity_contracts[display_name] = chosen["key"]

st.markdown("---")

_watchlist_size = len(get_watchlist(commodity_contracts))
st.caption(
    f"Scanning {_watchlist_size} instruments (NSE equities across 7 sectors"
    f"{' + selected MCX commodity contract(s)' if commodity_contracts else ''})."
)

if "scan_count" not in st.session_state:
    st.session_state.scan_count = 0

if "last_scan" not in st.session_state:
    st.session_state.last_scan = "Never"

run = st.button("🚀 Run Live Scan")

if run:
    st.session_state.scan_count += 1
    st.session_state.last_scan = datetime.now(IST).strftime("%d-%m-%Y %H:%M:%S")

df, full_df = run_scanner(commodity_contracts)

# Log any new actionable (BUY/SELL) signals from this scan to signal_log.csv.
# save_signal_log() (called inside append_new_signals) now pushes the
# updated CSV straight to GitHub via the Contents API — see
# push_signal_log_to_github() above — so this persists across redeploys
# and is visible to the check_signals.py GitHub Action.
if not full_df.empty:
    append_new_signals(full_df)

# =========================
# FULL SCANNED UNIVERSE — now grouped by sector (NEW)
# =========================

st.subheader("🔎 Full Scanned Universe")

if full_df.empty:
    st.warning("No data returned from scanner. Check token / market hours / connectivity.")
else:
    fcol1, fcol2, fcol3 = st.columns(3)

    with fcol1:
        signal_filter = st.multiselect(
            "Signal",
            options=sorted(full_df["Signal"].unique()),
            default=list(sorted(full_df["Signal"].unique()))
        )

    with fcol2:
        confidence_filter = st.multiselect(
            "Confidence",
            options=sorted(full_df["Confidence"].unique()),
            default=list(sorted(full_df["Confidence"].unique()))
        )

    with fcol3:
        min_score = st.slider("Minimum Score", min_value=0, max_value=10, value=0)

    filtered_df = full_df[
        (full_df["Signal"].isin(signal_filter)) &
        (full_df["Confidence"].isin(confidence_filter)) &
        (full_df["Score"] >= min_score)
    ].reset_index(drop=True)

    display_cols = [
        "Instrument", "Signal", "Confidence", "Trend", "DailyTrend", "Regime",
        "Score", "Prob%", "RSI", "Volume", "Volume Ratio",
        "ExpectedMove%", "RR", "Price", "SL", "T1", "T2"
    ]

    st.caption(f"Showing {len(filtered_df)} of {len(full_df)} scanned instruments. "
               f"👇 Expand a sector and click a row to view its chart below.")

    # Tracks the most recently clicked row across all sector tables this run.
    selected_name = None
    selected_key = None

    for sector in SECTOR_ORDER:
        sector_df = filtered_df[filtered_df["Sector"] == sector].reset_index(drop=True)

        if sector_df.empty:
            continue

        with st.expander(f"{sector}  ·  {len(sector_df)} instrument(s)", expanded=False):
            sel_event = st.dataframe(
                sector_df[display_cols],
                use_container_width=True,
                hide_index=True,
                on_select="rerun",
                selection_mode="single-row",
                key=f"universe_table_{sector}"
            )

            rows = sel_event.selection.get("rows", []) if sel_event else []
            if rows:
                selected_row = sector_df.iloc[rows[0]]
                selected_name = selected_row["Instrument"]
                selected_key = selected_row.get("InstrumentKey", "")

    # =========================
    # INSTRUMENT CHART (NEW) — shown when a row is clicked in any sector above
    # =========================

    if selected_name:
        selected_idx_lookup = filtered_df[filtered_df["Instrument"] == selected_name]
        if not selected_idx_lookup.empty:
            selected_row = selected_idx_lookup.iloc[0]
            selected_key = selected_row.get("InstrumentKey", selected_key)

        st.markdown("---")
        st.subheader(f"📊 {selected_name} — Chart & Indicators")

        if not selected_key:
            st.warning("No instrument key available for this row — can't fetch chart data.")
        else:
            with st.spinner(f"Loading {selected_name} chart..."):
                chart_candles = get_candles_range(selected_key, days_back=7)

            if not chart_candles:
                st.warning(
                    f"Could not load chart data for {selected_name}. "
                    f"This can happen outside market hours, on holidays, or if the "
                    f"range endpoint isn't available for this instrument type."
                )
            else:
                fig = build_instrument_chart(selected_name, chart_candles)
                if fig:
                    st.plotly_chart(fig, use_container_width=True)
                else:
                    st.info(f"Not enough candle history yet to chart {selected_name}.")


st.markdown("---")

# =========================
# SIGNAL PERFORMANCE TRACKING (NEW)
# =========================

st.subheader("📈 Signal Performance (Historical)")

signal_log_df = load_signal_log()

if signal_log_df.empty:
    st.info(
        "No signal history yet. As BUY/SELL signals are generated, they're logged "
        "automatically. Win rate and P&L% will appear here once signals have been "
        "checked against price (handled by the scheduled outcome-checker — see setup notes)."
    )
else:
    open_count = (signal_log_df["status"] == "OPEN").sum()
    per_instrument, overall = compute_performance_summary(signal_log_df)

    if overall is None:
        st.info(
            f"{open_count} signal(s) currently OPEN, none closed yet. "
            f"Performance stats appear once signals hit their target or stop loss."
        )
    else:
        pc1, pc2, pc3, pc4 = st.columns(4)
        with pc1:
            st.metric("Closed Trades", overall["total_trades"])
        with pc2:
            st.metric("Win Rate", f"{overall['win_rate_pct']}%")
        with pc3:
            st.metric("Avg P&L per Trade", f"{overall['avg_pnl_pct']}%")
        with pc4:
            st.metric("Currently Open", int(open_count))

        st.markdown("**Per-Instrument Breakdown**")
        st.dataframe(
            per_instrument.rename(columns={
                "instrument": "Instrument",
                "Trades": "Trades",
                "Wins": "Wins",
                "WinRate_Pct": "Win Rate %",
                "AvgPnL_Pct": "Avg P&L %"
            }),
            use_container_width=True,
            hide_index=True
        )

    with st.expander("View raw signal log"):
        st.dataframe(signal_log_df, use_container_width=True, hide_index=True)

st.markdown("---")

# =========================
# EXISTING TOP-5 SECTIONS (unchanged logic)
# =========================

if df.empty:

    st.warning("No strong setups found")

else:

    # =========================
    # BUY SETUPS
    # =========================

    buy_df = df[df["Signal"] == "BUY"]

    if not buy_df.empty:

        st.subheader("🟢 BUY Opportunities")

        for _, row in buy_df.iterrows():

          st.markdown(
            f"""
            <div style="
            padding:15px;
            border-radius:10px;
            border:1px solid #2ecc71;
            margin-bottom:10px;
            ">
            <h4>🟢 {row['Instrument']}</h4>
            <b>BUY</b><br>
            Price: {row['Price']}<br>
            Confidence: {row['Prob%']}%<br>
            RSI: {row['RSI']}<br>
            Volume: {row['Volume']}<br>
            RR: {row['RR']}
            </div>
            """,
            unsafe_allow_html=True
          )

    # =========================
    # SELL SETUPS
    # =========================

    sell_df = df[df["Signal"] == "SELL"]

    if not sell_df.empty:

        st.subheader("🔴 SELL Opportunities")

        for _, row in sell_df.iterrows():

          st.markdown(
            f"""
            <div style="
            padding:15px;
            border-radius:10px;
            border:1px solid #e74c3c;
            margin-bottom:10px;
            ">
            <h4>🔴 {row['Instrument']}</h4>
            <b>SELL</b><br>
            Price: {row['Price']}<br>
            Confidence: {row['Prob%']}%<br>
            RSI: {row['RSI']}<br>
            Volume: {row['Volume']}<br>
            RR: {row['RR']}
            </div>
            """,
            unsafe_allow_html=True
          )

    # =========================
    # BEST SETUP
    # =========================

    best = df.iloc[0]

    st.markdown("---")
    st.subheader("🥇 Best Trade Setup")

    c1, c2, c3 = st.columns(3)

    with c1:
        st.metric("Instrument", best["Instrument"])
        st.metric("Signal", best["Signal"])

    with c2:
        st.metric("Entry", best["Price"])
        st.metric("SL", best["SL"])

    with c3:
        st.metric("T1", best["T1"])
        st.metric("T2", best["T2"])

    st.info(
        f"Trend: {best['Trend']}\n\n"
        f"Confidence: {best['Prob%']}%\n\n"
        f"RSI: {best['RSI']} | Volume: {best['Volume']}\n\n"
        f"RR: {best['RR']}\n\n"
        f"Reason: {best['Reason']}"
    )

    st.markdown("---")

    st.caption(
        f"Scans Run: {st.session_state.scan_count} | "
        f"Last Scan: {st.session_state.last_scan}"
    )
