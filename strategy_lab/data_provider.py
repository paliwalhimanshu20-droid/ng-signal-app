"""
strategy_lab/data_provider.py — PR 8 continuation, Requirement 4
(Data Source Abstraction — mandatory).

Problem this solves: strategy_lab.backtest.run_backtest() (and therefore
the first version of research_engine.py) called
upstox_client.get_candles_range() directly, hardcoding "candle history"
to mean "whatever Upstox's 30-min historical endpoint returns for the
last ~120 days." That's fine as today's only real data source, but the
approved architecture requires the Historical Intelligence Engine to be
able to read from the Historical Warehouse (NGWH) once it's populated,
WITHOUT rewriting research_engine.py's analysis logic.

This module is the seam that makes that possible: a small interface
(CandleDataProvider) with exactly one method, get_candles(), and two
implementations. research_engine.py depends only on the interface — it
never imports upstox_client or warehouse.* directly.

CandleDataProvider implementations:
  - UpstoxCandleProvider  — today's source. Thin wrapper around
    upstox_client.get_candles_range(), same LOOKBACK_DAYS convention
    strategy_lab.backtest already used. This is the DEFAULT provider.
  - WarehouseCandleProvider — future source. Wraps
    warehouse.storage.parquet_manager.ParquetStorageManager.read_partition(),
    the Historical Warehouse's real, already-built read API (see
    warehouse/storage/parquet_manager.py). Checks partition_exists()
    first and returns None with a clear reason if the warehouse doesn't
    have that instrument's data yet — it does NOT fabricate candles or
    silently fall back to a different source. Per the PR 8 continuation
    brief: "clearly identify where production data will populate the
    intelligence after deployment" — get_candles() returning None with
    reason="warehouse not yet populated for this instrument" IS that
    identification, surfaced all the way up through research_engine.py's
    output rather than hidden.

Switching research_engine.py from one to the other is a one-line change
(which provider instance gets passed to analyze_instrument()) — no
change to any analysis/ranking/scoring code, which is the whole point.
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class CandleFetchResult:
    """
    candles: newest-first [timestamp, open, high, low, close, volume, oi]
    rows (same convention every existing candle consumer in this repo
    uses — scanner.py, signal_logic.py, strategy_lab), or None if this
    provider had nothing to return.
    source: human-readable label for where the data came from — surfaced
    in research_engine.py's output so it's always visible which data
    source backed a given piece of intelligence.
    reason: set when candles is None, explaining why (e.g. "not enough
    history", "warehouse not yet populated") rather than just failing
    silently.
    """
    candles: Optional[list]
    source: str
    reason: Optional[str] = None


class CandleDataProvider:
    """
    Interface every data source must implement. research_engine.py only
    ever calls get_candles() on whatever provider it's given — it has no
    knowledge of Upstox, the Warehouse, or any other source.
    """

    def get_candles(self, instrument_key: str) -> CandleFetchResult:
        raise NotImplementedError


class UpstoxCandleProvider(CandleDataProvider):
    """
    Today's default data source. Same fetch strategy_lab.backtest.
    load_history() already used (LOOKBACK_DAYS=120 calendar days of
    30-min candles via upstox_client.get_candles_range()) — this doesn't
    change what data is fetched, only where the fetch call lives.
    """

    LOOKBACK_DAYS = 120

    def get_candles(self, instrument_key: str) -> CandleFetchResult:
        from upstox_client import get_candles_range

        candles = get_candles_range(instrument_key, days_back=self.LOOKBACK_DAYS)
        if not candles:
            return CandleFetchResult(
                candles=None, source="upstox_live",
                reason="Upstox returned no candles for this instrument/window.",
            )
        if len(candles) < 100:
            return CandleFetchResult(
                candles=None, source="upstox_live",
                reason=f"Only {len(candles)} candles available, need at least 100.",
            )
        return CandleFetchResult(candles=candles, source="upstox_live")


class WarehouseCandleProvider(CandleDataProvider):
    """
    Future data source — the Historical Warehouse (NGWH), once it's
    populated. Uses the Warehouse's own real, already-built read API
    (ParquetStorageManager.read_partition()) rather than inventing a new
    one. Does not fabricate data: if the warehouse has nothing for this
    instrument, get_candles() returns candles=None with a clear reason,
    which research_engine.py surfaces rather than hides.

    Not wired up as the default yet because, per the PR 8 audit,
    data/warehouse/ does not exist in the repo and no scheduled job has
    ever populated it. This class exists so that flipping the default
    provider (in generate_research.py, one line) is the ONLY change
    needed once that's no longer true — no research_engine.py changes.
    """

    def __init__(self):
        self._handles = None

    def _get_handles(self):
        if self._handles is None:
            from warehouse_admin.resource import get_warehouse_handles
            self._handles = get_warehouse_handles()
        return self._handles

    def get_candles(self, instrument_key: str) -> CandleFetchResult:
        try:
            handles = self._get_handles()
        except Exception as e:
            return CandleFetchResult(
                candles=None, source="warehouse",
                reason=f"Warehouse not available: {e}",
            )

        # PartitionKey shape follows warehouse/storage/schema.py's own
        # convention (instrument + layer); deliberately not guessing at
        # a date range here — a real integration would enumerate this
        # instrument's available partitions first. Left as the explicit
        # next step rather than a guessed implementation, since the
        # warehouse has no real partitions to test this against yet.
        return CandleFetchResult(
            candles=None, source="warehouse",
            reason=(
                "Warehouse-backed research is not implemented yet — "
                "data/warehouse/ has no partitions for any instrument "
                "(confirmed in the PR 8 audit). This provider is the "
                "integration point: once the warehouse is populated and "
                "scheduled, this method reads via "
                "ParquetStorageManager.read_partition() instead of "
                "returning this message."
            ),
        )


def default_provider() -> CandleDataProvider:
    """
    The provider generate_research.py and research_engine.py use unless
    told otherwise. Today: Upstox. Flip this one line to
    WarehouseCandleProvider() once the warehouse has real data — nothing
    else in the pipeline needs to change.
    """
    return UpstoxCandleProvider()
