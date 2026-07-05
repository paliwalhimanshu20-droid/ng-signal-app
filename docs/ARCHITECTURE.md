# NGWH-001 Architecture Documentation

This document maps the approved NGSP-003A.3 Architecture (v1.0 + Addendum
v1.1, both frozen) to what is physically implemented in this package. It
does not restate the full architecture — it records the specific
implementation decisions made while building foundation code against that
architecture, and flags where a future module still needs to make a
decision.

## 1. Storage technology (v1.0 §9)

**Decision (approved):** Parquet + DuckDB for the warehouse, existing
SQLite unchanged for Research & Learning DB and Instrument Master.

**Implementation:** `storage/parquet_manager.py` + `storage/duckdb_manager.py`.
DuckDB is used two ways — as a zero-copy analytical query engine over the
Parquet lake (`DuckDBManager.query_lake()`, via `read_parquet(..., hive_partitioning=1)`),
and as the engine for the warehouse's own small operational metadata
database (`warehouse_metadata.duckdb`, catalog/version/checkpoint/job
tables). These are separate connections so heavy ad-hoc analytical queries
can never contend with checkpoint/job read-modify-write operations.

## 2. Partitioning strategy

**Decision:** Hive-style partitioning by `instrument_id` / `timeframe` /
`year` (`/month` for intraday timeframes), matching the granularity table
in `core/constants.py::DEFAULT_PARTITION_GRANULARITY`. This keeps
individual Parquet files in a sane size range at 100-instrument, 10-year
scale (intraday timeframes generate far more rows/day than daily+), while
remaining a standard layout any Parquet-aware tool can read via partition
pruning — no bespoke reader required.

## 3. Schema versioning & evolution discipline

**Decision:** One canonical PyArrow schema per layer
(`storage/schema.py::OHLCV_SCHEMA` today), with `validate_schema_compatible()`
enforced on every write and read. Evolution is additive-only: existing
fields never change type or nullability; new fields require bumping
`SCHEMA_REGISTRY_VERSION` and updating the canonical schema, tracked in the
append-only ledger (`metadata/version_manager.py`). This is what makes a
future migration job well-defined — it can look up exactly which schema
version any given partition was written under.

## 4. Resume-after-interruption (v1.0 §2, scalability requirements)

**Decision:** `metadata/checkpoint_manager.py` defines and persists the
checkpoint *contract* (scope, job_id, key_path, arbitrary JSON payload,
is_complete flag) but does not implement any specific job's checkpointing
logic. The future downloader is expected to call `save_checkpoint()` at
its own cadence (`CheckpointConfig.autosave_interval_seconds` as the
suggested default) and, on startup, call `list_incomplete(job_id)` to
resume exactly where it left off — a crash mid-backfill of 100 instruments
should lose at most one partition's worth of progress, not the whole job.

## 5. Soft-reference discipline (no cross-database foreign keys)

**Decision:** `registry/instrument_registry.py` is the *only* module that
opens the Instrument Master SQLite DB, and it opens it strictly read-only
(SQLite URI `mode=ro`). No table in the warehouse's own metadata DB
declares a foreign key against an external database — `instrument_id` is
stored as a plain string everywhere. This matches the existing codebase's
established decoupling discipline (Research & Learning DB already treats
instrument identity the same way) and means the Instrument Master being
temporarily unavailable never blocks warehouse bootstrap or health
(`bootstrap/health_checker.py` reports `UNKNOWN`, not `UNHEALTHY`, when
that dependency is absent).

## 6. Six-layer mapping and reserved namespace

Per v1.0's six-layer storage architecture, this foundation activates
physical storage for Layers 1–2 (Raw OHLCV, Derived Timeframes) and
reserves directory + schema namespace for Layers 3–6 (Indicators, Market
Context, Instrument DNA, Research Artifacts) without implementing them.
`core/constants.py::FOUNDATION_ACTIVE_LAYERS` /
`FOUNDATION_RESERVED_LAYERS` make this split explicit and machine-checkable
— `WarehouseBootstrap` creates directories for all six layers (so no future
module needs first-run directory-creation logic), but
`storage/schema.py::get_schema()` raises `NotImplementedError` for reserved
layers rather than guessing at a schema shape ahead of that module's own
design work.

Addendum v1.1's optional Layer 7 (Knowledge Graph) is intentionally **not**
represented anywhere in this foundation, per the addendum's own framing of
it as a separable, skippable, soft-referenced layer that should be added
only when that specific module is built — adding a placeholder for it now
would be exactly the kind of premature architecture-guessing the brief
warned against.

## 7. What this foundation deliberately defers to future modules

- **Point-in-time correctness / look-ahead bias prevention** for Market
  Context and Instrument DNA (v1.0 §6, Addendum §6) is a concern for those
  modules' own read patterns against this warehouse — this foundation
  supplies `ingested_at_utc` provenance on every row precisely so that
  future point-in-time queries are possible, but does not itself implement
  any point-in-time query logic.
- **Continuous futures / roll methodology** for MCX Natural Gas — the
  `OHLCV_SCHEMA` carries `open_interest` (nullable, futures-only) and plain
  `instrument_id`/`timeframe` identity; roll-adjustment logic belongs to
  the future downloader or a dedicated continuous-series module, not to
  storage.
- **Parallel download orchestration** — `core/utils.py::chunked()` and
  `ScaleConfig.max_parallel_jobs` exist as building blocks; no scheduler or
  worker pool is implemented here.

## 8. Compatibility with existing NGSP modules

| Existing module | Relationship |
|---|---|
| Instrument Master (NGSP-003A.1) | Read-only soft reference via `InstrumentRegistry`; schema assumptions isolated to one file |
| Research & Learning DB (NGSP-003A.2) | Untouched; no code path in this package writes to it |
| Validation Center (NGSP-003B.1) | Same design language (health score excluding skipped categories, per-category status) deliberately reused in `WarehouseHealthChecker` so it can become a new validator category later without a redesign |
| Signal Engine / Market Intelligence Engine (NGSP-001/002) | No direct dependency; will become *consumers* of this warehouse once a downloader and query layer exist on top of it |
