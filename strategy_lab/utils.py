"""
strategy_lab/utils.py
=====================

Shared utility functions for Strategy Lab.

This module contains reusable helpers used by:
    - backtest.py
    - optimizer.py
    - reports.py
    - metrics.py

Nothing in here should know anything about Streamlit or Upstox.

Author: NG Signal Pro v3
"""

from datetime import datetime
import math


# ==========================================================
# Candle Helpers
# ==========================================================

def sort_candles(candles):
    """
    Ensures candles are chronological (oldest → newest).

    Expected candle format:
    [timestamp, open, high, low, close, volume, oi]
    """
    return list(reversed(candles))


def closes(candles):
    return [c[4] for c in candles]


def highs(candles):
    return [c[2] for c in candles]


def lows(candles):
    return [c[3] for c in candles]


def volumes(candles):
    return [c[5] for c in candles]


# ==========================================================
# Timestamp Helpers
# ==========================================================

def parse_timestamp(ts):
    """
    Converts timestamp into datetime.
    Supports epoch milliseconds and ISO strings.
    """
    if isinstance(ts, (int, float)):
        return datetime.fromtimestamp(ts / 1000)

    return datetime.fromisoformat(str(ts))


def hours_between(start, end):
    return round(
        (end - start).total_seconds() / 3600,
        2
    )


# ==========================================================
# Percentage Helpers
# ==========================================================

def percentage_change(entry, exit_price):
    if entry == 0:
        return 0

    return round(
        ((exit_price - entry) / entry) * 100,
        2
    )


def risk_reward(entry, stop, target):
    risk = abs(entry - stop)

    if risk == 0:
        return 0

    reward = abs(target - entry)

    return round(
        reward / risk,
        2
    )


# ==========================================================
# Trade Helpers
# ==========================================================

def trade_result(signal, entry, exit_price):
    """
    Returns WIN / LOSS depending on trade direction.
    """

    if signal == "BUY":
        return "WIN" if exit_price > entry else "LOSS"

    if signal == "SELL":
        return "WIN" if exit_price < entry else "LOSS"

    return "NONE"


def pnl(signal, entry, exit_price):
    pct = percentage_change(entry, exit_price)

    if signal == "SELL":
        pct *= -1

    return round(pct, 2)


# ==========================================================
# Drawdown Helpers
# ==========================================================

def calculate_drawdown(equity_curve):
    """
    Returns maximum drawdown percentage.
    """

    if not equity_curve:
        return 0

    peak = equity_curve[0]
    max_dd = 0

    for value in equity_curve:

        if value > peak:
            peak = value

        dd = ((peak - value) / peak) * 100

        if dd > max_dd:
            max_dd = dd

    return round(max_dd, 2)


# ==========================================================
# Statistics
# ==========================================================

def average(values):

    if not values:
        return 0

    return round(sum(values) / len(values), 2)


def median(values):

    if not values:
        return 0

    values = sorted(values)

    n = len(values)

    mid = n // 2

    if n % 2 == 0:
        return round(
            (values[mid - 1] + values[mid]) / 2,
            2
        )

    return values[mid]


def standard_deviation(values):

    if len(values) < 2:
        return 0

    avg = average(values)

    variance = sum(
        (x - avg) ** 2
        for x in values
    ) / (len(values) - 1)

    return round(
        math.sqrt(variance),
        2
    )


# ==========================================================
# Win/Loss Helpers
# ==========================================================

def wins(trades):

    return len([
        t for t in trades
        if t.result == "WIN"
    ])


def losses(trades):

    return len([
        t for t in trades
        if t.result == "LOSS"
    ])


def open_trades(trades):

    return len([
        t for t in trades
        if t.status == "OPEN"
    ])


# ==========================================================
# Misc
# ==========================================================

def chunk(data, size):

    for i in range(0, len(data), size):
        yield data[i:i + size]


def safe_divide(a, b):

    if b == 0:
        return 0

    return a / b
