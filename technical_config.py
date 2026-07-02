"""
technical_config.py
====================
NGSP-001-R1 — Configuration layer for the Technical Intelligence Engine.

PURPOSE
-------
Every tunable number that technical_engine.py uses lives here — periods,
thresholds, weights, feature toggles. technical_engine.py should never
contain a bare magic number where this file could hold it instead.

Why this file exists as a separate module (not folded into
technical_engine.py or config.py): the future AI Strategy Optimizer
(not built yet — out of scope for this task) will need to read and
rewrite these values programmatically, per-instrument, without touching
any scoring logic. Keeping config physically separate from logic is
what makes that possible later without a rewrite.

RELATIONSHIP TO signal_logic.py
--------------------------------
signal_logic.py's live BUY/SELL/WATCH gate (signal_engine()) has its own
ADX and ExpectedMove% thresholds (ADX_WEAK_BELOW, ADX_STRONG_AT_OR_ABOVE,
MIN_EXPECTED_MOVE_PCT, MAX_EXPECTED_MOVE_PCT) that scanner.py depends on
today. This file imports those as the DEFAULT values for the Technical
Intelligence Engine's own scoring (so the two systems agree by default),
but assigns them to independent names here. That decoupling is
intentional: tuning the Technical Engine's scoring in the future must
never require editing signal_logic.py or risk changing scanner.py's live
gating behaviour. If you want the two to diverge later, just edit the
values below — signal_logic.py is unaffected either way.

NOTHING IN THIS FILE CHANGES SCANNER.PY, APP.PY, OR THE DASHBOARD.
"""

from signal_logic import (
    ADX_WEAK_BELOW as _SIGNAL_LOGIC_ADX_WEAK_BELOW,
    ADX_STRONG_AT_OR_ABOVE as _SIGNAL_LOGIC_ADX_STRONG_AT_OR_ABOVE,
    MIN_EXPECTED_MOVE_PCT as _SIGNAL_LOGIC_MIN_EXPECTED_MOVE_PCT,
    MAX_EXPECTED_MOVE_PCT as _SIGNAL_LOGIC_MAX_EXPECTED_MOVE_PCT,
)


# ============================================================
# 1. TREND INDICATORS
# ============================================================

EMA_PERIODS = {
    "short": 20,    # EMA20 — short-term trend
    "medium": 50,   # EMA50 — medium-term trend
    "long": 200,    # EMA200 — long-term regime filter
}

# Note: Supertrend's enable/disable is handled via
# ENABLED_INDICATORS["supertrend_agreement"] below, not a separate flag
# here -- one toggle mechanism per indicator, not two.

ADX_THRESHOLDS = {
    # Defaults mirror signal_logic.py's live gate today (see module
    # docstring above) but are independently editable.
    "weak_below": _SIGNAL_LOGIC_ADX_WEAK_BELOW,
    "strong_at_or_above": _SIGNAL_LOGIC_ADX_STRONG_AT_OR_ABOVE,
}


# ============================================================
# 2. MOMENTUM INDICATORS
# ============================================================

RSI_PERIOD = 14

RSI_ZONES = {
    # (low, high): descriptive label — used by score_rsi() to classify
    # the current reading. Ranges are inclusive of "low", exclusive of
    # next band's "low".
    "healthy_low": 45,
    "healthy_high": 65,
    "acceptable_low": 30,
    "acceptable_high": 70,
}

MACD_PARAMS = {
    "fast": 12,
    "slow": 26,
    "signal": 9,
}


# ============================================================
# 3. VOLATILITY INDICATORS
# ============================================================

ATR_PERIOD = 14  # documentation only today — signal_logic.atr() has a
                  # fixed internal period; exposed here so a future
                  # refactor of atr() itself has a config value ready
                  # to read instead of another hardcoded literal.

BOLLINGER_SETTINGS = {
    "period": 20,
    "num_std": 2.0,
}

EXPECTED_MOVE_BOUNDS = {
    # Defaults mirror signal_logic.py's live gate today (see module
    # docstring above) but are independently editable.
    "min_pct": _SIGNAL_LOGIC_MIN_EXPECTED_MOVE_PCT,
    "max_pct": _SIGNAL_LOGIC_MAX_EXPECTED_MOVE_PCT,
}


# ============================================================
# 4. VOLUME INDICATORS
# ============================================================

VOLUME_SETTINGS = {
    "lookback_candles": 20,     # relative_volume() averaging window
    "spike_threshold": 2.0,     # >= this x average = "spike"
    "high_threshold": 1.5,      # >= this x average = "high"
    "normal_threshold": 0.8,    # >= this x average = "normal"
    # below normal_threshold = "low"
}

# Future volume data sources — NOT implemented in this revision.
# Each key maps to whether the Volume Engine should attempt to score it;
# all False today because no data source is wired up yet. This dict
# exists so wiring one up later means: (1) write a scoring function,
# (2) register it, (3) flip the flag here — no engine redesign.
VOLUME_FUTURE_SOURCES = {
    "delivery_percentage": False,   # NSE delivery % data (not sourced yet)
    "open_interest": False,         # F&O OI (Upstox provides oi field per candle already — unused today)
    "volume_profile": False,        # price-level volume distribution
    "institutional_volume": False,  # bulk/block deal data
    "dark_pool": False,             # not applicable to NSE; placeholder for future asset classes
}


# ============================================================
# 5. PRICE ENGINE
# ============================================================

VWAP_DISTANCE_BANDS = {
    # distance_pct <= threshold -> score. Checked in ascending order.
    "tight": {"max_distance_pct": 0.3, "score": 100.0},
    "close": {"max_distance_pct": 0.8, "score": 80.0},
    "moderate": {"max_distance_pct": 1.5, "score": 55.0},
    # anything beyond the last band's max_distance_pct scores as "extended"
    "extended_score": 25.0,
}

# Future price-structure components — NOT implemented in this revision.
# Same pattern as VOLUME_FUTURE_SOURCES: flags exist so the Price Engine
# can grow by registration, not redesign.
PRICE_FUTURE_COMPONENTS = {
    "support_resistance": False,
    "pivot_points": False,
    "breakout_detection": False,
    "gap_analysis": False,
    "opening_range_breakout": False,
    "price_structure": False,       # higher-highs/higher-lows sequencing
    "distance_from_high": False,    # e.g. distance from 52w high
    "distance_from_low": False,
}


# ============================================================
# 6. COMPOSITE WEIGHTS
# ============================================================

# Engine-level weights for the composite Technical Score. Values are on
# a 0-100 scale (not fractions) and are expected to sum to 100 —
# validated by validate_config() below. The AI Strategy Optimizer
# (future) is expected to rewrite this dict per-instrument; nothing in
# technical_engine.py should assume these are static.
TECHNICAL_WEIGHTS = {
    "trend": 30,
    "momentum": 20,
    "volume": 15,
    "volatility": 15,
    "price": 20,
}

# Per-indicator weights WITHIN an engine. Empty by default = equal
# weighting across whichever indicators are enabled for that engine.
# Populate e.g. {"trend": {"ema_alignment": 40, "adx_strength": 20, ...}}
# to bias specific indicators — this is the hook the future Learning
# Engine uses to express "RSI matters more for this instrument than ADX
# does" without changing any code.
INDICATOR_WEIGHTS: dict = {}


# ============================================================
# 7. FEATURE TOGGLES (per-indicator enable/disable)
# ============================================================

# Every indicator function checks this before running. Disabling an
# indicator here removes it from that engine's aggregation (score no
# longer counted) without deleting any code — useful for the future
# optimizer to "turn off" indicators found to be noise for a given
# instrument, and useful today if e.g. VWAP is meaningless pre-market.
ENABLED_INDICATORS = {
    # Trend
    "ema_alignment": True,
    "ema200_confluence": True,
    "supertrend_agreement": True,
    "adx_strength": True,
    # Momentum
    "rsi": True,
    "macd": True,
    "adx_momentum_confirmation": True,
    # Volume
    "relative_volume": True,
    "volume_spike": True,
    # Volatility
    "expected_move": True,
    "bollinger_bands": True,
    # Price
    "vwap_distance": True,
}


# ============================================================
# 8. MULTI-TIMEFRAME SUPPORT (scaffolding only — not implemented)
# ============================================================

# The Trend Engine (and eventually others) is designed to accept a
# `timeframe` argument today (see IndicatorContext in technical_engine.py)
# even though every indicator function currently ignores it and scores
# off whatever candle list it's given. This list exists so that when
# multi-timeframe analysis IS implemented, there's already a canonical
# set of valid values to validate against instead of inventing one then.
SUPPORTED_TIMEFRAMES = ["5min", "15min", "1h", "daily"]
DEFAULT_TIMEFRAME = "5min"


# ============================================================
# 9. MULTI-ASSET-CLASS SUPPORT (scaffolding only — not implemented)
# ============================================================

# Same pattern as timeframe support above. IndicatorContext carries
# `asset_class` through every indicator function's signature today,
# unused, so that commodity/index/crypto-specific scoring adjustments
# (e.g. different volatility bounds for crypto vs. NSE equities) can be
# added later as branches inside existing functions rather than requiring
# new function signatures across the whole engine.
SUPPORTED_ASSET_CLASSES = ["equity", "commodity", "index", "crypto"]
DEFAULT_ASSET_CLASS = "equity"


# ============================================================
# VALIDATION
# ============================================================

def validate_config() -> None:
    """
    Sanity-checks this config at import time. Raises ValueError early
    (at process startup) rather than letting a bad weight silently
    produce a nonsensical technical_score deep inside a scan loop.
    """
    total_weight = sum(TECHNICAL_WEIGHTS.values())
    if abs(total_weight - 100) > 0.01:
        raise ValueError(
            f"TECHNICAL_WEIGHTS must sum to 100, got {total_weight}: {TECHNICAL_WEIGHTS}"
        )
    if DEFAULT_TIMEFRAME not in SUPPORTED_TIMEFRAMES:
        raise ValueError(f"DEFAULT_TIMEFRAME '{DEFAULT_TIMEFRAME}' not in SUPPORTED_TIMEFRAMES")
    if DEFAULT_ASSET_CLASS not in SUPPORTED_ASSET_CLASSES:
        raise ValueError(f"DEFAULT_ASSET_CLASS '{DEFAULT_ASSET_CLASS}' not in SUPPORTED_ASSET_CLASSES")


validate_config()
