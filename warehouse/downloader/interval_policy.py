"""
warehouse.downloader.interval_policy
========================================

Turns a requested (timeframe, start_date, end_date) into a concrete,
provider-legal request plan: which Upstox interval string to use, how far
back it's realistic to ask for, and how the overall range must be split
into individual API calls.

This is where the "Upstox won't actually give you 10 years of 30-minute
candles" reality gets handled explicitly rather than discovered at runtime
as a wall of errors — the plan clips the requested range to
`max_lookback_days` and logs a clear, structured warning explaining
exactly how much history had to be dropped and why, so a human reviewing
logs can decide whether to accept that gap or pursue a different data
source for the pre-cutoff period.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from warehouse.core.constants import Timeframe
from warehouse.core.logging_config import get_logger, log_with_context
from warehouse.downloader.downloader_config import IntervalPolicyConfig

logger = get_logger(__name__)


@dataclass(frozen=True)
class RequestChunk:
    """One (from_date, to_date) span that maps to exactly one Upstox API call."""

    upstox_interval: str
    from_date: date
    to_date: date


@dataclass(frozen=True)
class DownloadPlan:
    timeframe: Timeframe
    upstox_interval: str
    requested_start: date
    requested_end: date
    effective_start: date  # after clipping to max_lookback_days
    effective_end: date
    clipped_days: int      # how many days of the request were dropped due to provider limits
    chunks: tuple[RequestChunk, ...]


def build_plan(
    interval_policy: IntervalPolicyConfig,
    timeframe: Timeframe,
    start_date: date,
    end_date: date,
    *,
    today: date | None = None,
) -> DownloadPlan:
    """
    Build a DownloadPlan for one (timeframe, date range) request.

    Clips `start_date` forward if it falls outside what
    `IntervalPolicyEntry.max_lookback_days` says is realistically available,
    then splits [effective_start, effective_end] into chunks no larger than
    `chunk_days_per_request`.
    """
    if start_date > end_date:
        raise ValueError(f"start_date ({start_date}) must be <= end_date ({end_date})")

    entry = interval_policy.get(timeframe)
    today = today or date.today()
    earliest_available = today - timedelta(days=entry.max_lookback_days)

    effective_start = max(start_date, earliest_available)
    effective_end = min(end_date, today)
    clipped_days = max(0, (effective_start - start_date).days)

    if clipped_days > 0:
        log_with_context(
            logger, 30,  # WARNING
            "Requested start date clipped due to provider history limit",
            timeframe=timeframe.value, requested_start=str(start_date),
            effective_start=str(effective_start), clipped_days=clipped_days,
            max_lookback_days=entry.max_lookback_days,
        )

    chunks: list[RequestChunk] = []
    if effective_start <= effective_end:
        cursor = effective_start
        span = timedelta(days=entry.chunk_days_per_request - 1)
        while cursor <= effective_end:
            chunk_end = min(cursor + span, effective_end)
            chunks.append(RequestChunk(entry.upstox_interval, cursor, chunk_end))
            cursor = chunk_end + timedelta(days=1)

    return DownloadPlan(
        timeframe=timeframe,
        upstox_interval=entry.upstox_interval,
        requested_start=start_date,
        requested_end=end_date,
        effective_start=effective_start,
        effective_end=effective_end,
        clipped_days=clipped_days,
        chunks=tuple(chunks),
    )
