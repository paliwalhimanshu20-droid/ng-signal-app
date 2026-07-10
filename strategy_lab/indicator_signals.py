"""
strategy_lab/indicator_signals.py — PR 8 continuation, Requirement 2
(Indicator Reliability — true per-indicator evaluation, replacing the
combination-delta approximation from the first PR 8 pass).

Problem this solves: signal_engine() (in signal_logic.py) is a FUSED
multi-input function — it was never designed to be run with "only RSI"
or "only MACD" in isolation, so there's no existing way to ask "how
often does RSI alone produce a winning trade for this instrument."

This module defines one isolated signal rule per indicator, each
documented below with its exact logic (no hidden thresholds). Every
rule is a genuinely simple, standalone read of that one indicator —
deliberately NOT signal_engine()'s fused logic, since fusing multiple
indicators is exactly what would make this NOT an isolated test.

INDICATORS THAT ARE DIRECTIONAL (produce a BUY/SELL signal on their own):
  - ema_crossover   : ema20 vs ema50 (signal_logic.ema)
  - supertrend      : latest_trend (signal_logic.calculate_supertrend)
  - rsi_reversion   : signal_logic.rsi(), oversold/overbought thresholds
  - macd            : technical_engine.macd(), line vs signal line

INDICATORS THAT ARE NOT DIRECTIONAL (excluded from isolated BUY/SELL
testing, with the reason documented in NON_DIRECTIONAL_INDICATORS below
— this is a deliberate exclusion, not an oversight):
  - atr  : a volatility/magnitude measure (used for SL/T1/T2 sizing in
    signal_logic.levels()), has no inherent "up" or "down" reading.
  - adx  : a trend-STRENGTH measure (0-100, direction-agnostic by
    definition — see signal_logic.compute_adx()'s own docstring), used
    as a filter within combinations, not a standalone signal source.

Each directional rule below returns one of "BUY" / "SELL" / "NONE" at a
single decision point, given the same 50-bar window
research_engine.py's combination testing already uses (so results are
directly comparable in scale/timeframe).
"""

import pandas as pd

from signal_logic import ema, rsi, calculate_supertrend, detect_regime, levels
from technical_engine import macd as _macd
from strategy_lab.trade_simulator import simulate_trade_outcomes

# RSI thresholds: standard, widely-used oversold/overbought levels (not
# tuned to this dataset — a fixed, documented convention, same spirit as
# signal_logic.py using published Wilder ADX conventions elsewhere).
RSI_OVERSOLD = 30
RSI_OVERBOUGHT = 70

NON_DIRECTIONAL_INDICATORS = {
    "atr": "Volatility/magnitude measure, used for SL/T1/T2 sizing — no inherent direction.",
    "adx": "Trend-STRENGTH measure (0-100), direction-agnostic by definition — used as a filter, not a signal source.",
}

_INDICATOR_WINDOW = 50


def _ema_crossover_signal(closes, price):
    e20, e50 = ema(closes, 20), ema(closes, 50)
    return ("BUY" if e20 > e50 else "SELL"), {"ema20": e20, "ema50": e50}


def _supertrend_signal(window):
    st_result = calculate_supertrend(window)
    if not st_result or not st_result.get("latest_trend"):
        return "NONE", {}
    trend = st_result["latest_trend"]
    return ("BUY" if trend == "Bullish" else "SELL"), {"supertrend_value": st_result.get("latest_value")}


def _rsi_reversion_signal(closes):
    r = rsi(closes, 14)
    if r is None:
        return "NONE", {}
    if r < RSI_OVERSOLD:
        return "BUY", {"rsi": r}
    if r > RSI_OVERBOUGHT:
        return "SELL", {"rsi": r}
    return "NONE", {"rsi": r}


def _macd_signal(closes):
    macd_line, signal_line, histogram = _macd(closes)
    if macd_line is None:
        return "NONE", {}
    return ("BUY" if macd_line > signal_line else "SELL"), {"macd": macd_line, "macd_signal": signal_line}


DIRECTIONAL_INDICATORS = {
    "EMA20/EMA50 Crossover": lambda window, closes, price: _ema_crossover_signal(closes, price),
    "Supertrend": lambda window, closes, price: _supertrend_signal(window),
    "RSI (Mean Reversion)": lambda window, closes, price: _rsi_reversion_signal(closes),
    "MACD": lambda window, closes, price: _macd_signal(closes),
}


def walk_forward_indicator_signals(candles, indicator_fn):
    """
    Same walk-forward structure as research_engine._rescore_with_combination(),
    but the Signal at each bar comes from ONE isolated indicator rule
    instead of signal_engine()'s fused logic. Trend/Regime are still
    computed (via signal_logic.detect_regime(), off the shared ema20/
    ema50/price context every bar already needs) so
    strategy_lab.trade_simulator.simulate_trade_outcomes() — which needs
    Trend+Regime to size SL/T1/T2 via signal_logic.levels() — works
    unmodified against this output too.
    """
    results = []
    for i in range(len(candles) - 1, _INDICATOR_WINDOW, -1):
        window = candles[i: i - _INDICATOR_WINDOW: -1]
        if len(window) < _INDICATOR_WINDOW:
            continue

        closes = [c[4] for c in window]
        price = closes[-1]
        ema20, ema50 = ema(closes, 20), ema(closes, 50)
        regime = detect_regime(ema20, ema50, price)

        signal, extra = indicator_fn(window, closes, price)
        if signal == "NONE":
            continue

        trend = "Bullish" if signal == "BUY" else "Bearish"
        results.append({
            "Index": i, "Price": price, "Signal": signal,
            "Trend": trend, "Regime": regime,
        })

    return pd.DataFrame(results)


def evaluate_indicator_reliability(candles):
    """
    Runs every DIRECTIONAL_INDICATORS rule over the given candle series,
    simulates realized outcomes for each via the same trade_simulator
    used elsewhere, and returns a dict keyed by indicator name:

        { "win_rate": float, "sample_size": int, "average_return": float,
          "average_drawdown": float }

    Also returns entries for NON_DIRECTIONAL_INDICATORS with a `reason`
    key instead of metrics, so the frontend can display "N/A —
    <reason>" rather than a silently-missing row.
    """
    from strategy_lab.research_engine import _compute_max_drawdown_pct  # shared calc, not duplicated

    reliability = {}

    for name, fn in DIRECTIONAL_INDICATORS.items():
        signals_df = walk_forward_indicator_signals(candles, fn)
        if signals_df.empty:
            reliability[name] = {
                "win_rate": 0.0, "sample_size": 0,
                "average_return": 0.0, "average_drawdown": 0.0,
            }
            continue

        trades_df = simulate_trade_outcomes(candles, signals_df)
        if trades_df.empty:
            reliability[name] = {
                "win_rate": 0.0, "sample_size": 0,
                "average_return": 0.0, "average_drawdown": 0.0,
            }
            continue

        wins = trades_df[trades_df["pnl"] > 0]
        reliability[name] = {
            "win_rate": round(len(wins) / len(trades_df) * 100, 2),
            "sample_size": len(trades_df),
            "average_return": round(float(trades_df["pnl"].mean()), 4),
            "average_drawdown": _compute_max_drawdown_pct(trades_df),
        }

    for name, reason in NON_DIRECTIONAL_INDICATORS.items():
        reliability[name] = {"applicable": False, "reason": reason}

    return reliability
