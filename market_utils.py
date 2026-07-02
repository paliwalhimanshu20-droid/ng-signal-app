"""
NGSP-002 — Market Intelligence Engine: Utilities
=================================================

Small, dependency-free helpers shared across the market intelligence
modules. Keep this file free of business rules — anything with a
threshold belongs in ``market_config.py``.
"""

from __future__ import annotations

from datetime import date
from typing import Optional


def clamp(value: float, low: float, high: float) -> float:
    """Clamp ``value`` into the inclusive range [low, high].

    Raises
    ------
    ValueError
        If ``low`` > ``high``.
    """
    if low > high:
        raise ValueError(f"clamp bounds inverted: low={low} high={high}")
    return max(low, min(high, value))


def scale_linear(
    value: float,
    in_low: float,
    in_high: float,
    out_low: float = 0.0,
    out_high: float = 100.0,
) -> float:
    """Linearly map ``value`` from [in_low, in_high] to [out_low, out_high].

    Values outside the input range are clamped to the output bounds.
    A higher input maps to a higher output when ``out_high`` > ``out_low``,
    and to a lower output when the output range is inverted — which is
    how "lower VIX is better" style inversions are expressed.
    """
    if in_high == in_low:
        raise ValueError("scale_linear requires in_low != in_high")
    fraction = (value - in_low) / (in_high - in_low)
    fraction = clamp(fraction, 0.0, 1.0)
    return out_low + fraction * (out_high - out_low)


def days_until(target: date, today: Optional[date] = None) -> int:
    """Whole days from ``today`` until ``target`` (negative if past)."""
    reference = today or date.today()
    return (target - reference).days


def is_stale(
    last_updated: Optional[date],
    max_age_days: int,
    today: Optional[date] = None,
) -> bool:
    """True when data is missing or older than ``max_age_days``."""
    if last_updated is None:
        return True
    reference = today or date.today()
    return (reference - last_updated).days > max_age_days
