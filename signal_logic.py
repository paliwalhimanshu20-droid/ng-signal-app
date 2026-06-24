"""
signal_logic.py
================
Pure indicator + signal-scoring logic for ng-signal-app, with ZERO
dependency on Streamlit, requests, or any network/secrets access.

WHY THIS FILE EXISTS (read before editing):
This module is imported by BOTH:
  - app.py        (the live Streamlit dashboard — real-time signals)
  - backtest.py   (the offline historical threshold/sensitivity tester)

The entire point of backtest.py is to answer "would this scoring logic
have worked historically?" — which is only a meaningful question if
backtest.py runs the EXACT SAME scoring code as the live app. If the two
ever drift (e.g. you tweak a threshold in app.py but forget here, or vice
versa), the backtest silently stops representing live behavior and every
conclusion from it becomes misleading.

Rule of thumb: any change to indicator math or scoring rules belongs here,
not duplicated separately in app.py or backtest.py.

Candle format expected everywhere in this module (matches Upstox's raw
historical-candle response, NEWEST-FIRST unless stated otherwise):
    [timestamp, open, high, low, close, volume, oi]
"""

# ================= MOVING AVERAGES =================

def ema(prices, period):
    """Simple EMA of a chronological (oldest-first) list of closes."""
    m = 2 / (period + 1)
    e = prices[0]
    for p in prices[1:]:
        e = (p - e) * m + e
    return e


# ================= ATR =================

def atr(candles, period=14):
    """
    Average True Range — simple average of the most recent `period` true
    ranges. Used for SL/T1/T2 sizing and the ExpectedMove% volatility read.

    BUGFIX (found while extracting this for the Supertrend/backtest work):
    the original version read `h, l, pc = candles[i][1], candles[i][2],
    candles[i-1][4]` — that's OPEN and HIGH (indices 1, 2), not HIGH and
    LOW (indices 2, 3). True range was therefore computed without ever
    looking at the candle's actual low, which systematically UNDERSTATES
    volatility (the low is very often where the largest excursion is).
    That means every SL/T1/T2 distance and every ExpectedMove% reading
    produced by the old code was tighter than it should have been —
    independent of anything in this conversation's 6-point list, this is
    a correctness fix to the existing app. Re-test your SL/target spacing
    after this change; it will look wider than before, and that's expected.

    candles: newest-first, [timestamp, open, high, low, close, volume, oi]
    """
    trs = []
    for i in range(1, len(candles)):
        h, l, pc = candles[i][2], candles[i][3], candles[i - 1][4]
        trs.append(max(h - l, abs(h - pc), abs(l - pc)))

    if not trs:
        return 0

    recent = trs[-period:] if len(trs) >= period else trs
    return sum(recent) / len(recent)


# ================= RSI =================

def rsi(prices, period=14):
    """Standard Wilder's RSI. prices: chronological (oldest-first) closes."""
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


# ================= SUPERTREND =================

SUPERTREND_PERIOD = 10
SUPERTREND_MULTIPLIER = 3.0


def calculate_supertrend(candles, period=SUPERTREND_PERIOD, multiplier=SUPERTREND_MULTIPLIER):
    """
    Computes the Supertrend indicator from raw candles (newest-first,
    format: [timestamp, open, high, low, close, volume, oi]).

    Returns None if there isn't enough candle history for a stable read
    (needs at least period + 2 bars). Otherwise returns a dict:
      "timestamps":   raw timestamps, chronological (oldest -> newest)
      "supertrend":   Supertrend line value per bar (None during warm-up)
      "trend":        "Bullish" / "Bearish" per bar (None during warm-up)
      "latest_trend": most recent "Bullish"/"Bearish", or None
      "latest_value": most recent Supertrend line value, or None
    """
    if not candles or len(candles) < period + 2:
        return None

    ordered = list(reversed(candles))  # chronological, oldest first

    timestamps = [c[0] for c in ordered]
    highs = [c[2] for c in ordered]
    lows = [c[3] for c in ordered]
    closes = [c[4] for c in ordered]

    n = len(closes)

    trs = [highs[0] - lows[0]]
    for i in range(1, n):
        tr = max(
            highs[i] - lows[i],
            abs(highs[i] - closes[i - 1]),
            abs(lows[i] - closes[i - 1])
        )
        trs.append(tr)

    atr_vals = [None] * n
    seed = sum(trs[:period]) / period
    atr_vals[period - 1] = seed
    for i in range(period, n):
        atr_vals[i] = (atr_vals[i - 1] * (period - 1) + trs[i]) / period

    final_upper = [None] * n
    final_lower = [None] * n
    st_line = [None] * n
    st_is_bullish = [None] * n

    start = period - 1

    for i in range(start, n):
        mid = (highs[i] + lows[i]) / 2
        basic_upper = mid + multiplier * atr_vals[i]
        basic_lower = mid - multiplier * atr_vals[i]

        if i == start:
            final_upper[i] = basic_upper
            final_lower[i] = basic_lower
            st_is_bullish[i] = closes[i] >= mid
            st_line[i] = final_lower[i] if st_is_bullish[i] else final_upper[i]
            continue

        prev_close = closes[i - 1]

        final_upper[i] = (
            basic_upper if (basic_upper < final_upper[i - 1] or prev_close > final_upper[i - 1])
            else final_upper[i - 1]
        )
        final_lower[i] = (
            basic_lower if (basic_lower > final_lower[i - 1] or prev_close < final_lower[i - 1])
            else final_lower[i - 1]
        )

        prev_bullish = st_is_bullish[i - 1]

        if prev_bullish:
            st_is_bullish[i] = closes[i] >= final_lower[i]
        else:
            st_is_bullish[i] = closes[i] > final_upper[i]

        st_line[i] = final_lower[i] if st_is_bullish[i] else final_upper[i]

    trend_labels = [
        (None if b is None else ("Bullish" if b else "Bearish"))
        for b in st_is_bullish
    ]

    latest_trend, latest_value = None, None
    for i in range(n - 1, -1, -1):
        if trend_labels[i] is not None:
            latest_trend = trend_labels[i]
            latest_value = round(st_line[i], 2) if st_line[i] is not None else None
            break

    return {
        "timestamps": timestamps,
        "supertrend": st_line,
        "trend": trend_labels,
        "latest_trend": latest_trend,
        "latest_value": latest_value,
    }


# ================= ADX (trend STRENGTH, point #2) =================

def compute_adx(candles, period=14):
    """
    Wilder's ADX(period) — trend STRENGTH on a 0-100 scale, independent of
    direction. A clean EMA20/50 crossover during a low-ADX, range-bound
    market is statistically much closer to a coin flip than the same
    crossover during a high-ADX trend — ADX is what tells those two
    situations apart, which nothing else in this engine previously did.

    candles: newest-first, [timestamp, open, high, low, close, volume, oi]
    Returns the latest ADX value (float, 2dp), or None if there isn't
    enough history (~2*period bars needed to get past Wilder's
    double-smoothing warm-up).
    """
    if not candles or len(candles) < period * 2 + 1:
        return None

    ordered = list(reversed(candles))
    highs = [c[2] for c in ordered]
    lows = [c[3] for c in ordered]
    closes = [c[4] for c in ordered]
    n = len(closes)

    trs, plus_dms, minus_dms = [], [], []
    for i in range(1, n):
        up_move = highs[i] - highs[i - 1]
        down_move = lows[i - 1] - lows[i]

        plus_dm = up_move if (up_move > down_move and up_move > 0) else 0.0
        minus_dm = down_move if (down_move > up_move and down_move > 0) else 0.0

        tr = max(
            highs[i] - lows[i],
            abs(highs[i] - closes[i - 1]),
            abs(lows[i] - closes[i - 1])
        )

        trs.append(tr)
        plus_dms.append(plus_dm)
        minus_dms.append(minus_dm)

    m = len(trs)
    if m < period * 2:
        return None

    smoothed_tr = sum(trs[:period])
    smoothed_plus_dm = sum(plus_dms[:period])
    smoothed_minus_dm = sum(minus_dms[:period])

    def _dx(tr_s, pdm_s, mdm_s):
        if tr_s == 0:
            return 0.0
        plus_di = 100 * (pdm_s / tr_s)
        minus_di = 100 * (mdm_s / tr_s)
        denom = plus_di + minus_di
        if denom == 0:
            return 0.0
        return 100 * abs(plus_di - minus_di) / denom

    dx_vals = [_dx(smoothed_tr, smoothed_plus_dm, smoothed_minus_dm)]

    for i in range(period, m):
        smoothed_tr = smoothed_tr - (smoothed_tr / period) + trs[i]
        smoothed_plus_dm = smoothed_plus_dm - (smoothed_plus_dm / period) + plus_dms[i]
        smoothed_minus_dm = smoothed_minus_dm - (smoothed_minus_dm / period) + minus_dms[i]
        dx_vals.append(_dx(smoothed_tr, smoothed_plus_dm, smoothed_minus_dm))

    if len(dx_vals) < period:
        return None

    adx_val = sum(dx_vals[:period]) / period
    for dx in dx_vals[period:]:
        adx_val = (adx_val * (period - 1) + dx) / period

    return round(adx_val, 2)


# ADX strength bands used both for scoring and for the factor-performance
# bucketing in app.py — kept here so both stay in sync.
ADX_WEAK_BELOW = 20
ADX_STRONG_AT_OR_ABOVE = 25


# ================= VOLATILITY SANITY BOUNDS (point #5) =================

# ExpectedMove% (ATR / price * 100) outside this band is treated as
# unreliable: too low usually means a dead/illiquid session where any
# "signal" is noise; too high usually means a gap, news spike, or
# corporate-action distortion rather than a tradeable trend. These are
# starting points, not laws of physics — tune against your own
# instruments once you have backtest/live data to check them against.
MIN_EXPECTED_MOVE_PCT = 0.15
MAX_EXPECTED_MOVE_PCT = 5.0


# ================= REGIME =================

def detect_regime(ema20, ema50, price):
    gap = abs(ema20 - ema50)

    if gap > price * 0.01:
        return "TRENDING"
    elif gap > price * 0.005:
        return "BREAKOUT"
    else:
        return "RANGING"


# ================= TREND CONVICTION (point #3 — consolidation) =================

def trend_conviction(trend, daily_trend=None, supertrend_trend=None, market_trend=None):
    """
    Folds every available higher-context trend read (daily EMA trend,
    Supertrend, broader market/index trend) into ONE conviction score
    instead of stacking independent hard gates with independent penalties.

    Why this matters: daily_trend, supertrend_trend, and market_trend are
    all lagging trend-followers computed off related price series — they
    are correlated, not independent evidence. The original design (this
    conversation's earlier step) gave each one its own -2 penalty AND
    required ALL of them to agree before a BUY/SELL could fire. That
    triple-counts what's really one underlying question ("is this trend
    real?") and over-filters: one noisy disagreement among three
    correlated checks could silently kill a signal that two other checks
    confirmed.

    Returns (agree_count, total_count, conviction_pct, reasons):
      - agree_count / total_count only count layers that were actually
        available (not None) — an instrument with no daily-trend data
        yet isn't penalized for it.
      - conviction_pct is None if NO higher-context layer was available
        at all (caller should treat this as "no opinion", not "bad").
      - reasons: list of human-readable strings for the Reason column.
    """
    layers = [
        ("Daily trend", daily_trend),
        ("Supertrend", supertrend_trend),
        ("Market (Nifty) trend", market_trend),
    ]
    available = [(name, val) for name, val in layers if val is not None]

    if not available:
        return None, 0, None, []

    agree = [(name, val) for name, val in available if val == trend]
    disagree = [(name, val) for name, val in available if val != trend]

    agree_count = len(agree)
    total_count = len(available)
    conviction_pct = round((agree_count / total_count) * 100)

    reasons = [f"{name} agrees ({val})" for name, val in agree]
    reasons += [f"⚠ {name} disagrees ({val})" for name, val in disagree]

    return agree_count, total_count, conviction_pct, reasons


# ================= RISK GATES =================
# Single source of truth for the three independent hard gates that decide
# whether a setup is allowed to become an actionable BUY/SELL, regardless
# of how high its score is. signal_engine() uses this AND backtest.py
# (the offline threshold/sensitivity tester) reuses the exact same
# function — so testing "what if the score threshold were 6 instead of
# 8" can never silently diverge from what the live gates actually do.

def passes_risk_gates(conviction_pct, adx_val, expected_move):
    """
    conviction_pct: from trend_conviction(), or None.
    adx_val: from compute_adx(), or None.
    expected_move: ExpectedMove% (ATR/price*100).

    Returns (passes_all: bool, detail: dict) — detail breaks out each
    individual gate so callers (e.g. backtest.py) can report exactly
    which gate blocked a setup, not just a single pass/fail bit.
    """
    detail = {
        "conviction": (conviction_pct is None) or (conviction_pct >= 50),
        "strength": (adx_val is None) or (adx_val >= ADX_WEAK_BELOW),
        "volatility": MIN_EXPECTED_MOVE_PCT <= expected_move <= MAX_EXPECTED_MOVE_PCT,
    }
    return all(detail.values()), detail


# ================= SIGNAL ENGINE =================

def signal_engine(price, ema20, ema50, atr_val, daily_trend=None, supertrend_trend=None,
                   market_trend=None, adx_val=None):
    """
    price/ema20/ema50/atr_val: same as before — the 30-min-timeframe
    base inputs.

    daily_trend / supertrend_trend / market_trend: each "Bullish",
    "Bearish", or None. Consolidated into ONE conviction score via
    trend_conviction() — see that function's docstring for why these
    three are no longer independently gated/penalized (point #3).

    adx_val: ADX(14), trend STRENGTH not direction (point #2). Treated
    separately from conviction because strength and direction are
    different questions — an instrument can have a strong consensus
    direction (high conviction) in a weak, directionless market (low
    ADX) or vice versa.

    Also applies a volatility sanity filter (point #5): ExpectedMove%
    outside [MIN_EXPECTED_MOVE_PCT, MAX_EXPECTED_MOVE_PCT] is penalized.

    Returns: signal, score, probability, trend, regime, expected_move,
             reasons, conviction_pct
    (conviction_pct is returned so callers can log it for later factor-
    performance analysis without recomputing it.)
    """
    score = 4
    reasons = ["Base Score"]

    trend = "Bullish" if ema20 > ema50 else "Bearish"
    regime = detect_regime(ema20, ema50, price)
    expected_move = round((atr_val / price) * 100, 2) if atr_val else 0.0

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

    # --- Consolidated trend conviction (point #1 market regime feeds in
    # here too, as one of the three layers) ---
    agree_count, total_count, conviction_pct, conviction_reasons = trend_conviction(
        trend, daily_trend, supertrend_trend, market_trend
    )
    reasons.extend(conviction_reasons)

    if conviction_pct is None:
        # No higher-context data available at all — don't block on it,
        # just don't reward it either.
        pass
    elif conviction_pct == 100:
        score += 2
    elif conviction_pct >= 50:
        score += 1
    elif conviction_pct > 0:
        score -= 2
    else:
        score -= 3

    # --- ADX trend-strength filter (point #2) — score penalty/bonus only;
    # the actual gating decision is centralized in passes_risk_gates() below.
    if adx_val is not None:
        if adx_val < ADX_WEAK_BELOW:
            score -= 2
            reasons.append(f"⚠ Weak trend strength (ADX {adx_val}) — choppy/range risk")
        elif adx_val >= ADX_STRONG_AT_OR_ABOVE:
            score += 1
            reasons.append(f"Strong trend strength (ADX {adx_val})")

    # --- Volatility sanity filter (point #5) — same: score effect here,
    # gating decision centralized below.
    if expected_move < MIN_EXPECTED_MOVE_PCT:
        score -= 2
        reasons.append(f"⚠ Volatility too low ({expected_move}%) — dead session, unreliable")
    elif expected_move > MAX_EXPECTED_MOVE_PCT:
        score -= 2
        reasons.append(f"⚠ Volatility extreme ({expected_move}%) — possible news/gap spike")

    score = max(0, min(score, 10))
    probability = int((score / 10) * 100)

    passes_all, _gate_detail = passes_risk_gates(conviction_pct, adx_val, expected_move)

    if score >= 8 and passes_all:
        signal = "BUY" if trend == "Bullish" else "SELL"
    elif score >= 6:
        signal = "WATCH"
    else:
        signal = "NO TRADE"

    return signal, score, probability, trend, regime, expected_move, reasons, conviction_pct


# ================= LEVELS =================

def levels(price, atr_val, signal, trend, regime="TRENDING"):
    """
    regime: "TRENDING", "BREAKOUT", or "RANGING" — from detect_regime().
    Position sizing adapts to regime:
      - TRENDING: wider targets (price has room to run)
      - BREAKOUT: standard sizing
      - RANGING: tighter targets/stops
    Returns (None, None, None) if atr_val is invalid (0/None) so the
    caller can show "N/A" instead of a misleading repeated price.
    """
    if not atr_val or atr_val <= 0:
        return None, None, None

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
      
