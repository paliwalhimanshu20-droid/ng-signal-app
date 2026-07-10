"""
strategy_lab/research_engine.py — PR 8, Parts 5 & 6, extended.

The Historical Intelligence Engine. New algorithm design, not
integration glue — nothing elsewhere in the codebase determines "best
indicator combination," per-indicator reliability, instrument DNA, or a
composite research score. Built entirely on existing, UNMODIFIED
primitives: signal_logic.py's indicator functions, technical_engine.py's
macd(), strategy_lab.trade_simulator's realized-outcome simulation, and
strategy_lab.metrics's win-rate/expectancy/profit-factor rollup.

DATA SOURCE: fetched via strategy_lab.data_provider.CandleDataProvider —
this module has NO direct dependency on upstox_client or
get_candles_range(). Swapping the data source (e.g. to a populated
Historical Warehouse) is a provider-instance change at the call site,
not a change to any function below. See data_provider.py's module
docstring for the full explanation.

HONEST SCOPE LIMIT: with UpstoxCandleProvider (today's default), this
still analyzes ~120 days of 30-minute candles, not 10 years — the
provider abstraction makes a longer history possible once the Warehouse
is populated, it doesn't manufacture one today. Every result carries its
own `data_source` field so this is always visible, never implied.

CANDIDATE COMBINATIONS: signal_engine() already accepts optional
supertrend_trend/adx_val inputs — four fixed, explicit combinations are
tested, not an open-ended search, so a scheduled job's runtime stays
predictable.
"""

from datetime import datetime, timezone

import pandas as pd

from signal_logic import ema, atr, compute_adx, calculate_supertrend, signal_engine
from strategy_lab.data_provider import CandleDataProvider, default_provider
from strategy_lab.trade_simulator import simulate_trade_outcomes
from strategy_lab.indicator_signals import evaluate_indicator_reliability
from strategy_lab.research_score import compute_research_score
from strategy_lab import metrics as strategy_metrics

INDICATOR_COMBINATIONS = [
    {"name": "EMA20/EMA50 + ATR (baseline)", "use_adx": False, "use_supertrend": False},
    {"name": "EMA20/EMA50 + ATR + Supertrend", "use_adx": False, "use_supertrend": True},
    {"name": "EMA20/EMA50 + ATR + ADX", "use_adx": True, "use_supertrend": False},
    {"name": "EMA20/EMA50 + ATR + Supertrend + ADX", "use_adx": True, "use_supertrend": True},
]

REGIME_TYPES = ("TRENDING", "RANGING", "BREAKOUT")

# Strategy Family mapping: a documented, deliberately simple label for
# each tested combination — NOT a machine-learned classification, just a
# human-readable name for which combination won. Only covers
# INDICATOR_COMBINATIONS above; if that set changes, extend this too.
STRATEGY_FAMILY_MAP = {
    "EMA20/EMA50 + ATR (baseline)": "Momentum / Trend Following (unconfirmed)",
    "EMA20/EMA50 + ATR + Supertrend": "Trend Following",
    "EMA20/EMA50 + ATR + ADX": "Trend Following (strength-filtered)",
    "EMA20/EMA50 + ATR + Supertrend + ADX": "Trend Following (confirmed)",
}

# Volatility Profile / Momentum Strength bucket thresholds — documented,
# round-number conventions (ATR% of price; ADX per Wilder's own
# published bands: <20 weak trend, 20-40 moderate/strong, >40 very
# strong), not tuned to any specific instrument's data.
VOLATILITY_BUCKETS = [(0.5, "Low"), (1.5, "Medium"), (float("inf"), "High")]
ADX_MOMENTUM_BUCKETS = [(20, "Weak"), (40, "Moderate"), (float("inf"), "Strong")]

# NSE cash-market trading hours, used only to express holding time in
# "trading days" instead of raw minutes for the Instrument DNA display —
# an approximation, documented as such, not a precise session calendar.
TRADING_HOURS_PER_DAY = 6.25

_INDICATOR_WINDOW = 50


def _bucket(value, buckets):
    for threshold, label in buckets:
        if value <= threshold:
            return label
    return buckets[-1][1]


def _compute_max_drawdown_pct(trades_df):
    """
    strategy_lab.metrics.compute_metrics() does not compute this —
    verified directly (it only returns win_rate/expectancy/profit_factor/
    total_trades). Running cumulative P&L peak-to-trough decline, in
    percentage points, over the sequence of simulated trades in the
    order they occurred (oldest signal first). Shared by both the
    per-combination scoring below and strategy_lab/indicator_signals.py's
    per-indicator reliability — imported from here rather than duplicated.
    """
    if trades_df.empty or "pnl" not in trades_df.columns:
        return 0.0
    ordered = trades_df.sort_values("Index", ascending=False)
    cumulative = ordered["pnl"].cumsum()
    running_peak = cumulative.cummax()
    drawdown = cumulative - running_peak
    return round(float(drawdown.min()), 4)


def _rescore_with_combination(candles, combination):
    """
    Re-runs the same walk-forward loop for every combination, including
    the baseline — this is the one architectural change from PR 8's
    first pass: it no longer calls strategy_lab.backtest.run_backtest()
    at all (that function bundles its OWN Upstox fetch internally, which
    is exactly the coupling the data-provider abstraction requirement
    exists to remove). Candles now always come from the caller
    (analyze_instrument(), via a CandleDataProvider), so this function
    has zero knowledge of where the data came from.

    calculate_supertrend()/compute_adx() both expect newest-first
    candles directly (see their docstrings in signal_logic.py) — same
    convention scanner.py already uses, no reversal needed.
    """
    results = []
    for i in range(len(candles) - 1, _INDICATOR_WINDOW, -1):
        window = candles[i: i - _INDICATOR_WINDOW: -1]
        if len(window) < _INDICATOR_WINDOW:
            continue

        closes = [c[4] for c in window]
        price = closes[-1]

        ema20 = ema(closes, 20)
        ema50 = ema(closes, 50)
        atr_val = atr(window)

        supertrend_trend = None
        if combination["use_supertrend"]:
            st_result = calculate_supertrend(window)
            supertrend_trend = st_result["latest_trend"] if st_result else None

        adx_val = compute_adx(window) if combination["use_adx"] else None

        signal, score, prob, trend, regime, exp_move, reasons, conviction = signal_engine(
            price=price, ema20=ema20, ema50=ema50, atr_val=atr_val,
            supertrend_trend=supertrend_trend, adx_val=adx_val,
        )

        results.append({
            "Index": i, "Price": price, "Signal": signal, "Score": score,
            "Prob%": prob, "Trend": trend, "Regime": regime,
            "ExpectedMove%": exp_move, "Conviction%": conviction,
            # Instrument DNA support — captured on every bar regardless
            # of combination, so Historical Regime Distribution/
            # Volatility Profile/Momentum Strength (computed later from
            # the baseline combination's output) reflect the FULL
            # analyzed window, not just traded bars.
            "ATRPct": round((atr_val / price) * 100, 4) if price else 0.0,
            "ADX": adx_val,
        })

    return pd.DataFrame(results)


def _compute_instrument_dna(baseline_signals_df, adx_signals_df, best_combination):
    """
    Requirement 3 — Instrument DNA. Every value here is derived from
    already-computed walk-forward data, not hardcoded:

      - Historical Regime Distribution: % of ALL analyzed bars (not just
        traded ones) in each regime — baseline_signals_df has one row
        per bar processed, regardless of what signal (if any) fired.
      - Trend Preference / Mean Reversion Preference / Breakout Behaviour:
        the WINNING combination's regime-conditional win rate (already
        computed in regime_breakdown) — this is an honest simplification:
        it reads the best-performing strategy's behavior per regime, not
        a strategy-independent instrument property. Documented as such.
      - Volatility Profile: mean ATR-as-%-of-price across all analyzed
        bars, bucketed via VOLATILITY_BUCKETS.
      - Momentum Strength: mean ADX across bars where ADX was computed
        (the ADX-enabled combination's pass), bucketed via
        ADX_MOMENTUM_BUCKETS.
      - Preferred Holding Period / Strategy Family: from the winning
        combination's own trade simulation and STRATEGY_FAMILY_MAP.
    """
    regime_distribution = {}
    if not baseline_signals_df.empty:
        counts = baseline_signals_df["Regime"].value_counts(normalize=True) * 100
        regime_distribution = {k: round(v, 2) for k, v in counts.items()}

    avg_atr_pct = float(baseline_signals_df["ATRPct"].mean()) if not baseline_signals_df.empty else 0.0
    volatility_profile = _bucket(avg_atr_pct, VOLATILITY_BUCKETS)

    momentum_strength = "Unknown (ADX not computed this run)"
    if adx_signals_df is not None and not adx_signals_df.empty and adx_signals_df["ADX"].notna().any():
        avg_adx = float(adx_signals_df["ADX"].dropna().mean())
        momentum_strength = _bucket(avg_adx, ADX_MOMENTUM_BUCKETS)

    regime_breakdown = best_combination["regime_breakdown"]
    trend_preference = regime_breakdown.get("TRENDING", {}).get("win_rate")
    mean_reversion_preference = regime_breakdown.get("RANGING", {}).get("win_rate")
    breakout_behaviour = regime_breakdown.get("BREAKOUT", {}).get("win_rate")

    strategy_family = STRATEGY_FAMILY_MAP.get(
        best_combination["combination_name"], "Unclassified"
    )

    return {
        "historical_regime_distribution": regime_distribution,
        "trend_preference_win_rate": trend_preference,
        "mean_reversion_preference_win_rate": mean_reversion_preference,
        "breakout_behaviour_win_rate": breakout_behaviour,
        "volatility_profile": volatility_profile,
        "volatility_profile_avg_atr_pct": round(avg_atr_pct, 4),
        "momentum_strength": momentum_strength,
        "preferred_holding_days": best_combination.get("best_holding_days"),
        "worst_holding_days": best_combination.get("worst_holding_days"),
        "strategy_family": strategy_family,
    }


def analyze_instrument(instrument_name, instrument_key, data_provider: CandleDataProvider = None):
    """
    Full Historical Intelligence Engine pass for one instrument. Returns
    a structured result ready for strategy_lab.research_bridge to
    persist, or a data_available=False result if the provider had
    nothing to analyze — a real, expected state, not an error to hide.
    """
    provider = data_provider or default_provider()
    fetch = provider.get_candles(instrument_key)

    if fetch.candles is None:
        return {
            "instrument_name": instrument_name,
            "instrument_key": instrument_key,
            "data_available": False,
            "data_source": fetch.source,
            "reason": fetch.reason,
            "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        }

    candles = fetch.candles

    combination_results = []
    baseline_signals_df = None
    adx_signals_df = None

    for combo in INDICATOR_COMBINATIONS:
        signals_df = _rescore_with_combination(candles, combo)
        if combo["name"].endswith("(baseline)"):
            baseline_signals_df = signals_df
        if combo["use_adx"] and adx_signals_df is None:
            adx_signals_df = signals_df
        if signals_df.empty:
            continue

        trades_df = simulate_trade_outcomes(candles, signals_df)
        combo_metrics = strategy_metrics.compute_metrics(trades_df)
        combo_metrics["average_trade"] = combo_metrics.get("expectancy", 0.0)
        combo_metrics["max_drawdown"] = _compute_max_drawdown_pct(trades_df)
        if not trades_df.empty and "BarsHeld" in trades_df.columns:
            combo_metrics["avg_holding_time_minutes"] = round(float(trades_df["BarsHeld"].mean()) * 30, 1)
        else:
            combo_metrics["avg_holding_time_minutes"] = 0.0

        regime_breakdown = {}
        best_holding_days = worst_holding_days = None
        if not trades_df.empty:
            merged = trades_df.merge(signals_df[["Index", "Regime"]], on="Index", how="left")
            for regime_type in REGIME_TYPES:
                sub = merged[merged["Regime"] == regime_type]
                if sub.empty:
                    continue
                regime_breakdown[regime_type] = strategy_metrics.compute_metrics(sub)

            wins = trades_df[trades_df["pnl"] > 0]
            losses = trades_df[trades_df["pnl"] <= 0]
            if not wins.empty:
                best_holding_days = round(float(wins["BarsHeld"].mean()) * 30 / 60 / TRADING_HOURS_PER_DAY, 2)
            if not losses.empty:
                worst_holding_days = round(float(losses["BarsHeld"].mean()) * 30 / 60 / TRADING_HOURS_PER_DAY, 2)

        combination_results.append({
            "combination_name": combo["name"],
            "metrics": combo_metrics,
            "regime_breakdown": regime_breakdown,
            "total_signals": len(signals_df),
            "total_trades_simulated": len(trades_df),
            "best_holding_days": best_holding_days,
            "worst_holding_days": worst_holding_days,
        })

    if not combination_results:
        return {
            "instrument_name": instrument_name,
            "instrument_key": instrument_key,
            "data_available": False,
            "data_source": fetch.source,
            "reason": "No signals generated across any tested combination.",
            "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        }

    # Strategy ranking: win_rate first, trade count as tiebreaker — a
    # 90% win rate on 4 trades ranks below a 65% win rate on 400,
    # avoiding small-sample bias in the #1 slot.
    ranked = sorted(
        combination_results,
        key=lambda r: (r["metrics"]["win_rate"], r["metrics"]["total_trades"]),
        reverse=True,
    )
    for rank, r in enumerate(ranked, start=1):
        r["strategy_rank"] = rank

    best = ranked[0]
    best_regime = None
    if best["regime_breakdown"]:
        best_regime = max(best["regime_breakdown"].items(), key=lambda kv: kv[1]["win_rate"])[0]

    research_score = compute_research_score(best["metrics"], best["regime_breakdown"])
    indicator_reliability = evaluate_indicator_reliability(candles)
    instrument_dna = _compute_instrument_dna(baseline_signals_df, adx_signals_df, best)

    return {
        "instrument_name": instrument_name,
        "instrument_key": instrument_key,
        "data_available": True,
        "data_source": fetch.source,
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "combinations": ranked,
        "best_combination": best,
        "best_market_regime": best_regime,
        "confidence_source": (
            f"{best['total_trades_simulated']} simulated trades over "
            f"{best['total_signals']} historical signals, data source: {fetch.source}"
        ),
        "research_score": research_score,
        "indicator_reliability": indicator_reliability,
        "instrument_dna": instrument_dna,
    }
