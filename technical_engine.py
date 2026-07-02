"""
technical_engine.py
====================
NGSP-001-R1 -- Technical Intelligence Engine (Revision 1)

This revision upgrades the module from a flat scoring function into a
registry-based engine architecture, per the approved Architecture Review.
It is the foundation layer for the future Market Intelligence Engine,
Signal Intelligence Engine, Risk Intelligence Engine, Learning Engine,
and AI Strategy Optimizer.

WHAT DID NOT CHANGE
--------------------
- signal_logic.py: untouched. signal_engine() still drives scanner.py's
  live BUY/SELL/WATCH decision exactly as before.
- scanner.py, app.py, dashboard, reports.py: untouched.
- Every R0 public function (calculate_trend_score, calculate_momentum_score,
  calculate_volume_score, calculate_volatility_score, calculate_price_score,
  calculate_technical_score) keeps its exact signature and behavior, so any
  existing caller or test written against R0 keeps working unchanged.
- TechnicalScore dataclass: unchanged, kept for backward compatibility.

WHAT'S NEW IN R1
-----------------
1. All configurable values now live in technical_config.py -- nothing
   here is a bare magic number where config could hold it (Req 1).
2. Composite weights are read dynamically from technical_config.TECHNICAL_WEIGHTS
   instead of hardcoded module constants (Req 2). NOTE: the R1 default
   weights (trend 30 / momentum 20 / volume 15 / volatility 15 / price 20)
   differ numerically from R0's (30/25/15/15/15) -- see the R1 summary
   delivered alongside this file for why. The interface is unchanged;
   the numbers legitimately shift because that's what the new config
   defaults specify.
3. IndicatorResult -- every individual indicator (EMA, RSI, MACD, ADX,
   ATR/ExpectedMove, VWAP, Relative Volume, Bollinger, ...) now returns
   a structured result carrying its own score/weight/contribution/status,
   not just a line of text (Req 3).
4. TechnicalReport -- a richer report object grouping indicators into
   Trend/Momentum/Volume/Volatility/Price Engines, with composite score,
   confidence, strengths, weaknesses, warnings (Req 4).
5. Price Engine -- built as a registry so future components (support,
   resistance, pivots, breakouts, gap analysis, ORB, price structure,
   distance-from-high/low) can be added by registering a function, with
   zero changes to the engine itself (Req 5).
6. Every IndicatorResult exposes optimizer-ready metadata: parameters,
   weight, contribution, enabled, historical_accuracy placeholder (Req 6).
7. IndicatorContext carries `timeframe` and `asset_class` through every
   indicator function's signature today, unused by the math, so
   multi-timeframe and multi-asset-class support can be added later as
   branches inside existing functions (Req 7, part of multi-asset intent
   in this task's final note).
8. Volume Engine is registry-based with documented future source flags
   (delivery %, OI, volume profile, institutional volume) in
   technical_config.VOLUME_FUTURE_SOURCES -- none implemented (Req 8).
9. Docstrings expanded across all new public functions (Req 9).

DESIGNED FOR SCALE (per this task's final note)
-------------------------------------------------
The registry + IndicatorContext pattern used here is what makes 500+
instruments and multiple asset classes tractable without a rewrite:
  - Adding an indicator = write one function + register_indicator() call.
    Nothing about run_engine() or generate_technical_report() changes.
  - Per-instrument tuning = technical_config.INDICATOR_WEIGHTS /
    TECHNICAL_WEIGHTS are plain dicts an optimizer can rewrite per
    symbol at scan time without touching this file.
  - Multi-asset-class = IndicatorContext.asset_class is already threaded
    through every indicator function; asset-specific branches (e.g.
    different volatility bounds for crypto) can be added inside existing
    functions rather than forking the engine per asset class.
  - This module still performs a single pass over a single instrument's
    candles per call -- running it across 500+ instruments is a
    scanner.py concern (looping + async/batching), not something this
    module needs to know about. Keeping instrument-loop concerns out of
    the engine is itself part of designing for scale: the engine stays a
    pure function of (price, candles, context) -> TechnicalReport, so
    it's trivially parallelizable across instruments without any shared
    state risk.

Candle format throughout: newest-first,
    [timestamp, open, high, low, close, volume, oi]
(matches signal_logic.py / scanner.py / Upstox raw response)
"""

from dataclasses import dataclass, field
from typing import Callable, Dict, List, Optional, Tuple

from signal_logic import ema, atr, rsi, calculate_supertrend, compute_adx

from technical_config import (
    EMA_PERIODS, ADX_THRESHOLDS, RSI_PERIOD, RSI_ZONES, MACD_PARAMS,
    BOLLINGER_SETTINGS, EXPECTED_MOVE_BOUNDS, VOLUME_SETTINGS,
    VWAP_DISTANCE_BANDS, TECHNICAL_WEIGHTS, INDICATOR_WEIGHTS,
    ENABLED_INDICATORS, DEFAULT_TIMEFRAME, DEFAULT_ASSET_CLASS,
)


# ============================================================
# NEW INDICATORS (unchanged from R0, defaults now sourced from config)
# ============================================================

def ema200(closes: List[float]) -> Optional[float]:
    """EMA200 -- long-term trend filter. Reuses ema() from signal_logic.py.
    Period sourced from technical_config.EMA_PERIODS["long"]. Returns
    None if fewer than that many closes are available."""
    period = EMA_PERIODS["long"]
    if len(closes) < period:
        return None
    return ema(closes, period)


def macd(closes: List[float], fast: int = None, slow: int = None,
          signal: int = None) -> Tuple[Optional[float], Optional[float], Optional[float]]:
    """Standard MACD (fast EMA - slow EMA, with an EMA-of-that as signal).
    fast/slow/signal default to technical_config.MACD_PARAMS if not passed
    explicitly. Returns (macd_line, signal_line, histogram), each rounded
    to 4dp, or (None, None, None) if there isn't enough history. Reuses
    ema() from signal_logic.py for every EMA leg."""
    fast = fast if fast is not None else MACD_PARAMS["fast"]
    slow = slow if slow is not None else MACD_PARAMS["slow"]
    signal = signal if signal is not None else MACD_PARAMS["signal"]

    if len(closes) < slow + signal:
        return None, None, None

    macd_series = []
    for i in range(slow, len(closes) + 1):
        window = closes[:i]
        macd_series.append(ema(window, fast) - ema(window, slow))

    if len(macd_series) < signal:
        return None, None, None

    macd_line = macd_series[-1]
    signal_line = ema(macd_series, signal)
    histogram = round(macd_line - signal_line, 4)
    return round(macd_line, 4), round(signal_line, 4), histogram


def bollinger_bands(
    closes: List[float], period: int = None, num_std: float = None
) -> Tuple[Optional[float], Optional[float], Optional[float], Optional[float]]:
    """Returns (upper, middle, lower, pct_b). period/num_std default to
    technical_config.BOLLINGER_SETTINGS. pct_b = (price - lower) /
    (upper - lower), 0-1 under normal conditions; can go outside [0,1]
    when price pierces a band. Returns all-None if insufficient history."""
    period = period if period is not None else BOLLINGER_SETTINGS["period"]
    num_std = num_std if num_std is not None else BOLLINGER_SETTINGS["num_std"]

    if len(closes) < period:
        return None, None, None, None

    window = closes[-period:]
    middle = sum(window) / period
    variance = sum((c - middle) ** 2 for c in window) / period
    std = variance ** 0.5

    upper = middle + num_std * std
    lower = middle - num_std * std

    price = closes[-1]
    band_width = upper - lower
    pct_b = (price - lower) / band_width if band_width > 0 else 0.5

    return round(upper, 2), round(middle, 2), round(lower, 2), round(pct_b, 3)


def relative_volume(candles: list) -> Optional[float]:
    """Latest candle's volume vs. the average of the preceding N candles,
    where N = technical_config.VOLUME_SETTINGS["lookback_candles"].
    Distinct from scanner.py's existing volume_signal() (session-average
    based) -- this is a separate, fixed-lookback metric; both can coexist."""
    if not candles or len(candles) < 3:
        return None

    vols = [c[5] for c in candles if len(c) > 5]
    if len(vols) < 3:
        return None

    lookback_n = VOLUME_SETTINGS["lookback_candles"]
    latest_vol = vols[0]
    lookback = vols[1:1 + lookback_n]
    avg_vol = sum(lookback) / len(lookback) if lookback else 0

    if avg_vol == 0:
        return None

    return round(latest_vol / avg_vol, 2)


def volume_spike(rel_vol: Optional[float], threshold: float = None) -> bool:
    """True if relative_volume() >= threshold (default from VOLUME_SETTINGS)."""
    threshold = threshold if threshold is not None else VOLUME_SETTINGS["spike_threshold"]
    return rel_vol is not None and rel_vol >= threshold


def vwap(candles: list) -> Optional[float]:
    """Session VWAP = sum(typical_price * volume) / sum(volume), where
    typical_price = (high + low + close) / 3. candles: newest-first,
    reversed to chronological order before accumulating (same convention
    as signal_logic.py's calculate_supertrend()/compute_adx())."""
    if not candles:
        return None

    ordered = list(reversed(candles))
    cum_pv = 0.0
    cum_vol = 0.0
    for c in ordered:
        if len(c) < 6:
            continue
        high, low, close, vol = c[2], c[3], c[4], c[5]
        typical_price = (high + low + close) / 3
        cum_pv += typical_price * vol
        cum_vol += vol

    if cum_vol == 0:
        return None

    return round(cum_pv / cum_vol, 2)


# ============================================================
# R0 SUB-SCORES -- UNCHANGED SIGNATURES/BEHAVIOR, kept for backward
# compatibility. Defaults now sourced from technical_config instead of
# bare literals, but numerically identical to R0.
# ============================================================

def calculate_trend_score(
    price: float, ema20_val: float, ema50_val: float,
    ema200_val: Optional[float] = None,
    supertrend_trend: Optional[str] = None,
    adx_val: Optional[float] = None,
) -> Tuple[float, List[str]]:
    """R0-compatible composite trend score (0-100). See the Trend Engine
    registry (score_ema_alignment() etc.) for the R1 per-indicator
    breakdown of this same logic."""
    score = 0.0
    reasons: List[str] = []

    if ema20_val > ema50_val:
        score += 30
        reasons.append("EMA20 > EMA50 (bullish short-term trend)")
    else:
        reasons.append("EMA20 < EMA50 (bearish short-term trend)")

    if (ema20_val > ema50_val and price > ema20_val) or (ema20_val < ema50_val and price < ema20_val):
        score += 20
        reasons.append("Price confirms EMA trend direction")

    if ema200_val is not None:
        long_term_bullish = price > ema200_val
        short_term_bullish = ema20_val > ema50_val
        if long_term_bullish == short_term_bullish:
            score += 25
            reasons.append("Short-term trend aligned with EMA200 (long-term)")
        else:
            score += 5
            reasons.append("Short-term trend against EMA200 -- counter-trend risk")
    else:
        score += 12.5
        reasons.append("EMA200 unavailable (needs 200+ candles of history)")

    if supertrend_trend is not None:
        trend_label = "Bullish" if ema20_val > ema50_val else "Bearish"
        if supertrend_trend == trend_label:
            score += 15
            reasons.append(f"Supertrend agrees ({supertrend_trend})")
        else:
            reasons.append(f"Supertrend disagrees ({supertrend_trend})")
    else:
        score += 7.5
        reasons.append("Supertrend unavailable")

    if adx_val is not None:
        if adx_val >= ADX_THRESHOLDS["strong_at_or_above"]:
            score += 10
            reasons.append(f"Strong trend strength (ADX {adx_val})")
        elif adx_val < ADX_THRESHOLDS["weak_below"]:
            score -= 10
            reasons.append(f"Weak/choppy trend (ADX {adx_val})")
        else:
            score += 5
    else:
        score += 5
        reasons.append("ADX unavailable")

    return round(max(0.0, min(100.0, score)), 1), reasons


def calculate_momentum_score(
    rsi_val: Optional[float] = None,
    macd_line: Optional[float] = None,
    macd_signal_val: Optional[float] = None,
    macd_hist: Optional[float] = None,
    adx_val: Optional[float] = None,
) -> Tuple[float, List[str]]:
    """R0-compatible composite momentum score (0-100)."""
    raw = 0.0
    max_possible = 0.0
    reasons: List[str] = []

    if rsi_val is not None:
        max_possible += 40
        if RSI_ZONES["healthy_low"] <= rsi_val <= RSI_ZONES["healthy_high"]:
            raw += 40
            reasons.append(f"RSI {rsi_val} in healthy trending zone "
                            f"({RSI_ZONES['healthy_low']}-{RSI_ZONES['healthy_high']})")
        elif RSI_ZONES["acceptable_low"] <= rsi_val < RSI_ZONES["healthy_low"] or \
                RSI_ZONES["healthy_high"] < rsi_val <= RSI_ZONES["acceptable_high"]:
            raw += 25
            reasons.append(f"RSI {rsi_val} acceptable but not ideal")
        else:
            raw += 5
            zone = "overbought" if rsi_val > RSI_ZONES["acceptable_high"] else "oversold"
            reasons.append(f"RSI {rsi_val} {zone}")

    if macd_line is not None and macd_signal_val is not None:
        max_possible += 40
        if macd_hist is not None and macd_hist > 0 and macd_line > macd_signal_val:
            raw += 40
            reasons.append("MACD bullish: line above signal, positive histogram")
        elif macd_hist is not None and macd_hist < 0 and macd_line < macd_signal_val:
            raw += 40
            reasons.append("MACD bearish: line below signal, negative histogram (directionally consistent)")
        else:
            raw += 15
            reasons.append("MACD mixed / transitioning")

    if adx_val is not None:
        max_possible += 20
        if adx_val >= ADX_THRESHOLDS["strong_at_or_above"]:
            raw += 20
            reasons.append(f"ADX {adx_val} confirms momentum has strength behind it")
        else:
            raw += 8

    if max_possible == 0:
        return 50.0, ["No momentum data available -- neutral score"]

    normalized = (raw / max_possible) * 100
    return round(max(0.0, min(100.0, normalized)), 1), reasons


def calculate_volume_score(
    rel_vol: Optional[float] = None,
    vol_spike: bool = False,
    vol_ratio_session: Optional[float] = None,
) -> Tuple[float, List[str]]:
    """R0-compatible composite volume score (0-100)."""
    if rel_vol is None and vol_ratio_session is None:
        return 50.0, ["No volume data available -- neutral score"]

    ratio = rel_vol if rel_vol is not None else vol_ratio_session
    source = "20-candle avg" if rel_vol is not None else "session avg (legacy scanner.py metric)"
    reasons: List[str] = []

    if ratio >= VOLUME_SETTINGS["spike_threshold"]:
        score = 100.0
        reasons.append(f"Volume spike: {ratio}x {source}")
    elif ratio >= VOLUME_SETTINGS["high_threshold"]:
        score = 80.0
        reasons.append(f"High relative volume: {ratio}x {source}")
    elif ratio >= VOLUME_SETTINGS["normal_threshold"]:
        score = 55.0
        reasons.append(f"Normal volume: {ratio}x {source}")
    else:
        score = 25.0
        reasons.append(f"Low volume: {ratio}x {source} -- weak participation")

    if vol_spike:
        reasons.append(f"Volume spike flag triggered (>={VOLUME_SETTINGS['spike_threshold']}x 20-candle avg)")

    return score, reasons


def calculate_volatility_score(
    expected_move_pct: Optional[float] = None,
    pct_b: Optional[float] = None,
    min_pct: float = None,
    max_pct: float = None,
) -> Tuple[float, List[str]]:
    """R0-compatible composite volatility score (0-100). min_pct/max_pct
    default to technical_config.EXPECTED_MOVE_BOUNDS."""
    min_pct = min_pct if min_pct is not None else EXPECTED_MOVE_BOUNDS["min_pct"]
    max_pct = max_pct if max_pct is not None else EXPECTED_MOVE_BOUNDS["max_pct"]
    reasons: List[str] = []

    if expected_move_pct is None:
        return 50.0, ["No volatility data available -- neutral score"]

    if min_pct <= expected_move_pct <= max_pct:
        mid = (min_pct + max_pct) / 2
        span = (max_pct - min_pct) / 2
        distance = abs(expected_move_pct - mid) / span if span > 0 else 0
        score = 100.0 - (distance * 30)
        reasons.append(f"ExpectedMove {expected_move_pct}% within valid range ({min_pct}-{max_pct}%)")
    elif expected_move_pct < min_pct:
        score = 20.0
        reasons.append(f"ExpectedMove {expected_move_pct}% too low -- dead/illiquid session")
    else:
        score = 15.0
        reasons.append(f"ExpectedMove {expected_move_pct}% too high -- possible news/gap spike")

    if pct_b is not None:
        if pct_b > 1.0:
            score = max(0.0, score - 10)
            reasons.append("Price above upper Bollinger Band -- extended")
        elif pct_b < 0.0:
            score = max(0.0, score - 10)
            reasons.append("Price below lower Bollinger Band -- extended")
        else:
            reasons.append(f"Price within Bollinger Bands (%b={pct_b})")

    return round(max(0.0, min(100.0, score)), 1), reasons


def calculate_price_score(
    price: float,
    vwap_val: Optional[float] = None,
) -> Tuple[float, List[str]]:
    """R0-compatible price/VWAP score (0-100). Bands default to
    technical_config.VWAP_DISTANCE_BANDS."""
    if vwap_val is None or vwap_val == 0:
        return 50.0, ["VWAP unavailable -- neutral score"]

    distance_pct = abs(price - vwap_val) / vwap_val * 100
    reasons: List[str] = []
    bands = VWAP_DISTANCE_BANDS

    if distance_pct <= bands["tight"]["max_distance_pct"]:
        score = bands["tight"]["score"]
        reasons.append(f"Price tightly tracking VWAP ({distance_pct:.2f}% away)")
    elif distance_pct <= bands["close"]["max_distance_pct"]:
        score = bands["close"]["score"]
        reasons.append(f"Price reasonably close to VWAP ({distance_pct:.2f}% away)")
    elif distance_pct <= bands["moderate"]["max_distance_pct"]:
        score = bands["moderate"]["score"]
        reasons.append(f"Price moderately extended from VWAP ({distance_pct:.2f}% away)")
    else:
        score = bands["extended_score"]
        reasons.append(f"Price significantly extended from VWAP ({distance_pct:.2f}% away) -- chase risk")

    side = "above" if price > vwap_val else "below"
    reasons.append(f"Price is {side} VWAP ({vwap_val})")

    return score, reasons


# R1: weights now sourced from technical_config.TECHNICAL_WEIGHTS
# (0-100 scale there; converted to fractions here since calculate_
# technical_score()'s math, unchanged from R0, expects fractions).
TREND_WEIGHT = TECHNICAL_WEIGHTS["trend"] / 100.0
MOMENTUM_WEIGHT = TECHNICAL_WEIGHTS["momentum"] / 100.0
VOLUME_WEIGHT = TECHNICAL_WEIGHTS["volume"] / 100.0
VOLATILITY_WEIGHT = TECHNICAL_WEIGHTS["volatility"] / 100.0
PRICE_WEIGHT = TECHNICAL_WEIGHTS["price"] / 100.0


@dataclass
class TechnicalScore:
    """R0-compatible flat result. Kept for any existing caller. New code
    should prefer TechnicalReport (see generate_technical_report())."""
    trend_score: float
    momentum_score: float
    volume_score: float
    volatility_score: float
    price_score: float
    technical_score: float
    trend: str
    reasons: List[str] = field(default_factory=list)


# R1 validation fix: the block below (EMA200, MACD, RSI, relative volume,
# volume spike, Bollinger %b, VWAP, ExpectedMove%, ADX, Supertrend) was
# previously duplicated verbatim in both calculate_technical_score() (R0)
# and build_indicator_context() (R1). Extracted here so the underlying
# math exists in exactly one place. This is a pure internal refactor --
# no public signature changed, no output value changed (verified by the
# full test suite passing unchanged after this extraction). Note this
# does NOT eliminate duplicate *runtime work* if a caller invokes both
# calculate_technical_score() and generate_technical_report() for the
# same instrument in the same scan cycle -- each entry point still calls
# this helper independently, by design, since unifying the two entry
# points themselves would be a redesign, out of scope for this revision.
def _compute_derived_indicators(
    price: float, candles: list, closes: List[float], atr_val: Optional[float],
    adx_val: Optional[float] = None, supertrend_trend: Optional[str] = None,
) -> dict:
    """Internal helper (not part of the public API -- not exported,
    callers should use calculate_technical_score() or
    generate_technical_report()). Computes every indicator derivable
    purely from candles/closes/atr_val, plus resolves adx_val/
    supertrend_trend if not already supplied. Returns a dict so each
    caller can destructure it however suits its own output shape."""
    ema200_val = ema200(closes)
    macd_line, macd_signal_val, macd_hist = macd(closes)
    rsi_val = rsi(closes, RSI_PERIOD)
    rel_vol = relative_volume(candles)
    vol_spike = volume_spike(rel_vol)
    _, _, _, pct_b = bollinger_bands(closes)
    vwap_val = vwap(candles)
    expected_move_pct = round((atr_val / price) * 100, 2) if atr_val and price else None

    if adx_val is None:
        adx_val = compute_adx(candles)
    if supertrend_trend is None:
        st_result = calculate_supertrend(candles)
        supertrend_trend = st_result["latest_trend"] if st_result else None

    return {
        "ema200_val": ema200_val, "macd_line": macd_line, "macd_signal_val": macd_signal_val,
        "macd_hist": macd_hist, "rsi_val": rsi_val, "rel_vol": rel_vol, "vol_spike": vol_spike,
        "pct_b": pct_b, "vwap_val": vwap_val, "expected_move_pct": expected_move_pct,
        "adx_val": adx_val, "supertrend_trend": supertrend_trend,
    }


def calculate_technical_score(
    price: float,
    candles: list,
    ema20_val: Optional[float] = None,
    ema50_val: Optional[float] = None,
    atr_val: Optional[float] = None,
    supertrend_trend: Optional[str] = None,
    adx_val: Optional[float] = None,
) -> TechnicalScore:
    """R0-compatible entry point. Unchanged behavior from R0 aside from
    weights/thresholds now being sourced from technical_config.py. For
    the new registry-based report with per-indicator breakdown, see
    generate_technical_report() below."""
    closes = [c[4] for c in reversed(candles)] if candles else []

    if ema20_val is None and len(closes) >= EMA_PERIODS["short"]:
        ema20_val = ema(closes, EMA_PERIODS["short"])
    if ema50_val is None and len(closes) >= EMA_PERIODS["medium"]:
        ema50_val = ema(closes, EMA_PERIODS["medium"])
    if atr_val is None and candles:
        atr_val = atr(candles)

    if ema20_val is None or ema50_val is None:
        return TechnicalScore(
            trend_score=0.0, momentum_score=0.0, volume_score=0.0,
            volatility_score=0.0, price_score=0.0, technical_score=0.0,
            trend="Unknown",
            reasons=["Insufficient candle history for technical scoring"],
        )

    trend = "Bullish" if ema20_val > ema50_val else "Bearish"

    derived = _compute_derived_indicators(price, candles, closes, atr_val, adx_val, supertrend_trend)
    ema200_val = derived["ema200_val"]
    macd_line, macd_signal_val, macd_hist = derived["macd_line"], derived["macd_signal_val"], derived["macd_hist"]
    rsi_val = derived["rsi_val"]
    rel_vol = derived["rel_vol"]
    vol_spike = derived["vol_spike"]
    pct_b = derived["pct_b"]
    vwap_val = derived["vwap_val"]
    expected_move_pct = derived["expected_move_pct"]
    adx_val = derived["adx_val"]
    supertrend_trend = derived["supertrend_trend"]

    trend_score, trend_reasons = calculate_trend_score(
        price, ema20_val, ema50_val, ema200_val, supertrend_trend, adx_val
    )
    momentum_score, momentum_reasons = calculate_momentum_score(
        rsi_val, macd_line, macd_signal_val, macd_hist, adx_val
    )
    volume_score, volume_reasons = calculate_volume_score(rel_vol, vol_spike)
    volatility_score, volatility_reasons = calculate_volatility_score(
        expected_move_pct, pct_b
    )
    price_score, price_reasons = calculate_price_score(price, vwap_val)

    technical_score = round(
        trend_score * TREND_WEIGHT
        + momentum_score * MOMENTUM_WEIGHT
        + volume_score * VOLUME_WEIGHT
        + volatility_score * VOLATILITY_WEIGHT
        + price_score * PRICE_WEIGHT,
        1,
    )

    return TechnicalScore(
        trend_score=trend_score,
        momentum_score=momentum_score,
        volume_score=volume_score,
        volatility_score=volatility_score,
        price_score=price_score,
        technical_score=technical_score,
        trend=trend,
        reasons=trend_reasons + momentum_reasons + volume_reasons + volatility_reasons + price_reasons,
    )


# ============================================================
# R1: INDICATOR RESULT OBJECT (Req 3)
# ============================================================

@dataclass
class IndicatorResult:
    """Structured result for a single indicator -- the atomic unit the
    dashboard and future AI Strategy Optimizer will consume: one of
    these per indicator, per instrument, per scan.

    indicator_name : stable key, e.g. "ema_alignment", "rsi", "macd".
        Matches the key used in technical_config.ENABLED_INDICATORS and
        technical_config.INDICATOR_WEIGHTS.
    current_value : the raw indicator reading where a single scalar
        makes sense (e.g. RSI value, ADX value); None if not applicable.
    score : this indicator's own 0-100 score.
    weight : this indicator's share (0-100) within its engine, resolved
        from technical_config.INDICATOR_WEIGHTS or split equally.
    contribution : score * weight / 100 -- actual point contribution to
        its engine's score.
    status : short machine-readable label ("bullish", "bearish",
        "strong", "weak", "unavailable", "disabled", ...). Engine/
        dashboard code should branch on this, not parse `reason` text.
    enabled : whether this indicator was enabled via
        technical_config.ENABLED_INDICATORS for this run. Disabled
        indicators still appear in the report for transparency but are
        excluded from score aggregation.
    reason : human-readable explanation, shown in the dashboard.
    parameters : config values used to compute this indicator (e.g.
        {"period": 14} for RSI) -- optimizer metadata (Req 6).
    historical_accuracy : PLACEHOLDER for the future Learning Engine,
        which will populate this with a per-instrument, per-indicator
        track record. Always None until that engine exists --
        intentionally not implemented in this revision.
    """
    indicator_name: str
    current_value: Optional[float]
    score: float
    weight: float
    contribution: float
    status: str
    enabled: bool
    reason: str
    parameters: dict = field(default_factory=dict)
    historical_accuracy: Optional[float] = None


@dataclass
class EngineResult:
    """One engine's (Trend/Momentum/Volume/Volatility/Price) aggregated
    result: its own 0-100 score, its weight in the composite Technical
    Score (from technical_config.TECHNICAL_WEIGHTS), its point
    contribution to that composite, and every IndicatorResult combined
    to produce it (including disabled ones, for transparency)."""
    engine_name: str
    score: float
    weight: float
    contribution: float
    indicators: List[IndicatorResult]
    status: str


@dataclass
class TechnicalReport:
    """R1's preferred interface (Req 4). Supersedes TechnicalScore for
    new code; TechnicalScore remains available for backward compatibility.

    symbol/timeframe/asset_class : identify what this report is for.
        timeframe/asset_class are currently always DEFAULT_TIMEFRAME /
        DEFAULT_ASSET_CLASS unless explicitly passed -- see the Req 7/9
        notes in the module docstring on why these exist now.
    trend_engine..price_engine : the five EngineResults.
    technical_score : composite 0-100, sum of each engine's contribution.
    confidence : 0-100, the percentage of enabled indicators that
        actually had valid (non-"unavailable") data this run. A report
        built from thin history (e.g. no EMA200 yet) will show a
        plausible technical_score but LOW confidence -- dashboards
        should surface both, not just the score.
    trend : "Bullish"/"Bearish"/"Unknown", same semantics as R0.
    strengths/weaknesses/warnings : derived from indicator_results --
        strengths = indicators scoring >=75, weaknesses = indicators
        scoring <=30, warnings = any indicator whose reason flags a
        caution (e.g. counter-trend, overbought/oversold, extended).
    indicator_results : flattened list of every IndicatorResult across
        all five engines -- what the dashboard/optimizer should iterate
        over directly, rather than reaching into each engine separately.
    """
    symbol: Optional[str]
    timeframe: str
    asset_class: str
    trend_engine: EngineResult
    momentum_engine: EngineResult
    volume_engine: EngineResult
    volatility_engine: EngineResult
    price_engine: EngineResult
    technical_score: float
    confidence: float
    trend: str
    strengths: List[str]
    weaknesses: List[str]
    warnings: List[str]
    indicator_results: List[IndicatorResult]


@dataclass
class IndicatorContext:
    """Everything an indicator-scoring function might need, computed once
    per report and passed to every indicator function uniformly. This is
    what makes new indicators addable without touching engine plumbing: a
    new function just reads whatever fields off this context it needs.

    timeframe/asset_class are carried here specifically so future
    multi-timeframe and multi-asset-class indicators can branch on them
    without changing this dataclass's shape (Req 7, scale note)."""
    symbol: Optional[str]
    price: float
    candles: list
    closes: List[float]
    timeframe: str
    asset_class: str
    ema20_val: float
    ema50_val: float
    ema200_val: Optional[float]
    atr_val: Optional[float]
    adx_val: Optional[float]
    supertrend_trend: Optional[str]
    rsi_val: Optional[float]
    macd_line: Optional[float]
    macd_signal_val: Optional[float]
    macd_hist: Optional[float]
    vwap_val: Optional[float]
    rel_vol: Optional[float]
    vol_spike: bool
    pct_b: Optional[float]
    expected_move_pct: Optional[float]


# ============================================================
# R1: PER-INDICATOR SCORING FUNCTIONS
# Each takes an IndicatorContext and returns an IndicatorResult.
# weight/contribution are left at 0 here -- run_engine() fills them in
# once it knows how many enabled indicators are competing for weight
# within the engine (equal split, or technical_config.INDICATOR_WEIGHTS
# override).
# ============================================================

# ---- Trend Engine indicators ----

def score_ema_alignment(ctx: IndicatorContext) -> IndicatorResult:
    """How coherently EMA20/EMA50 crossover direction agrees with price
    position. Symmetric: rewards a clean bearish alignment just as much
    as a clean bullish one -- this measures trend COHERENCE, not
    bullishness. Direction is reported separately via `status`."""
    if (ctx.ema20_val > ctx.ema50_val and ctx.price > ctx.ema20_val) or \
       (ctx.ema20_val < ctx.ema50_val and ctx.price < ctx.ema20_val):
        score, reason = 100.0, "Price confirms EMA20/50 crossover direction"
    elif ctx.ema20_val != ctx.ema50_val:
        score, reason = 60.0, "EMA20/50 crossover present but price not yet confirming"
    else:
        score, reason = 40.0, "EMA20 approx EMA50 -- flat, no clear crossover"

    status = "bullish" if ctx.ema20_val > ctx.ema50_val else "bearish"
    return IndicatorResult(
        indicator_name="ema_alignment", current_value=round(ctx.ema20_val, 2),
        score=score, weight=0.0, contribution=0.0, status=status, enabled=True,
        reason=reason, parameters={"short": EMA_PERIODS["short"], "medium": EMA_PERIODS["medium"]},
    )


def score_ema200_confluence(ctx: IndicatorContext) -> IndicatorResult:
    """Whether the short-term trend agrees with the long-term (EMA200) regime."""
    if ctx.ema200_val is None:
        return IndicatorResult(
            indicator_name="ema200_confluence", current_value=None, score=50.0,
            weight=0.0, contribution=0.0, status="unavailable", enabled=True,
            reason="EMA200 unavailable (needs 200+ candles of history)",
            parameters={"long": EMA_PERIODS["long"]},
        )

    long_term_bullish = ctx.price > ctx.ema200_val
    short_term_bullish = ctx.ema20_val > ctx.ema50_val
    aligned = long_term_bullish == short_term_bullish
    score = 100.0 if aligned else 30.0
    status = "aligned" if aligned else "counter_trend"
    reason = ("Short-term trend aligned with EMA200 (long-term)" if aligned
              else "Short-term trend against EMA200 -- counter-trend risk")
    return IndicatorResult(
        indicator_name="ema200_confluence", current_value=round(ctx.ema200_val, 2),
        score=score, weight=0.0, contribution=0.0, status=status, enabled=True,
        reason=reason, parameters={"long": EMA_PERIODS["long"]},
    )


def score_supertrend_agreement(ctx: IndicatorContext) -> IndicatorResult:
    """Whether signal_logic.calculate_supertrend()'s trend label agrees
    with EMA direction."""
    if ctx.supertrend_trend is None:
        return IndicatorResult(
            indicator_name="supertrend_agreement", current_value=None, score=50.0,
            weight=0.0, contribution=0.0, status="unavailable", enabled=True,
            reason="Supertrend unavailable", parameters={},
        )
    trend_label = "Bullish" if ctx.ema20_val > ctx.ema50_val else "Bearish"
    agrees = ctx.supertrend_trend == trend_label
    score = 100.0 if agrees else 20.0
    status = "agrees" if agrees else "disagrees"
    reason = (f"Supertrend agrees ({ctx.supertrend_trend})" if agrees
              else f"Supertrend disagrees ({ctx.supertrend_trend})")
    return IndicatorResult(
        indicator_name="supertrend_agreement", current_value=None, score=score,
        weight=0.0, contribution=0.0, status=status, enabled=True, reason=reason, parameters={},
    )


def score_adx_strength(ctx: IndicatorContext) -> IndicatorResult:
    """ADX used as a trend-STRENGTH gate (see score_adx_momentum_confirmation
    for ADX's separate use as a momentum-confirming signal)."""
    if ctx.adx_val is None:
        return IndicatorResult(
            indicator_name="adx_strength", current_value=None, score=50.0,
            weight=0.0, contribution=0.0, status="unavailable", enabled=True,
            reason="ADX unavailable", parameters=dict(ADX_THRESHOLDS),
        )
    if ctx.adx_val >= ADX_THRESHOLDS["strong_at_or_above"]:
        score, status, reason = 100.0, "strong", f"Strong trend strength (ADX {ctx.adx_val})"
    elif ctx.adx_val < ADX_THRESHOLDS["weak_below"]:
        score, status, reason = 15.0, "weak", f"Weak/choppy trend (ADX {ctx.adx_val})"
    else:
        score, status, reason = 60.0, "moderate", f"Moderate trend strength (ADX {ctx.adx_val})"
    return IndicatorResult(
        indicator_name="adx_strength", current_value=ctx.adx_val, score=score,
        weight=0.0, contribution=0.0, status=status, enabled=True, reason=reason,
        parameters=dict(ADX_THRESHOLDS),
    )


# ---- Momentum Engine indicators ----

def score_rsi(ctx: IndicatorContext) -> IndicatorResult:
    """RSI zone classification, per technical_config.RSI_ZONES."""
    if ctx.rsi_val is None:
        return IndicatorResult(
            indicator_name="rsi", current_value=None, score=50.0, weight=0.0,
            contribution=0.0, status="unavailable", enabled=True,
            reason="RSI unavailable", parameters={"period": RSI_PERIOD},
        )
    z = RSI_ZONES
    if z["healthy_low"] <= ctx.rsi_val <= z["healthy_high"]:
        score, status, reason = 100.0, "healthy", f"RSI {ctx.rsi_val} in healthy trending zone"
    elif z["acceptable_low"] <= ctx.rsi_val < z["healthy_low"] or z["healthy_high"] < ctx.rsi_val <= z["acceptable_high"]:
        score, status, reason = 60.0, "acceptable", f"RSI {ctx.rsi_val} acceptable but not ideal"
    else:
        zone = "overbought" if ctx.rsi_val > z["acceptable_high"] else "oversold"
        score, status, reason = 20.0, zone, f"RSI {ctx.rsi_val} {zone}"
    return IndicatorResult(
        indicator_name="rsi", current_value=ctx.rsi_val, score=score, weight=0.0,
        contribution=0.0, status=status, enabled=True, reason=reason,
        parameters={"period": RSI_PERIOD, **RSI_ZONES},
    )


def score_macd(ctx: IndicatorContext) -> IndicatorResult:
    """MACD line/signal/histogram directional consistency."""
    if ctx.macd_line is None or ctx.macd_signal_val is None:
        return IndicatorResult(
            indicator_name="macd", current_value=None, score=50.0, weight=0.0,
            contribution=0.0, status="unavailable", enabled=True,
            reason="MACD unavailable", parameters=dict(MACD_PARAMS),
        )
    if ctx.macd_hist is not None and ctx.macd_hist > 0 and ctx.macd_line > ctx.macd_signal_val:
        score, status, reason = 100.0, "bullish", "MACD bullish: line above signal, positive histogram"
    elif ctx.macd_hist is not None and ctx.macd_hist < 0 and ctx.macd_line < ctx.macd_signal_val:
        score, status, reason = 100.0, "bearish", "MACD bearish: line below signal, negative histogram (directionally consistent)"
    else:
        score, status, reason = 35.0, "mixed", "MACD mixed / transitioning"
    return IndicatorResult(
        indicator_name="macd", current_value=ctx.macd_line, score=score, weight=0.0,
        contribution=0.0, status=status, enabled=True, reason=reason, parameters=dict(MACD_PARAMS),
    )


def score_adx_momentum_confirmation(ctx: IndicatorContext) -> IndicatorResult:
    """ADX as a momentum-confirming signal (distinct weight/purpose from
    score_adx_strength(), which uses ADX as a trend-strength gate)."""
    if ctx.adx_val is None:
        return IndicatorResult(
            indicator_name="adx_momentum_confirmation", current_value=None, score=50.0,
            weight=0.0, contribution=0.0, status="unavailable", enabled=True,
            reason="ADX unavailable", parameters=dict(ADX_THRESHOLDS),
        )
    if ctx.adx_val >= ADX_THRESHOLDS["strong_at_or_above"]:
        score, status, reason = 100.0, "strong", f"ADX {ctx.adx_val} confirms momentum has strength behind it"
    else:
        score, status, reason = 40.0, "weak", f"ADX {ctx.adx_val} does not confirm strong momentum"
    return IndicatorResult(
        indicator_name="adx_momentum_confirmation", current_value=ctx.adx_val, score=score,
        weight=0.0, contribution=0.0, status=status, enabled=True, reason=reason,
        parameters=dict(ADX_THRESHOLDS),
    )


# ---- Volume Engine indicators ----

def score_relative_volume(ctx: IndicatorContext) -> IndicatorResult:
    """20-candle relative volume classification, per
    technical_config.VOLUME_SETTINGS."""
    if ctx.rel_vol is None:
        return IndicatorResult(
            indicator_name="relative_volume", current_value=None, score=50.0,
            weight=0.0, contribution=0.0, status="unavailable", enabled=True,
            reason="Relative volume unavailable", parameters=dict(VOLUME_SETTINGS),
        )
    v = VOLUME_SETTINGS
    if ctx.rel_vol >= v["spike_threshold"]:
        score, status, reason = 100.0, "spike", f"Volume spike: {ctx.rel_vol}x 20-candle avg"
    elif ctx.rel_vol >= v["high_threshold"]:
        score, status, reason = 80.0, "high", f"High relative volume: {ctx.rel_vol}x 20-candle avg"
    elif ctx.rel_vol >= v["normal_threshold"]:
        score, status, reason = 55.0, "normal", f"Normal volume: {ctx.rel_vol}x 20-candle avg"
    else:
        score, status, reason = 25.0, "low", f"Low volume: {ctx.rel_vol}x 20-candle avg -- weak participation"
    return IndicatorResult(
        indicator_name="relative_volume", current_value=ctx.rel_vol, score=score,
        weight=0.0, contribution=0.0, status=status, enabled=True, reason=reason,
        parameters=dict(VOLUME_SETTINGS),
    )


def score_volume_spike(ctx: IndicatorContext) -> IndicatorResult:
    """Binary spike flag as its own indicator, separate from
    relative_volume's graded score -- kept distinct because the task
    spec lists "Relative Volume" and "Volume Spike" as two separate
    required indicators, and a future optimizer may want to weight the
    binary flag differently from the graded ratio (e.g. spike matters a
    lot for breakout strategies, less for mean-reversion ones)."""
    if ctx.rel_vol is None:
        return IndicatorResult(
            indicator_name="volume_spike", current_value=None, score=50.0,
            weight=0.0, contribution=0.0, status="unavailable", enabled=True,
            reason="Volume spike flag unavailable (no relative volume data)",
            parameters={"spike_threshold": VOLUME_SETTINGS["spike_threshold"]},
        )
    if ctx.vol_spike:
        score, status, reason = 100.0, "spike_confirmed", "Volume spike flag triggered"
    else:
        score, status, reason = 50.0, "no_spike", "No volume spike this candle"
    return IndicatorResult(
        indicator_name="volume_spike", current_value=(1.0 if ctx.vol_spike else 0.0),
        score=score, weight=0.0, contribution=0.0, status=status, enabled=True,
        reason=reason, parameters={"spike_threshold": VOLUME_SETTINGS["spike_threshold"]},
    )


# ---- Volatility Engine indicators ----

def score_expected_move(ctx: IndicatorContext) -> IndicatorResult:
    """ATR-derived ExpectedMove% sanity-band scoring, per
    technical_config.EXPECTED_MOVE_BOUNDS."""
    bounds = EXPECTED_MOVE_BOUNDS
    if ctx.expected_move_pct is None:
        return IndicatorResult(
            indicator_name="expected_move", current_value=None, score=50.0,
            weight=0.0, contribution=0.0, status="unavailable", enabled=True,
            reason="ExpectedMove% unavailable", parameters=dict(bounds),
        )
    min_pct, max_pct = bounds["min_pct"], bounds["max_pct"]
    if min_pct <= ctx.expected_move_pct <= max_pct:
        mid = (min_pct + max_pct) / 2
        span = (max_pct - min_pct) / 2
        distance = abs(ctx.expected_move_pct - mid) / span if span > 0 else 0
        score = 100.0 - (distance * 30)
        status, reason = "healthy", f"ExpectedMove {ctx.expected_move_pct}% within valid range ({min_pct}-{max_pct}%)"
    elif ctx.expected_move_pct < min_pct:
        score, status, reason = 20.0, "too_low", f"ExpectedMove {ctx.expected_move_pct}% too low -- dead/illiquid session"
    else:
        score, status, reason = 15.0, "too_high", f"ExpectedMove {ctx.expected_move_pct}% too high -- possible news/gap spike"
    return IndicatorResult(
        indicator_name="expected_move", current_value=ctx.expected_move_pct,
        score=round(score, 1), weight=0.0, contribution=0.0, status=status,
        enabled=True, reason=reason, parameters=dict(bounds),
    )


def score_bollinger_bands(ctx: IndicatorContext) -> IndicatorResult:
    """Bollinger %b band-extension scoring."""
    if ctx.pct_b is None:
        return IndicatorResult(
            indicator_name="bollinger_bands", current_value=None, score=50.0,
            weight=0.0, contribution=0.0, status="unavailable", enabled=True,
            reason="Bollinger Bands unavailable", parameters=dict(BOLLINGER_SETTINGS),
        )
    if ctx.pct_b > 1.0:
        score, status, reason = 30.0, "extended_above", "Price above upper Bollinger Band -- extended"
    elif ctx.pct_b < 0.0:
        score, status, reason = 30.0, "extended_below", "Price below lower Bollinger Band -- extended"
    else:
        score = max(50.0, 100.0 - abs(ctx.pct_b - 0.5) * 80)
        status, reason = "within_bands", f"Price within Bollinger Bands (%b={ctx.pct_b})"
    return IndicatorResult(
        indicator_name="bollinger_bands", current_value=ctx.pct_b, score=round(score, 1),
        weight=0.0, contribution=0.0, status=status, enabled=True, reason=reason,
        parameters=dict(BOLLINGER_SETTINGS),
    )


# ---- Price Engine indicators ----

def score_vwap_distance(ctx: IndicatorContext) -> IndicatorResult:
    """Price's distance from session VWAP, per
    technical_config.VWAP_DISTANCE_BANDS. This is the ONLY Price Engine
    indicator implemented today. The Price Engine is registry-based
    specifically so future components (support/resistance, pivots,
    breakouts, gap analysis, ORB, price structure, distance-from-high/low
    -- see technical_config.PRICE_FUTURE_COMPONENTS) can be added later
    by writing a function with this same signature and calling
    register_indicator("price", "<name>", <function>) -- no change to
    PRICE_INDICATOR_REGISTRY's consumers (run_engine(),
    generate_technical_report()) is required."""
    score, reasons = calculate_price_score(ctx.price, ctx.vwap_val)
    status = "unavailable" if ctx.vwap_val is None else ("above_vwap" if ctx.price > ctx.vwap_val else "below_vwap")
    return IndicatorResult(
        indicator_name="vwap_distance", current_value=ctx.vwap_val, score=score,
        weight=0.0, contribution=0.0, status=status, enabled=True,
        reason=" | ".join(reasons), parameters=dict(VWAP_DISTANCE_BANDS),
    )


# ============================================================
# R1: ENGINE REGISTRIES (Req 5, Req 8 -- extensibility without redesign)
# ============================================================

TREND_INDICATOR_REGISTRY: Dict[str, Callable[[IndicatorContext], IndicatorResult]] = {
    "ema_alignment": score_ema_alignment,
    "ema200_confluence": score_ema200_confluence,
    "supertrend_agreement": score_supertrend_agreement,
    "adx_strength": score_adx_strength,
}

MOMENTUM_INDICATOR_REGISTRY: Dict[str, Callable[[IndicatorContext], IndicatorResult]] = {
    "rsi": score_rsi,
    "macd": score_macd,
    "adx_momentum_confirmation": score_adx_momentum_confirmation,
}

VOLUME_INDICATOR_REGISTRY: Dict[str, Callable[[IndicatorContext], IndicatorResult]] = {
    "relative_volume": score_relative_volume,
    "volume_spike": score_volume_spike,
    # Future: "delivery_percentage", "open_interest", "volume_profile",
    # "institutional_volume" -- see technical_config.VOLUME_FUTURE_SOURCES.
    # Not implemented; add via register_indicator("volume", ...) when ready.
}

VOLATILITY_INDICATOR_REGISTRY: Dict[str, Callable[[IndicatorContext], IndicatorResult]] = {
    "expected_move": score_expected_move,
    "bollinger_bands": score_bollinger_bands,
}

PRICE_INDICATOR_REGISTRY: Dict[str, Callable[[IndicatorContext], IndicatorResult]] = {
    "vwap_distance": score_vwap_distance,
    # Future: support/resistance, pivots, breakouts, gap analysis, ORB,
    # price structure, distance-from-high/low -- see
    # technical_config.PRICE_FUTURE_COMPONENTS. Not implemented; add via
    # register_indicator("price", ...) when ready.
}

ENGINE_REGISTRIES: Dict[str, Dict[str, Callable]] = {
    "trend": TREND_INDICATOR_REGISTRY,
    "momentum": MOMENTUM_INDICATOR_REGISTRY,
    "volume": VOLUME_INDICATOR_REGISTRY,
    "volatility": VOLATILITY_INDICATOR_REGISTRY,
    "price": PRICE_INDICATOR_REGISTRY,
}


def register_indicator(engine_name: str, indicator_name: str,
                        fn: Callable[[IndicatorContext], IndicatorResult]) -> None:
    """Register a new indicator function for the given engine at runtime.

    This is the extensibility mechanism required by Req 5/8: adding a
    future indicator (support/resistance, open interest, delivery %,
    volume profile, etc.) means writing one function with the signature
    `(ctx: IndicatorContext) -> IndicatorResult` and calling this once --
    no changes to run_engine(), generate_technical_report(), or any
    engine's internal structure are needed.

    Also remember to add the indicator's name to
    technical_config.ENABLED_INDICATORS (defaults to enabled if the key
    is absent -- see run_engine()) and optionally to
    technical_config.INDICATOR_WEIGHTS if it shouldn't split weight
    equally with its engine's other indicators.

    Parameters
    ----------
    engine_name : one of "trend", "momentum", "volume", "volatility", "price".
    indicator_name : stable key for this indicator (used in config
        lookups and in the IndicatorResult it produces).
    fn : the scoring function.
    """
    registry = ENGINE_REGISTRIES.get(engine_name)
    if registry is None:
        raise ValueError(f"Unknown engine '{engine_name}'. Valid engines: {list(ENGINE_REGISTRIES.keys())}")
    registry[indicator_name] = fn


# ============================================================
# R1: ENGINE RUNNER
# ============================================================

def run_engine(engine_name: str, ctx: IndicatorContext) -> EngineResult:
    """Runs every registered, enabled indicator for the given engine
    against the given context, resolves per-indicator weights (from
    technical_config.INDICATOR_WEIGHTS if set for this engine, otherwise
    an equal split across enabled indicators), and aggregates into an
    EngineResult.

    Disabled indicators (technical_config.ENABLED_INDICATORS[name] ==
    False) are still included in the returned EngineResult.indicators
    list for transparency, but excluded from scoring/weighting."""
    registry = ENGINE_REGISTRIES[engine_name]
    all_results: List[IndicatorResult] = []
    enabled_results: List[IndicatorResult] = []

    for name, fn in registry.items():
        if not ENABLED_INDICATORS.get(name, True):
            r = IndicatorResult(
                indicator_name=name, current_value=None, score=0.0, weight=0.0,
                contribution=0.0, status="disabled", enabled=False,
                reason="Disabled via technical_config.ENABLED_INDICATORS", parameters={},
            )
            all_results.append(r)
            continue
        r = fn(ctx)
        all_results.append(r)
        enabled_results.append(r)

    per_engine_weights = INDICATOR_WEIGHTS.get(engine_name, {})
    n = len(enabled_results)
    for r in enabled_results:
        w = per_engine_weights.get(r.indicator_name, (100.0 / n if n else 0.0))
        r.weight = round(w, 2)
        r.contribution = round(r.score * r.weight / 100.0, 2)

    engine_score = round(sum(r.contribution for r in enabled_results), 1) if enabled_results else 50.0
    weight_pct = TECHNICAL_WEIGHTS.get(engine_name, 0)
    contribution = round(engine_score * weight_pct / 100.0, 1)
    status = "bullish" if engine_score >= 60 else "bearish" if engine_score <= 40 else "neutral"

    return EngineResult(
        engine_name=engine_name, score=engine_score, weight=weight_pct,
        contribution=contribution, indicators=all_results, status=status,
    )


# ============================================================
# R1: CONTEXT BUILDER + MAIN ENTRY POINT
# ============================================================

def build_indicator_context(
    price: float,
    candles: list,
    symbol: Optional[str] = None,
    timeframe: str = DEFAULT_TIMEFRAME,
    asset_class: str = DEFAULT_ASSET_CLASS,
    ema20_val: Optional[float] = None,
    ema50_val: Optional[float] = None,
    atr_val: Optional[float] = None,
    supertrend_trend: Optional[str] = None,
    adx_val: Optional[float] = None,
) -> Optional[IndicatorContext]:
    """Builds the shared IndicatorContext for one instrument's scan
    cycle. Precomputed values (ema20_val, ema50_val, atr_val,
    supertrend_trend, adx_val) can be passed in from scanner.py to avoid
    recomputing values it already has; anything omitted is computed here
    using signal_logic.py's existing functions -- never reimplemented.

    Returns None if there isn't enough candle history to compute even
    EMA20/EMA50 -- callers should treat that as "insufficient data" and
    not attempt to run the engines."""
    closes = [c[4] for c in reversed(candles)] if candles else []

    if ema20_val is None and len(closes) >= EMA_PERIODS["short"]:
        ema20_val = ema(closes, EMA_PERIODS["short"])
    if ema50_val is None and len(closes) >= EMA_PERIODS["medium"]:
        ema50_val = ema(closes, EMA_PERIODS["medium"])
    if atr_val is None and candles:
        atr_val = atr(candles)

    if ema20_val is None or ema50_val is None:
        return None

    derived = _compute_derived_indicators(price, candles, closes, atr_val, adx_val, supertrend_trend)

    return IndicatorContext(
        symbol=symbol, price=price, candles=candles, closes=closes,
        timeframe=timeframe, asset_class=asset_class,
        ema20_val=ema20_val, ema50_val=ema50_val, ema200_val=derived["ema200_val"],
        atr_val=atr_val, adx_val=derived["adx_val"], supertrend_trend=derived["supertrend_trend"],
        rsi_val=derived["rsi_val"], macd_line=derived["macd_line"],
        macd_signal_val=derived["macd_signal_val"], macd_hist=derived["macd_hist"],
        vwap_val=derived["vwap_val"], rel_vol=derived["rel_vol"], vol_spike=derived["vol_spike"],
        pct_b=derived["pct_b"], expected_move_pct=derived["expected_move_pct"],
    )


def generate_technical_report(
    price: float,
    candles: list,
    symbol: Optional[str] = None,
    timeframe: str = DEFAULT_TIMEFRAME,
    asset_class: str = DEFAULT_ASSET_CLASS,
    ema20_val: Optional[float] = None,
    ema50_val: Optional[float] = None,
    atr_val: Optional[float] = None,
    supertrend_trend: Optional[str] = None,
    adx_val: Optional[float] = None,
) -> TechnicalReport:
    """R1's main entry point (Req 4). Builds an IndicatorContext, runs
    all five engines via their registries, and returns a full
    TechnicalReport.

    This function performs a single instrument's analysis per call and
    holds no state between calls -- safe to run across 500+ instruments
    in a loop (or in parallel) without any shared-state risk. Looping
    across the watchlist remains scanner.py's job, unchanged.

    Optional precomputed args (ema20_val, ema50_val, atr_val,
    supertrend_trend, adx_val) let a caller that already computed these
    this cycle (e.g. scanner.py, once wired in) skip redundant work.

    Never raises on insufficient/bad data -- returns a TechnicalReport
    with technical_score=0.0, confidence=0.0, and a warning explaining
    why, so a single bad instrument can't crash a batch run over 500+
    symbols."""
    ctx = build_indicator_context(
        price, candles, symbol, timeframe, asset_class,
        ema20_val, ema50_val, atr_val, supertrend_trend, adx_val,
    )

    if ctx is None:
        empty = EngineResult(engine_name="unavailable", score=0.0, weight=0.0,
                              contribution=0.0, indicators=[], status="unavailable")
        return TechnicalReport(
            symbol=symbol, timeframe=timeframe, asset_class=asset_class,
            trend_engine=empty, momentum_engine=empty, volume_engine=empty,
            volatility_engine=empty, price_engine=empty, technical_score=0.0,
            confidence=0.0, trend="Unknown", strengths=[], weaknesses=[],
            warnings=["Insufficient candle history for technical scoring"],
            indicator_results=[],
        )

    trend_engine = run_engine("trend", ctx)
    momentum_engine = run_engine("momentum", ctx)
    volume_engine = run_engine("volume", ctx)
    volatility_engine = run_engine("volatility", ctx)
    price_engine = run_engine("price", ctx)

    engines = [trend_engine, momentum_engine, volume_engine, volatility_engine, price_engine]
    technical_score = round(sum(e.contribution for e in engines), 1)
    trend = "Bullish" if ctx.ema20_val > ctx.ema50_val else "Bearish"

    all_indicators = [i for e in engines for i in e.indicators]
    enabled_indicators = [i for i in all_indicators if i.enabled]
    valid_indicators = [i for i in enabled_indicators if i.status != "unavailable"]
    confidence = round((len(valid_indicators) / len(enabled_indicators)) * 100, 1) if enabled_indicators else 0.0

    strengths = [f"{i.indicator_name}: {i.reason}" for i in enabled_indicators if i.score >= 75]
    weaknesses = [f"{i.indicator_name}: {i.reason}" for i in enabled_indicators if i.score <= 30]
    warning_keywords = ("against", "weak", "disagree", "overbought", "oversold", "extended",
                         "too low", "too high", "chase risk")
    warnings = list(dict.fromkeys(
        i.reason for i in enabled_indicators
        if any(kw in i.reason.lower() for kw in warning_keywords)
    ))

    return TechnicalReport(
        symbol=symbol, timeframe=timeframe, asset_class=asset_class,
        trend_engine=trend_engine, momentum_engine=momentum_engine,
        volume_engine=volume_engine, volatility_engine=volatility_engine,
        price_engine=price_engine, technical_score=technical_score,
        confidence=confidence, trend=trend, strengths=strengths,
        weaknesses=weaknesses, warnings=warnings, indicator_results=all_indicators,
    )
