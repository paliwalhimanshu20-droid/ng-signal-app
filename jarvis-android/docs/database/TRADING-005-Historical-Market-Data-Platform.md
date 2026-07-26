# TRADING-005 — Historical Market Data Platform

**Schema version 4 → 5.** Purely additive: 26 new tables across six new packages under
`com.jarvis.tidb.historical`. Nothing in Module 1 (Core Market Foundation), Module 2 (Signal
Intelligence), or Module 3 (Trading Analytics) is altered — `instruments`, `historical_candles`,
etc. keep their exact existing shape and data. The new tables only add foreign keys pointing
*at* them.

---

## 0. Pre-requisite: repository integrity audit

Before this milestone was built, the uploaded repository was audited and found to contain two
complete, mutually-incompatible generations of the `core` and `signals` TIDB packages
(47 duplicate top-level declarations — `RecordStatus`, `Timeframe`, `HistoricalCandleEntity`,
`ExchangeDao`, `SignalEntity`, etc. each defined twice in the same Kotlin package), plus an
orphaned, already-broken legacy per-module database cluster (`TidbDatabase`, `SignalDatabase`,
`AnalyticsDatabase` and their DI modules — none reachable from the live app, two importing
classes that don't exist). 48 files were removed after tracing actual usage against the live
`di/TidbModule.kt` wiring and repository method signatures (not doc comments, which described
an abandoned parallel rewrite in `core`). Full details, the exact keep/delete list, and the
resolution of the previously-known `LegacyDatabaseConsolidator` table-name bug (which lived
entirely in one of the removed files) are in the delivery notes for this milestone. This section
exists so a future reader of the schema history understands why a "v5" migration follows a
codebase that needed cleanup first — the platform below was built only after that baseline was
verified to have zero duplicate declarations and zero unresolved imports.

---

## 1. Design principles

- **Extend, never recreate.** `HistoricalCandleEntity` (multi-exchange, multi-asset-class,
  multi-timeframe, versioned-by-`qualityScore`, provenance via `source`/`sourceId`/
  `importBatchId`/`checksum`, duplicate-proof via the unique `(instrumentId, timeframe,
  timestamp)` index) already covers most of "Historical Candle Storage" §2 of the milestone
  spec. This platform adds only what it doesn't: correction history and gap tracking.
- **Store, don't recompute.** The Indicator Warehouse persists computed values; nothing reads
  a candle range and recomputes an indicator on the fly. Recomputation is an explicit,
  versioned, tracked operation (`IndicatorComputationRunEntity`).
- **Metadata only, no predictions.** Instrument DNA and Evidence Foundation tables store
  descriptive statistics and structured observations — nothing here scores, ranks, or
  recommends. A future decision/strategy engine consumes these tables; it doesn't live here.
- **One physical database.** Following the v1.0 consolidation, every new table gets real Room
  `@ForeignKey`s to `instruments`/`historical_candles`/etc. — no logical-only FKs within this
  platform, since everything lives in `TradingIntelligenceDatabase` now.
- **Asset-class-agnostic.** No column assumes commodities specifically; `CorporateActionEntity`
  is a harmless no-op table for pure futures instruments and becomes relevant the day an
  equity/index instrument is onboarded.

## 2. Module map

| Package | Tables | Purpose |
|---|---|---|
| `historical.ingestion` | `data_providers`, `ingestion_jobs`, `ingestion_job_logs`, `ingestion_checkpoints` | Multi-source provider registry, job lifecycle with retry/recovery, progress tracking, incremental-update cursors |
| `historical.candle` | `candle_versions`, `candle_gaps` | Correction audit trail for existing candles; detected-gap bookkeeping through to backfill |
| `historical.quality` | `candle_quality_reports`, `quality_issues`, `corporate_actions` | Scored validation runs, individual findings, corporate-action tracking/adjustment status |
| `historical.indicator` | `indicator_definitions`, `indicator_values`, `indicator_computation_runs` | Versioned indicator configs, stored (not recomputed) output, computation/recomputation tracking |
| `historical.dna` | 8 facet tables (see §4) | Descriptive statistical profile per instrument — volatility, session behavior, trend persistence, liquidity, gaps, seasonality, indicator behavior, general statistics |
| `historical.evidence` | `market_observations`, `evidence_records`, `pattern_occurrences`, `supporting_indicators`, `confidence_components`, `source_references` | Structured record of what was observed and why it might matter — no decisioning |

## 3. Data flow

```
DataProviderEntity (registry)
        │
        ▼
IngestionJobEntity ──creates──▶ IngestionJobLogEntity (retry/recovery trail)
        │                              ▲
        │ advances                     │ on failure
        ▼                              │
IngestionCheckpointEntity ─────────────┘
        │
        ▼ (writes via existing Module 1 repository)
HistoricalCandleEntity (Module 1 — unchanged)
        │
        ├──correction──▶ CandleVersionEntity (prior state preserved)
        │
        ├──gap detected──▶ CandleGapEntity ──dispatches──▶ (new IngestionJobEntity, jobType=GAP_FILL)
        │
        ├──validated by──▶ CandleQualityReportEntity ──contains──▶ QualityIssueEntity[]
        │
        └──consumed by──▶ IndicatorComputationRunEntity ──writes──▶ IndicatorValueEntity[]
                                                                          │
                    ┌─────────────────────────────────────────────────────┘
                    ▼
    Instrument DNA facets (VolatilityProfile, SessionBehavior, TrendPersistence,
    Liquidity, GapBehavior, SeasonalTendency, IndicatorBehavior, StatisticalCharacteristics)
                    │
                    ▼
    MarketObservationEntity ──built into──▶ EvidenceRecordEntity
                                                  ├──▶ SupportingIndicatorEntity[] (→ IndicatorValueEntity)
                                                  ├──▶ ConfidenceComponentEntity[]
                                                  └──▶ SourceReferenceEntity[]

    PatternOccurrenceEntity — detected independently, referenced by SourceReferenceEntity
    when a pattern match is cited as evidence.
```

## 4. Entity relationships (ER summary)

```
instruments (Module 1) ─┬─▶ ingestion_jobs ──▶ ingestion_job_logs
                         ├─▶ ingestion_checkpoints ◀── data_providers
                         ├─▶ candle_gaps
                         ├─▶ candle_quality_reports ──▶ quality_issues
                         ├─▶ corporate_actions
                         ├─▶ indicator_values ◀── indicator_definitions ──▶ indicator_computation_runs
                         ├─▶ dna_volatility_profiles
                         ├─▶ dna_session_behavior_profiles
                         ├─▶ dna_trend_persistence_profiles
                         ├─▶ dna_liquidity_profiles
                         ├─▶ dna_gap_behavior_profiles
                         ├─▶ dna_seasonal_tendencies
                         ├─▶ dna_indicator_behavior_profiles ◀── indicator_definitions
                         ├─▶ dna_statistical_characteristics
                         ├─▶ market_observations
                         ├─▶ evidence_records ◀── market_observations (nullable FK)
                         │        ├──▶ supporting_indicators ◀── indicator_values
                         │        ├──▶ confidence_components
                         │        └──▶ source_references
                         └─▶ pattern_occurrences

historical_candles (Module 1) ─▶ candle_versions
```

## 5. Migration notes

- `MIGRATION_4_5` (`database/migration/HistoricalPlatformMigration.kt`) is purely additive:
  every statement is `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`, so a
  partially-applied retry is safe, matching the project's no-destructive-migrations invariant.
- Registered in `TidbMigrations.ALL`; no change to the legacy-consolidation path
  (`LegacyDatabaseConsolidator` runs before `TradingIntelligenceDatabase` opens, independent of
  in-place migrations).
- Every column, index, and foreign key in the migration SQL was cross-checked programmatically
  against the corresponding Kotlin entity definitions (26/26 tables match exactly on both
  column set and index count) before delivery.
- One naming collision was caught and fixed during this cross-check:
  `IndicatorDefinitionEntity` originally declared its own `version` column, which collided with
  the embedded `AuditMetadata.version` optimistic-lock column. Renamed to `definitionVersion`.

## 6. What's deliberately out of scope

- No decision/scoring/recommendation engine (Evidence Foundation §6 of the spec is explicit
  about this).
- No AI-generated predictions in Instrument DNA (§5 of the spec).
- No UI/ViewModel layer for any of this — repositories only, per the existing module pattern.
- No corporate-action back-adjustment *algorithm* — `CorporateActionEntity.applied`/`appliedAt`
  track whether adjustment happened; the adjustment computation itself is future work.
