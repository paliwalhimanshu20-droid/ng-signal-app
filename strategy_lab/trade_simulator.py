"""
strategy_lab/trade_simulator.py — PR 8, Part 5 support module.

WHY THIS FILE EXISTS (read this before wiring it into anything else):

strategy_lab/backtest.py's run_backtest() produces one row per historical
bar with a Signal/Score/Trend/Regime label — it tallies how often each
signal type fired, but it never checks whether following that signal
would actually have made money. strategy_lab/metrics.py's
compute_win_rate() / compute_expectancy() / compute_profit_factor() all
read a "pnl" column that run_backtest()'s output does not contain —
called directly on run_backtest()'s output, compute_win_rate() returns
0.0 for every instrument, always, regardless of real performance,
because `df.get("pnl", 0) > 0` evaluates against a scalar 0 broadcast
to False for every row.

This module closes that gap: for every actionable (BUY/SELL) signal row,
walk forward through the same candle series and determine whether SL,
T1, or T2 was hit first (or neither, within a max holding window),
using the exact same signal_logic.levels() function scanner.py and
strategy_lab/strategies.py already use for live SL/T1/T2 — no new
position-sizing logic is introduced, this only asks "what would have
happened" using rules that already exist and are already in production.

Does not modify run_backtest(), signal_logic.py, or any other existing
file. Pure addition.
"""

from signal_logic import atr, levels
import pandas as pd

# How many bars forward to check for SL/T1/T2 before giving up and
# marking the trade a timeout (exit at last available close). 20 bars
# on a 30-min candle series is ~10 trading hours — a deliberately modest
# holding window matching this system's intraday signal character (see
# ExpectedMove%/regime-based target multipliers in signal_logic.levels()).
DEFAULT_MAX_HOLDING_BARS = 20

# Same window size run_backtest() uses to compute indicators at each
# decision point — kept identical so re-deriving atr_val here produces
# the same SL/T1/T2 the original signal would have shown.
_INDICATOR_WINDOW = 50


def simulate_trade_outcomes(candles, signals_df, max_holding_bars=DEFAULT_MAX_HOLDING_BARS):
    """
    candles: the same descending-order (index 0 = most recent) candle
    list passed into strategy_lab.backtest.run_backtest().

    signals_df: the DataFrame returned in run_backtest()'s
    result["data"] — must have Index/Price/Signal/Trend/Regime columns.

    Returns a NEW DataFrame — one row per BUY/SELL signal in signals_df
    that had enough forward data to simulate — with columns:
        Index, Signal, EntryPrice, SL, T1, T2, ExitPrice, ExitReason
        (one of "SL", "T1", "T2", "TIMEOUT"), BarsHeld, pnl_pct.

    Rows for WATCH/NO TRADE signals, or where indicators/levels couldn't
    be computed, are skipped — they have no position to simulate.
    """
    rows = []

    for _, row in signals_df.iterrows():
        signal = row["Signal"]
        if signal not in ("BUY", "SELL"):
            continue

        i = int(row["Index"])
        window = candles[i: i - _INDICATOR_WINDOW: -1]
        if len(window) < _INDICATOR_WINDOW:
            continue

        atr_val = atr(window)
        entry_price = row["Price"]
        trend = row["Trend"]
        regime = row["Regime"]

        sl, t1, t2 = levels(entry_price, atr_val, signal, trend, regime)
        if sl is None:
            continue

        exit_price = entry_price
        exit_reason = "TIMEOUT"
        bars_held = 0

        forward_end = max(i - 1 - max_holding_bars, -1)
        for j in range(i - 1, forward_end, -1):
            if j < 0:
                break
            candle = candles[j]
            high, low = candle[2], candle[3]
            bars_held += 1

            if signal == "BUY":
                if low <= sl:
                    exit_price, exit_reason = sl, "SL"
                    break
                if high >= t2:
                    exit_price, exit_reason = t2, "T2"
                    break
                if high >= t1:
                    exit_price, exit_reason = t1, "T1"
                    break
            else:  # SELL
                if high >= sl:
                    exit_price, exit_reason = sl, "SL"
                    break
                if low <= t2:
                    exit_price, exit_reason = t2, "T2"
                    break
                if low <= t1:
                    exit_price, exit_reason = t1, "T1"
                    break
        else:
            # Loop exhausted without hitting SL/T1/T2 — timeout exit at
            # the last bar we checked (or entry price if no forward data
            # existed at all, e.g. signal fired on the most recent bar).
            last_j = max(forward_end + 1, 0)
            if last_j < i:
                exit_price = candles[last_j][4]
            bars_held = max(bars_held, 0)

        if signal == "BUY":
            pnl_pct = (exit_price - entry_price) / entry_price * 100
        else:
            pnl_pct = (entry_price - exit_price) / entry_price * 100

        rows.append({
            "Index": i,
            "Signal": signal,
            "EntryPrice": entry_price,
            "SL": sl,
            "T1": t1,
            "T2": t2,
            "ExitPrice": exit_price,
            "ExitReason": exit_reason,
            "BarsHeld": bars_held,
            "pnl": round(pnl_pct, 4),
        })

    return pd.DataFrame(rows)
