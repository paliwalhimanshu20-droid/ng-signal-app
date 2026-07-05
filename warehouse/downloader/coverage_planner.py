"""
warehouse.downloader.coverage_planner
=========================================

Answers "what do I actually need to download?" by comparing a requested
(instrument, timeframe, date range) against what the warehouse catalog
already reports as covered. This is what makes incremental daily updates
cheap (only fetch the gap since the last catalog entry) and makes backfill
resumable at the partition level even without a mid-partition checkpoint
(a fully-written partition is simply skipped on the next run).

This module only reads the catalog (`WarehouseMetadataManager`) — it never
reads Parquet files directly, which keeps it fast even at 100-instrument
scale (a metadata query, not a filesystem scan).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from warehouse.core.constants import Timeframe, WarehouseLayer
from warehouse.metadata.metadata_manager import WarehouseMetadataManager


@dataclass(frozen=True)
class CoverageGap:
    start: date
    end: date


def find_missing_ranges(
    metadata_manager: WarehouseMetadataManager,
    layer: WarehouseLayer,
    instrument_id: str,
    timeframe: Timeframe,
    requested_start: date,
    requested_end: date,
    *,
    force_refresh: bool = False,
) -> list[CoverageGap]:
    """
    Return the sub-range(s) of [requested_start, requested_end] not already
    covered by the catalog, based on the instrument/timeframe's recorded
    min/max timestamp coverage.

    This is intentionally coarse (min/max span, not partition-by-partition
    gap detection) — it is correct for the downloader's actual access
    pattern (sequential backfill, then daily incremental appends), and
    avoids the cost of enumerating every partition for a coverage check
    that runs before every download. A gap *inside* an already-covered
    span (e.g. a manually deleted partition file) is a data-integrity
    concern the health checker's `catalog_consistency` check is
    responsible for surfacing, not this planner.

    Args:
        force_refresh: if True, ignore existing coverage entirely and
            return the full requested range — used for explicit
            re-download/repair runs.
    """
    if force_refresh:
        return [CoverageGap(requested_start, requested_end)]

    summary = metadata_manager.coverage_summary(instrument_id, timeframe.value)
    if summary["total_rows"] == 0 or summary["earliest_timestamp_utc"] is None:
        return [CoverageGap(requested_start, requested_end)]

    covered_start = summary["earliest_timestamp_utc"].date()
    covered_end = summary["latest_timestamp_utc"].date()

    gaps: list[CoverageGap] = []

    # Gap before the covered range (e.g. extending a backfill further back).
    if requested_start < covered_start:
        gap_end = min(requested_end, covered_start - timedelta(days=1))
        if requested_start <= gap_end:
            gaps.append(CoverageGap(requested_start, gap_end))

    # Gap after the covered range (the common case: daily incremental update).
    if requested_end > covered_end:
        gap_start = max(requested_start, covered_end + timedelta(days=1))
        if gap_start <= requested_end:
            gaps.append(CoverageGap(gap_start, requested_end))

    return gaps
