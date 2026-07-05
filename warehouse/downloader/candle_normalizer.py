"""
warehouse.downloader.candle_normalizer
==========================================

Converts raw Upstox candle rows into a PyArrow table matching
`warehouse.storage.schema.OHLCV_SCHEMA`, with a validation gate in between.

Raw Upstox row format (confirmed against the existing app's
`signal_logic.py` docstring): `[timestamp, open, high, low, close, volume,
oi]`, ISO-8601 timestamp string with timezone offset, newest-first.

Validation performed before a row is accepted (fails closed — a bad row
raises `CandleValidationError` rather than silently being dropped or
silently corrupting the warehouse):
    - OHLC ordering sane: low <= open,close <= high, and low <= high
    - all price fields are positive
    - volume is non-negative
    - timestamp parses to a timezone-aware UTC datetime
"""

from __future__ import annotations

from datetime import datetime, timezone

import pyarrow as pa

from warehouse.core.constants import SCHEMA_REGISTRY_VERSION
from warehouse.core.utils import utc_now
from warehouse.downloader.exceptions import CandleValidationError
from warehouse.storage.schema import OHLCV_SCHEMA

# Index positions in Upstox's raw candle row.
_IDX_TIMESTAMP, _IDX_OPEN, _IDX_HIGH, _IDX_LOW, _IDX_CLOSE, _IDX_VOLUME, _IDX_OI = range(7)


def _parse_timestamp(raw: str) -> datetime:
    try:
        dt = datetime.fromisoformat(raw)
    except (ValueError, TypeError) as exc:
        raise CandleValidationError(f"Unparseable candle timestamp: {raw!r}") from exc
    if dt.tzinfo is None:
        raise CandleValidationError(f"Candle timestamp is not timezone-aware: {raw!r}")
    return dt.astimezone(timezone.utc)


def _validate_row(row: list, *, instrument_key: str) -> None:
    if len(row) < 6:
        raise CandleValidationError(
            f"Malformed candle row (expected >= 6 fields, got {len(row)}): {row!r}",
            context={"instrument_key": instrument_key},
        )
    _, o, h, l, c, v = row[:6]
    if not (o > 0 and h > 0 and l > 0 and c > 0):
        raise CandleValidationError(
            f"Non-positive OHLC value in candle row: {row!r}", context={"instrument_key": instrument_key}
        )
    if not (l <= o <= h and l <= c <= h and l <= h):
        raise CandleValidationError(
            f"OHLC ordering inconsistent (expected low <= open,close <= high): {row!r}",
            context={"instrument_key": instrument_key},
        )
    if v < 0:
        raise CandleValidationError(
            f"Negative volume in candle row: {row!r}", context={"instrument_key": instrument_key}
        )


def normalize_candles(
    instrument_key: str,
    timeframe_value: str,
    upstox_interval: str,
    candles: list[list],
    *,
    source: str = "upstox_v2",
    strict: bool = True,
) -> pa.Table:
    """
    Convert raw Upstox candle rows into an OHLCV_SCHEMA-conformant table.

    Args:
        strict: if True (default), any row failing validation raises
            immediately. If False, invalid rows are skipped and logged
            rather than aborting the whole chunk — use sparingly, only when
            a caller has an explicit reason to tolerate partial data (this
            is NOT the default because silently dropping candles is exactly
            the kind of silent data-quality issue the architecture's
            validation discipline exists to prevent).

    Returns:
        A pa.Table with zero rows (not None) if `candles` is empty — callers
        should check `.num_rows` rather than truthiness.
    """
    from warehouse.core.logging_config import get_logger

    logger = get_logger(__name__)

    ingested_at = utc_now()
    rows_out: dict[str, list] = {f.name: [] for f in OHLCV_SCHEMA}

    for row in candles:
        try:
            _validate_row(row, instrument_key=instrument_key)
        except CandleValidationError:
            if strict:
                raise
            logger.warning(f"Skipping invalid candle row for {instrument_key}: {row!r}")
            continue

        ts = _parse_timestamp(row[_IDX_TIMESTAMP])
        oi = row[_IDX_OI] if len(row) > _IDX_OI else None

        rows_out["instrument_id"].append(instrument_key)
        rows_out["timeframe"].append(timeframe_value)
        rows_out["timestamp_utc"].append(ts)
        rows_out["open"].append(float(row[_IDX_OPEN]))
        rows_out["high"].append(float(row[_IDX_HIGH]))
        rows_out["low"].append(float(row[_IDX_LOW]))
        rows_out["close"].append(float(row[_IDX_CLOSE]))
        rows_out["volume"].append(float(row[_IDX_VOLUME]))
        rows_out["open_interest"].append(float(oi) if oi is not None else None)
        rows_out["source"].append(source)
        rows_out["ingested_at_utc"].append(ingested_at)
        rows_out["is_derived"].append(False)
        rows_out["derived_from_timeframe"].append(None)
        rows_out["schema_version"].append(SCHEMA_REGISTRY_VERSION)

    return pa.table(rows_out, schema=OHLCV_SCHEMA)
