"""
warehouse.storage.schema
===========================

Canonical PyArrow schemas for every layer this foundation writes physical
data for (RAW_OHLCV, DERIVED_TIMEFRAMES). Reserved layers (indicators,
market_context, instrument_dna, research_artifacts) get placeholder schema
functions that raise NotImplementedError with a clear message — this keeps
the schema *registry* future-proof (one place to look up "what layer X's
shape will be") without pretending those layers are implemented.

Schema evolution rule: NEVER change an existing field's type or remove a
field. Only append nullable fields. This is what makes `VersionManager`
(metadata/version_manager.py) meaningful — column additions are the only
sanctioned in-place evolution; anything else requires a new schema version
and a migration job (future module).
"""

from __future__ import annotations

import pyarrow as pa

from warehouse.core.constants import SCHEMA_REGISTRY_VERSION, WarehouseLayer
from warehouse.core.exceptions import SchemaMismatchError

# ---------------------------------------------------------------------------
# OHLCV — the base candle schema, shared by RAW_OHLCV and DERIVED_TIMEFRAMES
# ---------------------------------------------------------------------------
OHLCV_SCHEMA = pa.schema(
    [
        pa.field("instrument_id", pa.string(), nullable=False),
        pa.field("timeframe", pa.string(), nullable=False),
        pa.field("timestamp_utc", pa.timestamp("us", tz="UTC"), nullable=False),
        pa.field("open", pa.float64(), nullable=False),
        pa.field("high", pa.float64(), nullable=False),
        pa.field("low", pa.float64(), nullable=False),
        pa.field("close", pa.float64(), nullable=False),
        pa.field("volume", pa.float64(), nullable=False),
        pa.field("open_interest", pa.float64(), nullable=True),  # futures only; null for equities
        # Provenance / lineage — required for point-in-time correctness per
        # the approved architecture's data-integrity discipline.
        pa.field("source", pa.string(), nullable=False),           # e.g. "upstox_v2"
        pa.field("ingested_at_utc", pa.timestamp("us", tz="UTC"), nullable=False),
        pa.field("is_derived", pa.bool_(), nullable=False),         # False for RAW_OHLCV, True for DERIVED_TIMEFRAMES
        pa.field("derived_from_timeframe", pa.string(), nullable=True),  # set only when is_derived=True
        pa.field("schema_version", pa.int32(), nullable=False),
    ]
)

# Sort/partition key columns, in the order Parquet files should be sorted by
# on write. This directly affects DuckDB scan pruning efficiency.
OHLCV_SORT_KEYS: tuple[str, ...] = ("instrument_id", "timeframe", "timestamp_utc")

# Columns that uniquely identify a candle row — used for de-duplication
# during incremental updates (future downloader) and by the validation
# layer (NGSP-003B.1) to detect duplicate/overlapping writes.
OHLCV_PRIMARY_KEY: tuple[str, ...] = ("instrument_id", "timeframe", "timestamp_utc")


def get_schema(layer: WarehouseLayer) -> pa.Schema:
    """
    Return the canonical PyArrow schema for a given warehouse layer.

    Raises NotImplementedError for reserved (not-yet-built) layers so that
    callers get a clear, intentional error rather than a KeyError or silent
    wrong-schema bug.
    """
    if layer in (WarehouseLayer.RAW_OHLCV, WarehouseLayer.DERIVED_TIMEFRAMES):
        return OHLCV_SCHEMA
    if layer == WarehouseLayer.INDICATORS:
        raise NotImplementedError(
            "Indicators layer schema is owned by the future Indicators module (not part of NGWH-001)."
        )
    if layer == WarehouseLayer.MARKET_CONTEXT:
        raise NotImplementedError(
            "Market Context layer schema is owned by the future Market Context module (not part of NGWH-001)."
        )
    if layer == WarehouseLayer.INSTRUMENT_DNA:
        raise NotImplementedError(
            "Instrument DNA layer schema is owned by the future DNA module (not part of NGWH-001)."
        )
    if layer == WarehouseLayer.RESEARCH_ARTIFACTS:
        raise NotImplementedError(
            "Research Artifacts layer schema is owned by future research-integration work (not part of NGWH-001)."
        )
    raise ValueError(f"Unknown warehouse layer: {layer!r}")


def validate_schema_compatible(layer: WarehouseLayer, candidate: pa.Schema) -> None:
    """
    Verify that `candidate` (e.g. a schema read back from an existing
    Parquet file, or produced by a writer) is compatible with the canonical
    schema for `layer`.

    "Compatible" means: every canonical field is present with the same type
    and nullability-or-looser. Extra fields in `candidate` beyond the
    canonical schema are NOT allowed here — additive evolution must go
    through bumping SCHEMA_REGISTRY_VERSION and updating the canonical
    schema itself, so drift can't silently creep in via ad-hoc extra columns.
    """
    canonical = get_schema(layer)
    canonical_fields = {f.name: f for f in canonical}
    candidate_fields = {f.name: f for f in candidate}

    missing = set(canonical_fields) - set(candidate_fields)
    if missing:
        raise SchemaMismatchError(
            f"Candidate schema for layer {layer.value!r} is missing fields: {sorted(missing)}",
            context={"layer": layer.value, "missing_fields": sorted(missing)},
        )

    extra = set(candidate_fields) - set(canonical_fields)
    if extra:
        raise SchemaMismatchError(
            f"Candidate schema for layer {layer.value!r} has unexpected extra fields: {sorted(extra)}. "
            "Schema evolution must go through the canonical schema + SCHEMA_REGISTRY_VERSION bump.",
            context={"layer": layer.value, "extra_fields": sorted(extra)},
        )

    for name, canonical_field in canonical_fields.items():
        candidate_field = candidate_fields[name]
        if not canonical_field.type.equals(candidate_field.type):
            raise SchemaMismatchError(
                f"Field {name!r} type mismatch in layer {layer.value!r}: "
                f"expected {canonical_field.type}, got {candidate_field.type}",
                context={"layer": layer.value, "field": name},
            )
        if canonical_field.nullable is False and candidate_field.nullable is True:
            raise SchemaMismatchError(
                f"Field {name!r} is required (non-nullable) in canonical schema for layer "
                f"{layer.value!r}, but candidate schema marks it nullable.",
                context={"layer": layer.value, "field": name},
            )


def current_schema_version() -> int:
    return SCHEMA_REGISTRY_VERSION
