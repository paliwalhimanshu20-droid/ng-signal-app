"""
All direct network calls to Upstox (plus the one call to Upstox's public,
unauthenticated NSE instrument-master asset). Nothing in here touches
signal-scoring logic or renders dashboard UI — if Upstox changes an
endpoint, an auth header, or a response shape, this is the only file
that needs to change.

Functions:
    safe_get                 — shared GET wrapper with surfaced errors
    load_instrument_master   — full NSE instrument master (cached 24h)
    validate_watchlist_keys  — cross-checks hardcoded watchlist keys vs. master
    get_commodity_contracts  — resolves live MCX futures contracts (e.g. Natural Gas)
    load_instrument_file     — reads local instruments.csv (if present)
    get_instrument_key       — symbol -> instrument_key lookup via instruments.csv
    get_price                — single LTP
    get_prices_bulk          — LTP for many instruments in one call
    get_candles              — today's 30-min candles (scanner use)
    get_candles_range        — multi-day 30-min candles (chart use)
    get_daily_trend          — per-instrument daily EMA20/50 trend (cached 1h)
    get_market_trend         — Nifty 50 daily EMA20/50 trend (cached 1h)
"""

import requests
import streamlit as st
import pandas as pd
import gzip
import json
from datetime import datetime

from config import UPSTOX_ACCESS_TOKEN, IST, NIFTY50_INSTRUMENT_KEY
from signal_logic import ema


def safe_get(url, headers=None):
    # UPDATED: surfaces the real failure (status code + response body, or
    # exception) instead of silently returning None. This is what exposed
    # the 429 rate-limit issue — previously every failure looked identical
    # ("no data"), with no way to tell auth/rate-limit/network apart.
    try:
        r = requests.get(url, headers=headers, timeout=10)

        if r.status_code != 200:
            st.error(f"API failed ({r.status_code}) for {url}\n{r.text[:300]}")
            return None

        return r.json()

    except Exception as e:
        st.error(f"API exception for {url}\n{e}")
        return None

# ================= INSTRUMENT MASTER =================

@st.cache_data(ttl=86400)
def load_instrument_master():
    """
    Fetches Upstox's official NSE instrument master file, used by
    validate_watchlist_keys() to catch hardcoded instrument_key drift
    (see that function's docstring). Public, unauthenticated S3-hosted
    asset — no auth headers required.

    NOTE: the uncompressed .json object on this bucket has been
    deprecated by Upstox — requesting it now returns a 403 AccessDenied
    (S3's standard response for a missing object key when the bucket
    denies anonymous s3:ListBucket, so it presents as a permissions
    error rather than a plain 404, which is a bit misleading). Every
    current example in Upstox's own docs/community uses the gzipped
    .json.gz path instead, so that's what's fetched here — decompressed
    locally before parsing, same resulting data shape as before.
    """
    url = "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json.gz"

    try:
        response = requests.get(url, timeout=20)

        if response.status_code != 200:
            st.error(
                f"Instrument master fetch failed ({response.status_code}) for {url}\n"
                f"{response.text[:300]}"
            )
            return []

        raw_bytes = gzip.decompress(response.content)
        return json.loads(raw_bytes)

    except Exception as e:
        st.error(f"Instrument Master Error: {e}")
        return []


def validate_watchlist_keys(watchlist):
    """
    Cross-checks every hardcoded NSE equity instrument_key in the watchlist
    against Upstox's official NSE instrument master file (load_instrument_master(),
    already defined above but previously unused elsewhere in this app).

    This catches drift caused by corporate actions (stock splits, ISIN
    re-issuance, delistings, symbol changes) that silently break a single
    hardcoded key — the only symptom otherwise is a 400 "Invalid Instrument
    key" error for that one instrument, discovered one at a time during a
    live scan. This checks all of them in one shot, with zero extra cost
    against Upstox's rate-limited endpoints (the master file is a public,
    unauthenticated static asset, cached 24h).

    MCX commodity entries are skipped — those are resolved dynamically via
    get_commodity_contracts() already, not hardcoded, so they can't drift
    the same way.

    Returns a list of (name, hardcoded_key, live_key_or_reason) tuples for
    anything that doesn't match. Empty list = everything checks out.
    Returns None if the master file itself couldn't be fetched.
    """
    master = load_instrument_master()
    if not master:
        return None

    lookup = {}
    for row in master:
        sym = row.get("trading_symbol")
        key = row.get("instrument_key")
        if sym and key:
            lookup[sym] = key

    mismatches = []
    for name, key in watchlist.items():
        if "(MCX)" in name:
            continue

        live_key = lookup.get(name)
        if live_key is None:
            mismatches.append((name, key, "NOT FOUND in NSE master — symbol may have changed/delisted"))
        elif live_key != key:
            mismatches.append((name, key, live_key))

    return mismatches


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

            # BUGFIX: this was `< datetime.now(IST).date()`, which only
            # excludes contracts that have ALREADY expired — a contract
            # expiring TODAY still passed through and showed up as a
            # selectable option. Using <= rolls it off the list on its
            # expiry day itself, so the dropdown always shows the next
            # *tradeable-beyond-today* contract.
            if expiry_dt.date() <= datetime.now(IST).date():
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


def get_prices_bulk(keys):
    """
    Fetches LTP for MANY instruments in a single API call instead of one
    call per instrument. Upstox's LTP endpoint accepts a comma-separated
    list and supports up to 500 instrument keys per request — our whole
    ~39-instrument watchlist fits in exactly one call.

    This replaces 39 separate get_price() calls (one per instrument, inside
    the scanner loop) with a single call made once at the start of
    run_scanner(). Doesn't touch get_candles() — there's no bulk equivalent
    for historical candle data, each instrument still needs its own call
    for that — but this still meaningfully cuts total request volume per
    scan, which matters after hitting Upstox's rate limit.

    Returns {instrument_key: last_price} for whatever came back successfully.
    Missing/failed instruments are just absent from the dict — callers
    should treat a missing key the same as get_price() returning None.
    """
    if not keys:
        return {}

    joined = ",".join(keys)
    url = f"https://api.upstox.com/v2/market-quote/ltp?instrument_key={joined}"

    headers = {
        "Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}",
        "Accept": "application/json",
        "Api-Version": "2.0"
    }

    data = safe_get(url, headers)

    if not data:
        return {}

    prices = {}
    try:
        for entry in data.get("data", {}).values():
            ikey = entry.get("instrument_token")
            price = entry.get("last_price")
            if ikey and price is not None:
                prices[ikey] = price
    except Exception:
        pass

    return prices


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


@st.cache_data(ttl=3600)
def get_market_trend():
    """
    Broad-market regime filter (point #1 from the "near-perfect signals"
    discussion): is Nifty 50 itself trending up or down on the daily
    timeframe? A single stock's BUY signal fighting a falling index is a
    bigger headwind than any stock-level filter alone catches — this is
    usually the single highest-leverage context check a pure stock-level
    scanner is missing.

    Computed ONCE per scan (cached 1h, same pattern as get_daily_trend),
    not once per instrument — it's the same index for every stock in the
    watchlist.

    Returns "Bullish", "Bearish", or None if the index data couldn't be
    fetched or there isn't enough daily history.
    """
    to_date = datetime.now(IST).strftime("%Y-%m-%d")
    from_date = (datetime.now(IST) - pd.Timedelta(days=120)).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{NIFTY50_INSTRUMENT_KEY}/day/{to_date}/{from_date}"
    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"})

    if not data:
        return None

    candles = data.get("data", {}).get("candles", None)
    if not candles or len(candles) < 50:
        return None

    closes = [c[4] for c in reversed(candles)]
    e20 = ema(closes, 20)
    e50 = ema(closes, 50)

    return "Bullish" if e20 > e50 else "Bearish"
    
