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
import logging
import time
from datetime import datetime

from config import UPSTOX_ACCESS_TOKEN, IST, NIFTY50_INSTRUMENT_KEY
from signal_logic import ema

logger = logging.getLogger(__name__)


def _mask_token(headers):
    """
    DIAGNOSTIC-ONLY helper (PR 8 final diagnostic patch). Returns a copy of
    `headers` with the Authorization value masked to
    "Bearer eyJh...bcd1" (first 4 / last 4 chars of the token visible) so
    logs never contain a usable credential. Does not touch the real
    `headers` dict used for the actual request — read-only, for printing.
    """
    if not headers:
        return headers
    masked = dict(headers)
    auth = masked.get("Authorization")
    if isinstance(auth, str) and auth.lower().startswith("bearer "):
        token = auth[7:]
        if len(token) > 10:
            masked["Authorization"] = f"Bearer {token[:4]}...{token[-4:]}"
        elif token:
            masked["Authorization"] = "Bearer ***(short/empty-looking token)***"
        else:
            masked["Authorization"] = "Bearer <EMPTY>"
    return masked


def _parse_candle_url(url):
    """
    DIAGNOSTIC-ONLY, best-effort, read-only parse of a
    /historical-candle/{key}/{interval}/... URL for logging purposes.
    Returns (instrument_key, interval) or (None, None) if the URL doesn't
    match that shape (e.g. price/market-trend endpoints) — never raises,
    never affects the actual request.
    """
    try:
        if "/historical-candle/" not in url:
            return None, None
        tail = url.split("/historical-candle/", 1)[1]
        parts = tail.split("/")
        instrument_key = parts[0] if len(parts) > 0 else None
        interval = parts[1] if len(parts) > 1 else None
        return instrument_key, interval
    except Exception:
        return None, None


_HTTP_STATUS_LABELS = {
    400: "400 Bad Request (malformed request)",
    401: "401 Unauthorized (invalid/expired/missing token)",
    403: "403 Forbidden (token lacks permission for this resource)",
    404: "404 Not Found (endpoint or instrument key not found)",
    429: "429 Too Many Requests (rate limited)",
    500: "500 Internal Server Error (Upstox-side)",
    502: "502 Bad Gateway (Upstox-side)",
    503: "503 Service Unavailable (Upstox-side)",
}


def safe_get(url, headers=None, label=None):
    """
    INVESTIGATION INSTRUMENTATION (PR 8 final diagnostic patch — logging
    only, NO behavior change): every Upstox HTTP call in this app funnels
    through this one function, so instrumenting it here, once, captures a
    complete request/response trace for every call without touching any
    caller's logic, the request itself, or the return value.

    IMPORTANT: this patch replaces the previous `logger.info()`/
    `logger.warning()` diagnostic calls with plain `print()` calls.
    `logger.info()` was proven (prior investigation session) to be
    silently dropped in generate_research.py's execution context because
    nothing in that call chain configures a logging handler — the lines
    were being computed but never actually appearing anywhere. `print()`
    goes straight to stdout, which GitHub Actions always captures in the
    step log with zero configuration required, in Streamlit, in a plain
    script, or anywhere else Python runs. This is the only way to
    guarantee these diagnostics are "always visible" as required.

    The actual network call (`requests.get(url, headers=headers,
    timeout=10)`), the success/failure branching, the `st.error()` calls,
    and the return values (`r.json()` on success, `None` on failure) are
    unchanged from before this patch. Only what gets printed is
    new/expanded.
    """
    request_start_perf = time.perf_counter()
    request_start_iso = datetime.now(IST).isoformat()
    instrument_key, interval = _parse_candle_url(url)
    masked_headers = _mask_token(headers)

    print(flush=True)
    print("=" * 70, flush=True)
    print("REQUEST", flush=True)
    print("=" * 70, flush=True)
    print(f"label            : {label}", flush=True)
    print(f"url              : {url}", flush=True)
    print(f"http_method      : GET", flush=True)
    print(f"instrument_key   : {instrument_key}", flush=True)
    print(f"interval         : {interval}", flush=True)
    print(f"headers          : {masked_headers}", flush=True)
    print(f"request_start    : {request_start_iso}", flush=True)

    try:
        r = requests.get(url, headers=headers, timeout=10)
        elapsed_ms = round((time.perf_counter() - request_start_perf) * 1000, 1)

        parsed_json = None
        try:
            parsed_json = r.json()
        except Exception:
            parsed_json = None

        print("-" * 70, flush=True)
        print("RESPONSE", flush=True)
        print("-" * 70, flush=True)
        print(f"status_code      : {r.status_code}"
              + (f"  -> {_HTTP_STATUS_LABELS[r.status_code]}" if r.status_code in _HTTP_STATUS_LABELS else ""),
              flush=True)
        print(f"response_headers : {dict(r.headers)}", flush=True)
        print(f"elapsed_ms       : {elapsed_ms}", flush=True)
        print(f"body_first_1000  : {r.text[:1000]!r}", flush=True)
        print(f"body_json        : {parsed_json if parsed_json is not None else '(not valid JSON)'}", flush=True)

        if r.status_code != 200:
            print(f"RESULT           : REQUEST FAILED — non-200 status {r.status_code}", flush=True)
            print("=" * 70, flush=True)
            st.error(f"API failed ({r.status_code}) for {url}\n{r.text[:300]}")
            return None

        candles = None
        if isinstance(parsed_json, dict):
            candles = parsed_json.get("data", {}).get("candles", None)

        if candles is not None:
            print(f"candle_count     : {len(candles)}", flush=True)
            if len(candles) == 0:
                print("RESULT           : REQUEST SUCCEEDED (HTTP 200) but candles list is EMPTY", flush=True)
            else:
                # Upstox returns candles newest-first; labeled plainly as
                # "first/last in returned array" rather than asserting
                # chronological order here.
                print(f"first_candle_in_array (typically most recent) : {candles[0][0]}", flush=True)
                print(f"last_candle_in_array (typically oldest)       : {candles[-1][0]}", flush=True)
                print("RESULT           : REQUEST SUCCEEDED with data", flush=True)
        else:
            print("RESULT           : REQUEST SUCCEEDED (HTTP 200) but response has no 'candles' key "
                  "(not a candle endpoint, or unexpected response shape)", flush=True)

        print("=" * 70, flush=True)
        return parsed_json

    except Exception as e:
        elapsed_ms = round((time.perf_counter() - request_start_perf) * 1000, 1)
        timed_out = isinstance(e, requests.exceptions.Timeout)
        connection_error = isinstance(e, requests.exceptions.ConnectionError)

        print("-" * 70, flush=True)
        print("RESPONSE", flush=True)
        print("-" * 70, flush=True)
        print(f"RESULT           : REQUEST FAILED — no HTTP response received", flush=True)
        print(f"exception_type   : {type(e).__name__}", flush=True)
        print(f"exception_detail : {e}", flush=True)
        print(f"timed_out        : {timed_out}", flush=True)
        print(f"connection_error : {connection_error}", flush=True)
        print(f"elapsed_ms       : {elapsed_ms}", flush=True)
        print("=" * 70, flush=True)

        # UNCHANGED from before this investigation — exact same message,
        # exact same single except-block shape. Only the printing above is new.
        st.error(f"API exception for {url}\n{e}")
        return None

# ================= INSTRUMENT MASTER =================

@st.cache_data(ttl=86400)
def load_instrument_master():
    """
    Fetches Upstox's official NSE instrument master file, used by
    validate_watchlist_keys() to catch hardcoded instrument_key drift
    (see that function's docstring).

    URL/format verified directly against Upstox's current official docs
    (https://upstox.com/developer/api-documentation/instruments) as of
    this fix: the gzipped NSE.json.gz path below is the ONLY NSE BOD
    instrument link listed there — the plain uncompressed .json path
    is gone from the docs entirely (not just slow/broken — actually
    removed as a documented endpoint). No authentication is documented
    or required for this asset; it's a public static file.

    ROOT CAUSE OF THE 403 ACCESSDENIED XML (found by comparing a raw
    `requests.get()` call, which failed, against a fetch using ordinary
    browser-like headers, which succeeded and returned valid gzip data):
    this is NOT Upstox denying access to the object. It's the CDN in
    front of assets.upstox.com blocking the request before it reaches
    S3, because Python's `requests` library sends `User-Agent:
    python-requests/X.Y.Z` by default — a signature CDN/WAF bot filters
    commonly reject on public static-asset buckets. The bucket returns
    an S3-style AccessDenied error page for the block, which reads like
    a permissions problem but isn't one — the object is public and the
    URL is correct. Sending a standard browser User-Agent (below) is
    the fix; nothing about the URL, auth, or response format needed to
    change.
    """
    url = "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json.gz"
    headers = {
        # Ordinary desktop-browser User-Agent -- see docstring above for
        # why this specific header is what was actually causing the 403.
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        ),
        "Accept": "application/gzip, application/octet-stream, */*",
    }

    # ---- Network-level failures (DNS, connection refused, timeout) ----
    # Caught separately from HTTP-level failures below: these never get a
    # response object at all, so they need their own message rather than
    # falling into the "check response.status_code" path.
    try:
        response = requests.get(url, headers=headers, timeout=20)
    except requests.exceptions.Timeout:
        st.error(
            f"Instrument master fetch timed out after 20s ({url}). "
            f"Upstox's asset CDN may be slow or temporarily unreachable — try again shortly. "
            f"The rest of the app continues to work; only 'Validate Watchlist Instrument Keys' is affected."
        )
        return []
    except requests.exceptions.ConnectionError as e:
        st.error(
            f"Network error reaching Upstox's instrument master ({url}): {e}\n"
            f"Check connectivity to assets.upstox.com. The rest of the app continues to work."
        )
        return []
    except requests.exceptions.RequestException as e:
        st.error(f"Unexpected network error fetching instrument master ({url}): {e}")
        return []

    # ---- HTTP-level failures: distinguish WHY, not just THAT it failed ----
    if response.status_code != 200:
        content_type = response.headers.get("content-type", "")
        body_preview = response.text[:300]

        if "xml" in content_type.lower() and "AccessDenied" in response.text:
            # The specific failure this fix targets -- see the module-level
            # docstring above for the confirmed root cause. Distinguished
            # from a genuine auth failure below because the fix for THIS
            # case is "the CDN blocked the request", not "check credentials"
            # -- there are no credentials involved in this call at all.
            st.error(
                f"Instrument master blocked by Upstox's CDN (HTTP {response.status_code}, "
                f"XML AccessDenied) for {url}.\n\n"
                f"This is CDN/WAF-level bot filtering, not a real permissions or "
                f"authentication issue — this file is public and unauthenticated. "
                f"If this fix (a standard browser User-Agent header) stops working "
                f"again in the future, Upstox's CDN provider may have tightened its "
                f"bot-filtering rules further; check the response body below for clues.\n\n"
                f"Response body: {body_preview}"
            )
        elif response.status_code in (401, 403):
            st.error(
                f"Instrument master fetch returned {response.status_code} for {url}.\n"
                f"This asset is documented as public/unauthenticated, so a persistent "
                f"401/403 here (that ISN'T the XML AccessDenied case above) may mean "
                f"Upstox has changed this endpoint to require authentication — check "
                f"https://upstox.com/developer/api-documentation/instruments for updates.\n\n"
                f"Content-Type: {content_type}\nResponse body: {body_preview}"
            )
        else:
            st.error(
                f"Instrument master fetch failed (HTTP {response.status_code}) for {url}\n"
                f"Content-Type: {content_type}\nResponse body: {body_preview}"
            )
        return []

    # ---- Response-parsing failures (bad gzip, bad JSON) ----
    try:
        raw_bytes = gzip.decompress(response.content)
    except (OSError, EOFError) as e:
        # gzip.BadGzipFile is a subclass of OSError -- catching OSError
        # covers it across supported Python versions without importing it
        # by name.
        st.error(
            f"Instrument master response wasn't valid gzip data ({url}): {e}\n"
            f"Content-Type: {response.headers.get('content-type', '')}\n"
            f"First 300 chars: {response.text[:300]}"
        )
        return []

    try:
        return json.loads(raw_bytes)
    except json.JSONDecodeError as e:
        st.error(f"Instrument master decompressed but wasn't valid JSON ({url}): {e}")
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


@st.cache_data(ttl=3600)  # PERFORMANCE (audit finding): this made a live,
# uncached, direct requests.get() call (bypassing safe_get() entirely, so
# it had no diagnostic logging either) on EVERY rerun anywhere in the app
# — tab_settings renders unconditionally regardless of active tab, same
# as every other st.tabs()-body-always-executes case fixed this session.
# Contracts don't change intraday (same reasoning get_daily_trend/
# get_market_trend already use at this same ttl), so caching costs
# nothing in freshness and removes a real, previously invisible network
# call from every single interaction in the app.
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

    request_start_perf = time.perf_counter()
    request_start_iso = datetime.now(IST).isoformat()
    logger.info(
        "API_DIAGNOSTIC_REQUEST | label=COMMODITY_CONTRACTS (%s) | url=%s | request_start=%s",
        name_filter, url, request_start_iso,
    )

    try:
        r = requests.get(url, headers=headers, params=params, timeout=15)
        elapsed_ms = round((time.perf_counter() - request_start_perf) * 1000, 1)
        if r.status_code != 200:
            # Upstox typically returns a JSON error body with details —
            # surface it so issues are diagnosable from the dashboard
            # instead of just seeing a bare status code.
            try:
                err_body = r.json()
                err_detail = err_body.get("errors", err_body)
            except Exception:
                err_detail = r.text[:200]
            logger.info(
                "API_DIAGNOSTIC_RESPONSE | label=COMMODITY_CONTRACTS (%s) | url=%s | "
                "request_start=%s | elapsed_ms=%.1f | returned=True | timed_out=False | "
                "status_code=%s | headers=%s | body_snippet=%r",
                name_filter, url, request_start_iso, elapsed_ms, r.status_code, dict(r.headers), r.text[:300],
            )
            return {"error": f"Search API returned status {r.status_code}: {err_detail}", "contracts": []}
        logger.info(
            "API_DIAGNOSTIC_RESPONSE | label=COMMODITY_CONTRACTS (%s) | url=%s | "
            "request_start=%s | elapsed_ms=%.1f | returned=True | timed_out=False | "
            "status_code=%s | headers=%s | body_snippet=None",
            name_filter, url, request_start_iso, elapsed_ms, r.status_code, dict(r.headers),
        )
        payload = r.json()
    except Exception as e:
        elapsed_ms = round((time.perf_counter() - request_start_perf) * 1000, 1)
        timed_out = isinstance(e, requests.exceptions.Timeout)
        logger.warning(
            "API_DIAGNOSTIC_RESPONSE | label=COMMODITY_CONTRACTS (%s) | url=%s | "
            "request_start=%s | elapsed_ms=%.1f | returned=False | timed_out=%s | "
            "status_code=None | headers=None | body_snippet=None | exception=%s: %s",
            name_filter, url, request_start_iso, elapsed_ms, timed_out, type(e).__name__, e,
        )
        # UNCHANGED message/behavior from before — only the logging above is new.
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

    data = safe_get(url, headers, label=f"BULK_PRICES ({len(keys)} instruments)")

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


def get_candles(key, label=None):
    # FIX: original code called .get() directly on safe_get()'s return value,
    # which crashes with AttributeError when safe_get returns None
    # (timeouts, holidays, no data, expired token, rate limits).
    #
    # `label` (new, optional, default None): passed through to safe_get()
    # for the Live Scan investigation's diagnostic logging only — does not
    # affect the request, response handling, or return value at all.
    today = datetime.now(IST).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{key}/30minute/{today}"

    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}, label=label)

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
def get_daily_trend(key, label=None):
    """
    Fetches recent DAILY candles (not 30-min) and computes EMA20/EMA50 on
    that higher timeframe, to check whether the broader trend agrees with
    the 30-min signal. This is the "higher-timeframe filter" — trading
    WITH the bigger trend reduces (does not eliminate) false counter-trend
    signals, a well-established practice, not a guarantee.

    Returns "Bullish", "Bearish", or None if not enough daily data exists
    (e.g. a newly-listed instrument, or fewer than 50 trading days available).

    `label` (new, optional, default None): passed through to safe_get()
    for the Live Scan investigation's diagnostic logging only. NOTE: this
    function is @st.cache_data-decorated — adding a new parameter shifts
    its cache key, so the first call after this change is a one-time cache
    miss per instrument (identical to any other code change touching a
    cached function's signature); it does not change what the function
    returns or how long results stay cached afterward.
    """
    to_date = datetime.now(IST).strftime("%Y-%m-%d")
    from_date = (datetime.now(IST) - pd.Timedelta(days=120)).strftime("%Y-%m-%d")

    url = f"https://api.upstox.com/v2/historical-candle/{key}/day/{to_date}/{from_date}"

    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}, label=label)

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
    data = safe_get(url, {"Authorization": f"Bearer {UPSTOX_ACCESS_TOKEN}"}, label="MARKET_TREND (NIFTY50)")

    if not data:
        return None

    candles = data.get("data", {}).get("candles", None)
    if not candles or len(candles) < 50:
        return None

    closes = [c[4] for c in reversed(candles)]
    e20 = ema(closes, 20)
    e50 = ema(closes, 50)

    return "Bullish" if e20 > e50 else "Bearish"
