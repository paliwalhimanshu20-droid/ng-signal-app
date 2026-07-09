"""
The scan loop: pulls live data for every watchlist instrument, runs it
through signal_logic's scoring engine, and assembles the results table the
dashboard renders. This is the piece that ties config + watchlist +
upstox_client + signal_logic together for a single "Run Live Scan" click.
"""

import logging
import time
import pandas as pd

from watchlist import get_watchlist, get_sector
from upstox_client import (
    get_prices_bulk, get_candles, get_daily_trend, get_market_trend,
)
from signal_logic import (
    ema, atr, rsi,
    calculate_supertrend, compute_adx, signal_engine, levels,
)
from config import COMMODITY_RISK_PARAMS

logger = logging.getLogger(__name__)

# ============================================================
# TEMPORARY TIMING INSTRUMENTATION (PR 7B — production profiling)
# Same self-contained pattern as app.py / warehouse_admin/render.py: no
# logic changed anywhere in run_scanner() below, only timing prints were
# added. Per-instrument calls (candle download, indicators, signal engine)
# are accumulated across the loop and printed ONCE as a total after the
# loop — printing per-instrument (x34 every scan) would flood the log
# without adding anything a sum doesn't already tell you. Remove
# `_timed`, `_now_iso`, `_contextmanager`, and every timing line below
# once the Watcher has reviewed real measurements from PR 7B.
# ============================================================
from contextlib import contextmanager as _contextmanager
from datetime import datetime as _dt

_SLOW_THRESHOLD_MS = 250.0


def _now_iso():
    return _dt.now().isoformat(timespec="milliseconds")


@_contextmanager
def _timed(label):
    _t0 = time.perf_counter()
    print(f"[TIMING START] {label} @ {_now_iso()}")
    try:
        yield
    finally:
        _ms = (time.perf_counter() - _t0) * 1000
        _flag = "  <<< SLOW (>250ms)" if _ms > _SLOW_THRESHOLD_MS else ""
        print(f"[TIMING END] {label} = {_ms:.2f} ms{_flag} @ {_now_iso()}")


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


def _agree_label(layer_trend, trend):
    """Small shared helper: 'Agree'/'Disagree'/'N/A' label for a single
    higher-context trend layer vs. the instrument's own EMA trend — used
    to populate the per-factor columns that signal_log.compute_factor_performance()
    later groups by."""
    if layer_trend is None:
        return "N/A"
    return "Agree" if layer_trend == trend else "Disagree"


def run_scanner(commodity_contracts=None):
    """
    Returns a tuple: (top5_df, full_df)
    full_df now includes EVERY stock that returned valid data, with RSI
    and Volume columns added, regardless of score — so the dashboard can
    show the full scanned universe with filters, not just the top 5.

    commodity_contracts: dict of display_name -> instrument_key, built from
    the dashboard's expiry dropdowns. Passed straight through to get_watchlist().
    """
    _scan_t0 = time.perf_counter()
    print(f"\n########## [scanner.py] run_scanner() START @ {_now_iso()} ##########")

    with _timed("SCANNER: get_watchlist() [instrument lookup]"):
        watchlist = get_watchlist(commodity_contracts)
    all_results = []

    # Fetch all LTPs in ONE bulk call instead of one call per instrument
    # inside the loop below (see get_prices_bulk() in upstox_client.py for why).
    with _timed("SCANNER: get_prices_bulk() [bulk LTP fetch]"):
        price_lookup = get_prices_bulk([key for key in watchlist.values() if key])

    # NEW: broad-market (Nifty 50) regime — fetched ONCE for the whole
    # scan, not per instrument (it's cached 1h anyway, but no reason to
    # even attempt it 39 times).
    with _timed("SCANNER: get_market_trend() [not in PR 7B's original list, added for visibility]"):
        market_trend = get_market_trend()

    # Cumulative accumulators for the per-instrument loop below — see
    # module docstring note on why these print once, after the loop,
    # instead of once per instrument.
    _t_candles_ms = 0.0
    _t_daily_trend_ms = 0.0
    _t_indicators_ms = 0.0
    _t_signal_engine_ms = 0.0
    _n_scanned = 0

    print(f"[TIMING START] SCANNER: get_candles() cumulative [historical candle download] @ {_now_iso()}")
    print(f"[TIMING START] SCANNER: get_daily_trend() cumulative [not in PR 7B's original list, added for visibility] @ {_now_iso()}")
    print(f"[TIMING START] SCANNER: indicator calculations cumulative [ema/atr/rsi/volume/supertrend/adx] @ {_now_iso()}")
    print(f"[TIMING START] SCANNER: signal_engine() cumulative @ {_now_iso()}")

    for name, key in watchlist.items():

        if not key:
            continue

        instrument_key = key

        # THROTTLE: Upstox's standard rate limit is 25 requests/sec, 250/min.
        # Each instrument below fires 1-2 API calls (candles, and daily
        # trend on cache miss — price now comes from the bulk lookup above,
        # not a per-instrument call). Without a pause, ~39 instruments could
        # still fire requests fast enough to trip a 429. This keeps the
        # loop comfortably under the limit.
        time.sleep(0.2)

        try:
            _t0 = time.perf_counter()
            candles = get_candles(instrument_key, label=f"{name} ({instrument_key})")
            _t_candles_ms += (time.perf_counter() - _t0) * 1000

            if not candles:
                continue

            closes = [c[4] for c in reversed(candles)]

            if len(closes) < 50:
                continue

            price = price_lookup.get(instrument_key)

            if not price:
               continue

            _t0 = time.perf_counter()
            ema20 = ema(closes, 20)
            ema50 = ema(closes, 50)
            atr_val = atr(candles)

            # NEW indicators
            rsi_val = rsi(closes, 14)
            vol_ratio, vol_tag = volume_signal(candles)

            # Supertrend — computed off the same intraday `candles`
            # already fetched above, no extra API call.
            st_result = calculate_supertrend(candles)
            supertrend_trend = st_result["latest_trend"] if st_result else None
            supertrend_value = st_result["latest_value"] if st_result else None

            # NEW: ADX(14) — trend STRENGTH, off the same candles, no
            # extra API call.
            adx_val = compute_adx(candles)
            _t_indicators_ms += (time.perf_counter() - _t0) * 1000

            # Higher-timeframe (daily) trend filter — cached, so this
            # doesn't multiply API calls on every scan within the same day.
            _t0 = time.perf_counter()
            daily_trend = get_daily_trend(instrument_key, label=f"{name} ({instrument_key})")
            _t_daily_trend_ms += (time.perf_counter() - _t0) * 1000

            # NG SIGNAL ACCURACY FIX: instruments added via the MCX commodity
            # dropdown are named "<Display> (MCX)" (same convention
            # watchlist.get_sector() uses). Those get asset-class-appropriate
            # ADX/ExpectedMove% gate bounds from config.COMMODITY_RISK_PARAMS
            # instead of the NSE-equity-tuned signal_logic.py defaults — see
            # that dict's comment for why. Every NSE equity is unaffected
            # (risk_overrides stays {} for them, so signal_engine() falls
            # back to its original module-level defaults exactly as before).
            is_commodity = "(MCX)" in name
            risk_overrides = COMMODITY_RISK_PARAMS if is_commodity else {}

            _t0 = time.perf_counter()
            signal, score, prob, trend, regime, expected_move, reasons, conviction_pct = signal_engine(
                price,
                ema20,
                ema50,
                atr_val,
                daily_trend,
                supertrend_trend,
                market_trend,
                adx_val,
                **risk_overrides
            )
            _t_signal_engine_ms += (time.perf_counter() - _t0) * 1000
            _n_scanned += 1

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

            # Per-layer agreement flags — stored individually (not just the
            # blended ConvictionPct) so signal_log.compute_factor_performance()
            # can later check, with real outcome data, whether each factor
            # actually correlates with wins on its own.
            all_results.append({
                "Instrument": name,
                "InstrumentKey": instrument_key,
                "Sector": get_sector(name),
                "DailyTrend": daily_trend if daily_trend else "N/A",
                "MarketTrend": market_trend if market_trend else "N/A",
                "Signal": signal,
                "Confidence": confidence,
                "Trend": trend,
                "Supertrend": supertrend_trend if supertrend_trend else "N/A",
                "SupertrendValue": supertrend_value if supertrend_value is not None else "N/A",
                "Regime": regime,
                "ADX": adx_val if adx_val is not None else "N/A",
                "ConvictionPct": conviction_pct if conviction_pct is not None else "N/A",
                "DailyTrendAgree": _agree_label(daily_trend, trend),
                "SupertrendAgree": _agree_label(supertrend_trend, trend),
                "MarketTrendAgree": _agree_label(market_trend, trend),
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
            # Was st.error(...) — that made this module unusable outside a
            # Streamlit session (see generate_signals.py, NGSP Phase 0).
            # Same behavior otherwise: this instrument is skipped, nothing
            # is appended to all_results, exactly as before. The Scanner
            # tab's full_df table never included per-instrument error rows
            # previously, so it still doesn't — only where the error is
            # surfaced changed (app/Actions logs instead of an st.error
            # toast on the page).
            logger.warning("%s Error: %s", name, e)
            continue

    _candles_flag = "  <<< SLOW (>250ms)" if _t_candles_ms > _SLOW_THRESHOLD_MS else ""
    print(f"[TIMING END] SCANNER: get_candles() cumulative [historical candle download, {_n_scanned}/{len(watchlist)} instruments] = {_t_candles_ms:.2f} ms{_candles_flag} @ {_now_iso()}")

    _daily_trend_flag = "  <<< SLOW (>250ms)" if _t_daily_trend_ms > _SLOW_THRESHOLD_MS else ""
    print(f"[TIMING END] SCANNER: get_daily_trend() cumulative [not in PR 7B's original list, added for visibility] = {_t_daily_trend_ms:.2f} ms{_daily_trend_flag} @ {_now_iso()}")

    _indicators_flag = "  <<< SLOW (>250ms)" if _t_indicators_ms > _SLOW_THRESHOLD_MS else ""
    print(f"[TIMING END] SCANNER: indicator calculations cumulative [ema/atr/rsi/volume/supertrend/adx, {_n_scanned} instruments] = {_t_indicators_ms:.2f} ms{_indicators_flag} @ {_now_iso()}")

    _signal_engine_flag = "  <<< SLOW (>250ms)" if _t_signal_engine_ms > _SLOW_THRESHOLD_MS else ""
    print(f"[TIMING END] SCANNER: signal_engine() cumulative = {_t_signal_engine_ms:.2f} ms{_signal_engine_flag} @ {_now_iso()}")

    with _timed("SCANNER: DataFrame creation [full_df + top5_df build]"):
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

    _scan_total_ms = (time.perf_counter() - _scan_t0) * 1000
    _scan_flag = "  <<< SLOW" if _scan_total_ms > 1000 else ""
    print(f"########## [scanner.py] run_scanner() END @ {_now_iso()}  TOTAL={_scan_total_ms:.2f} ms{_scan_flag} ##########\n")

    return top5_df, full_df

