"""
The scan loop: pulls live data for every watchlist instrument, runs it
through signal_logic's scoring engine, and assembles the results table the
dashboard renders. This is the piece that ties config + watchlist +
upstox_client + signal_logic together for a single "Run Live Scan" click.
"""

import time
import pandas as pd
import streamlit as st

from watchlist import get_watchlist, get_sector
from upstox_client import (
    get_prices_bulk, get_candles, get_daily_trend, get_market_trend,
)
from signal_logic import (
    ema, atr, rsi,
    calculate_supertrend, compute_adx, signal_engine, levels,
)
from config import COMMODITY_RISK_PARAMS


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

    watchlist = get_watchlist(commodity_contracts)
    all_results = []

    # Fetch all LTPs in ONE bulk call instead of one call per instrument
    # inside the loop below (see get_prices_bulk() in upstox_client.py for why).
    price_lookup = get_prices_bulk([key for key in watchlist.values() if key])

    # NEW: broad-market (Nifty 50) regime — fetched ONCE for the whole
    # scan, not per instrument (it's cached 1h anyway, but no reason to
    # even attempt it 39 times).
    market_trend = get_market_trend()

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
            candles = get_candles(instrument_key)

            if not candles:
                continue

            closes = [c[4] for c in reversed(candles)]

            if len(closes) < 50:
                continue

            price = price_lookup.get(instrument_key)

            if not price:
               continue

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

            # Higher-timeframe (daily) trend filter — cached, so this
            # doesn't multiply API calls on every scan within the same day.
            daily_trend = get_daily_trend(instrument_key)

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
    
