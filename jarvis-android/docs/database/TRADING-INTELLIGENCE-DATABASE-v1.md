# Trading Intelligence Database v1.0 — Architecture Reference

**Status:** FROZEN. This is the official, final schema reference for the JARVIS Trading
Intelligence Database. Future work builds runtime engines and JARVIS intelligence on top of
this schema — it does not redesign it. Only bug fixes and additive migrations from here.

**Database:** `com.jarvis.tidb.database.TradingIntelligenceDatabase` — schema version **4**,
file `jarvis_trading_intelligence.db`.

**Supersedes:** `TidbDatabase` (Module 1, reached internal schema v2), `SignalDatabase`
(Module 2, schema v1), `AnalyticsDatabase` (Module 3, schema v1) — three physically separate
Room databases, now merged into one.

---

## 1. Complete Architecture Overview

```
UI / ViewModels / future Strategy Engine / future AI Inference Engine
                            │
                            ▼
                  Repository Interfaces (Flow<T>, suspend fun)
   core.repository.*   signals.repository.*   analytics.repository.*
                            │
                            ▼
                  Repository Implementations
   core.repository.impl.*  signals.repository.impl.*  analytics.repository.impl.*
                            │
                            ▼
                          DAOs
   core.dao.*          signals.dao.*          analytics.dao.*
                            │
                            ▼
              TradingIntelligenceDatabase (ONE Room database,
                    jarvis_trading_intelligence.db)
```

Module boundaries are now expressed **only** by:

1. **Kotlin package** — `com.jarvis.tidb.core.*` (Market Foundation), `com.jarvis.tidb.signals.*`
   (Signal Intelligence), `com.jarvis.tidb.analytics.*` (Trading Analytics & Learning).
2. **Repository interfaces** — every cross-package read/write goes through a repository
   interface (`InstrumentRepository`, `SignalRepository`, `TradeRepository`, etc.), never a raw
   DAO or table reference from another package.
3. **Documentation** — this file, plus the three per-module docs (`TRADING-001`, `TRADING-002`,
   `TRADING-003`), which remain valid for entity-level detail and are not superseded, only
   consolidated here at the architecture level.

There is exactly **one** physical `.db` file. `TidbModule` (in `com.jarvis.tidb.di`) is the
single DI entry point, replacing the three former per-module DI objects
(`core.di.TidbModule`, `signals.di.SignalModule`, `analytics.di.AnalyticsModule`).

## 2. Full ER Diagram

```
Exchange ──1:N──> MarketSession
   │
   └──1:N──> Instrument ──1:N──> Contract
                  │
                  ├──1:N──> HistoricalCandle
                  ├──1:1──> LiveMarketSnapshot
                  ├──1:N──> MarketEvent
                  │
                  ├──1:N──> Signal (real FK, was logical-only pre-v1.0)
                  │            ├──1:N──> SignalReason
                  │            ├──1:1──> SignalSnapshot
                  │            ├──1:N──> SignalLifecycle
                  │            ├──1:N──> SignalTag
                  │            └──1:N──> SignalNote
                  │
                  ├──1:N──> Trade (real FK on instrumentId; real FK on signalId ──> Signal)
                  │            ├──1:N──> TradeExecution
                  │            ├──1:N──> TradeExit
                  │            ├──1:N──> TradeFees
                  │            └──1:N──> TradeJournal
                  │
                  ├──1:1──> InstrumentPerformance
                  │
                  └──1:N──> PortfolioPosition (real FK, new in v1.0)

Backtest ──1:N──> BacktestConfiguration
   │
   └──1:N──> BacktestRun ──1:N──> BacktestTrade
                  └──1:1──> BacktestResult

Trade ──1:N──> LearningObservation, FailureAnalysis, DecisionRecord, TradingTimelineEvent
                  (via relatedTradeRowId — analytics-internal FK, unchanged from Module 3)

LearningObservation ──evidence for──> LearningInsight ──basis for──> OptimizationSuggestion
LearningEvidenceLink (NEW in v1.0) ──generic polymorphic link──> {Signal | Trade | BacktestRun |
                                          PerformanceMetric | TimelineEvent}
                                     ──supports──> {Observation | Insight | Suggestion |
                                          Pattern | FailureAnalysis}

TradingTimelineEvent (append-only, permanent — every module can append)
DecisionRecord ──1:N──> DecisionExplanation
LessonLearned (references LearningInsight and/or FailureAnalysis)

Portfolio ──1:N──> PortfolioPosition (real FK ──> Instrument, new in v1.0)
   ├──1:N──> PortfolioAllocation
   ├──1:N──> PortfolioRisk
   ├──1:N──> CapitalMovement
   └──1:N──> PortfolioSnapshot (NEW in v1.0 — immutable point-in-time state)
```

**The full "reconstruct everything" chain the consolidation brief asked for:**

```
HistoricalCandle → (feeds) → Signal → Trade → Performance (Strategy/Instrument/Monthly
rollups + Snapshots) → Learning (Observation → Insight → Suggestion, with EvidenceLinks
back to Signal/Trade/BacktestRun/PerformanceMetric/TimelineEvent) → TradingTimelineEvent
(permanent record of every step) → Portfolio (Position/Allocation/Risk/CapitalMovement/
Snapshot)
```

No table in this chain duplicates another's data — every step is a reference (`rowId` /
`instrumentId` / `signalId`), never a copy, except for the two intentionally-denormalized
tables discussed in §5 (`PortfolioSnapshotEntity.positionsJson` and the `*Performance` rollup
tables), both of which exist specifically because they must survive changes to the live tables
they were computed from.

## 3. Module Boundaries

| Package | Owns | Consumes from other packages | How |
|---|---|---|---|
| `core` (Module 1) | Exchange, Instrument, Contract, HistoricalCandle, LiveMarketSnapshot, MarketEvent, MarketSession | nothing | — |
| `signals` (Module 2) | Signal, SignalReason, SignalSnapshot, SignalLifecycle, SignalTag, SignalNote | `core.Instrument` | Real `@ForeignKey` (v1.0) + `InstrumentRepository` validation |
| `analytics` (Module 3) | Trade\*, Backtest\*, Performance\*, Learning\*, Timeline\*, Portfolio\* | `core.Instrument`, `signals.Signal` | Real `@ForeignKey` on `TradeEntity`/`PortfolioPositionEntity` (v1.0) + `InstrumentRepository`/`SignalRepository` validation in `TradeRepositoryImpl` |

No package ever queries another package's DAO or table directly — always through the owning
package's repository interface. This rule didn't change in v1.0; what changed is that Room can
now also enforce referential integrity at the SQLite level wherever a real `@ForeignKey` was
added, on top of (not instead of) the repository-layer validation.

## 4. Repository Dependency Graph

```
TidbModule (com.jarvis.tidb.di) — single init entry point
   │
   ├── core.repository.impl.*        (no dependencies on other packages)
   │
   ├── signals.repository.impl.SignalRepositoryImpl
   │        (no runtime dependency on core — instrumentId is FK-enforced by SQLite now,
   │         but SignalRepositoryImpl itself still only touches signals.dao.*)
   │
   └── analytics.repository.impl.TradeRepositoryImpl
            ├── depends on → signals.repository.SignalRepository   (interface only)
            └── depends on → core.repository.InstrumentRepository  (interface only)

   (all other analytics.repository.impl.* — Backtest/Performance/Learning/Timeline/Portfolio —
    depend only on their own analytics.dao.* and have no cross-package repository dependency)
```

`TradeRepositoryImpl` remains the single cross-package-aware repository implementation in the
whole codebase, exactly as it was when `analytics` was still a separate database — merging the
physical databases did not change *which* repository needs cross-module awareness, only how
strongly that awareness can be backed by the storage layer (real FK vs. logical-only).

## 5. Database Version History

| Version | Physical database | What it represents |
|---|---|---|
| 1 | `TidbDatabase` (legacy) | Module 1 initial build |
| 2 | `TidbDatabase` (legacy) | Module 1 Revision 1 — UUID/audit/soft-delete/external-IDs/data-provenance |
| 1 | `SignalDatabase` (legacy) | Module 2 initial and only version |
| 1 | `AnalyticsDatabase` (legacy) | Module 3 initial and only version |
| **4** | **`TradingIntelligenceDatabase`** | **v1.0 — the unified, frozen schema** |

The unified database's version counter starts at **4**, not 1, purely so that a bare version
number is never ambiguous about which physical schema generation it refers to — versions 1–3
are permanently reserved to mean "one of the three legacy per-module databases." This is a
labeling choice only; it has no effect on Room's migration mechanics, which key off
`(oldVersion, newVersion)` pairs registered in `TidbMigrations.ALL`, currently empty since v1.0
is the schema's first release.

## 6. Migration Strategy

Two distinct mechanisms, because they solve two different problems:

**A. In-place schema evolution (`TidbMigrations.ALL`)** — ordinary Room `Migration(old, new)`
objects for changes made *after* v1.0 ships (e.g. `MIGRATION_4_5` adding a column). This is the
familiar Room pattern already used for the legacy `TidbDatabase`'s `MIGRATION_1_2`, and it's
where all future schema changes belong. **No destructive fallback is ever configured** —
`TradingIntelligenceDatabase.build()` never calls `fallbackToDestructiveMigration()`.

**B. One-time legacy consolidation (`LegacyDatabaseConsolidator`)** — for upgrading installs
that still have the three old `.db` files on disk. A Room `Migration` cannot solve this: it
only ever transforms the single database Room is currently opening, and has no way to reach
into three separate, independently-versioned `.db` files. `LegacyDatabaseConsolidator` uses
SQLite's `ATTACH DATABASE` to open each legacy file under a temporary alias, copies every table
across with `INSERT OR IGNORE INTO ... SELECT ... FROM alias.table` inside one transaction per
source database, then detaches. Tables are copied in FK-safe (parent-before-child) order. Only
after all three sources succeed are the legacy files renamed to a `.migrated` suffix — never
deleted — so the pre-consolidation data is always recoverable. The whole operation is
idempotent and safe to call on every app startup (`runIfNeeded` short-circuits once the unified
file exists), and it must run once, before `TradingIntelligenceDatabase.getInstance()` is first
called.

**Data preserved, nothing destructive**: every row from every legacy table has a 1:1 target
table in the unified schema (see the table lists in `LegacyDatabaseConsolidator`), and the
`INSERT OR IGNORE` pattern means a partially-completed prior attempt never duplicates or
corrupts rows on retry.

## 7. Index Strategy (post-review)

The review across all three modules found no duplicate indexes to remove — each module had
already indexed exactly its own DAOs' query predicates, and no cross-module query existed
before v1.0 to require a new composite index (cross-module reads were always two separate
queries stitched together at the repository layer, e.g. `TradeRepositoryImpl` calling both
`InstrumentRepository` and its own DAO). What v1.0 changes is that the new real `@ForeignKey`
columns (`TradeEntity.signalId`, `TradeEntity.instrumentId`, `PortfolioPositionEntity.instrumentId`)
were **already indexed** in Module 3's original design (`Index(["signalId"])`,
`Index(["instrumentId"])`, etc.), since Module 3 anticipated exactly this kind of lookup even
under logical-FK validation — so no new indexes were required to support the FK upgrade itself;
SQLite's foreign-key enforcement uses the same B-tree index either way.

Index families, by access pattern, across the unified schema:

| Pattern | Where |
|---|---|
| Historical/time-window queries | `historical_candles(instrumentId, timeframe, timestamp)` unique composite; `trades(entryTimestamp, instrumentId)`; `trading_timeline_events(eventType, occurredAt)`; `portfolio_snapshots(portfolioRowId, snapshotType, snapshotAt)` |
| Instrument queries | `instruments(symbol)` unique, `instruments(assetClass)`, every analytics/signals table's `instrumentId` column |
| Strategy queries | `strategy_performance(strategyId)` unique, `trades(strategyId)`, `backtests(strategyId)` |
| Timeline queries | `trading_timeline_events(eventType, occurredAt)`, `(relatedTradeRowId)`, `(relatedInstrumentId)` |
| Portfolio queries | `portfolio_positions(portfolioRowId, instrumentId, status)`, `capital_movements(portfolioRowId, occurredAt)`, `portfolio_snapshots(portfolioRowId, snapshotType, snapshotAt)` |
| Analytics/learning queries | `learning_observations(relatedTradeRowId/relatedInstrumentId/relatedStrategyId/confidence)`, `learning_evidence_links(linkedEntityType, linkedEntityRowId)` and `(sourceType, sourceRowId)` |

## 8. Timeline Integration Strategy

**No timeline engine is implemented** — per the brief, this section documents the mapping so a
future engine can be built without another schema pass.

Every entity capable of appearing in Executive Trading Memory already carries the metadata a
timeline reconstruction needs:

| Source entity | Timeline-relevant fields already present | Maps to `TradingTimelineEventEntity.eventType` |
|---|---|---|
| `SignalEntity` (create / `transitionStatus`) | `generatedAt`, `uuid`, `instrumentId`; `SignalLifecycleEntity` already logs every transition with `changedAt`/`changedBy`/`reason` | `SIGNAL_GENERATED` |
| `TradeEntity` / `TradeExecutionEntity` / `TradeExitEntity` | `entryTimestamp`, `closeTimestamp`, `executedAt`, `exitedAt`, `closeReason` | `TRADE_EXECUTED`, `TRADE_CLOSED`, `STOP_LOSS_HIT`, `TARGET_HIT` |
| `BacktestRunEntity` | `startedAt`, `completedAt`, `status` | `BACKTEST_COMPLETED` |
| `LearningObservationEntity` / `LearningInsightEntity` | `generatedAt`, `generatedBy` | `AI_OBSERVATION`, `AI_INSIGHT` |
| `OptimizationSuggestionEntity` | `generatedAt`, `status`/`reviewedAt` | `OPTIMIZATION_SUGGESTED` |
| `PortfolioAllocationEntity` / `CapitalMovementEntity` | `asOf`, `occurredAt`, `movementType` | `PORTFOLIO_REBALANCED`, `CAPITAL_DEPOSITED`/`CAPITAL_WITHDRAWN` |
| `PortfolioSnapshotEntity` (new) | `snapshotAt`, `snapshotType` | not itself a timeline event type, but every snapshot is a natural anchor point a future engine can use to answer "what did the timeline look like as of this snapshot" |

The pattern a future timeline engine should follow: whichever repository method performs the
write (`SignalRepository.createSignal`, `TradeRepository.recordExit`, etc.) also calls
`TimelineRepository.recordEvent(...)` with a `relatedXRowId` pointing back at the row just
written. `TradeRepositoryImpl` and `SignalRepositoryImpl` already do exactly this for the
lifecycle-audit case (`SignalLifecycleEntity`); generalizing it to also always emit a
`TradingTimelineEventEntity` is additive application logic, not a schema change.

## 9. Performance Considerations

- **Denormalized rollups stay denormalized.** `StrategyPerformanceEntity`,
  `InstrumentPerformanceEntity`, `MonthlyPerformanceEntity`, and now `PortfolioSnapshotEntity`
  intentionally do not get "properly normalized" into pure joins now that everything is one
  database — they exist because dashboards read them far more often than the underlying rows
  change, and merging databases doesn't change that read/write ratio.
- **Real FKs add write-time cost, not read-time cost.** SQLite foreign-key checks are index
  lookups against columns that were already indexed pre-v1.0 (see §7), so the `@ForeignKey`
  upgrade on `TradeEntity`/`PortfolioPositionEntity` adds a bounded constant-time check per
  insert/update, not a scan.
- **One `SQLiteDatabase` connection instead of three.** Merging into one file means one WAL,
  one connection pool, one set of PRAGMAs — this is a net performance win for any query that
  used to require the app layer to stitch together reads from two databases (e.g. a dashboard
  showing trades next to their instruments previously issued two round trips against two
  separate Room instances; that's now a single-database read, even though the repository layer
  still keeps the query itself split across two repository calls to preserve module boundaries).

## 10. Scalability Strategy

Unchanged in substance from Module 1's original growth/partitioning analysis and Module 3's
scalability notes — merging the physical files doesn't change the row-growth profile of any
table, only where they live. What's new at v1.0:

- **Everything can now be backed up/restored atomically** — one `.db` file instead of three
  that would need to be snapshotted in a mutually consistent instant.
- **A future server-side migration (Postgres/DuckDB) now has one schema to port, not three** —
  the cross-module `@ForeignKey`s added in v1.0 are exactly the relationships a relational
  warehouse schema would want anyway, so this consolidation is a net step *toward* that future
  migration, not away from it.
- **`LegacyDatabaseConsolidator`'s ATTACH-based copy pattern generalizes.** The same technique
  (attach, `INSERT OR IGNORE ... SELECT`, detach) is the mechanism a future on-device →
  on-device migration (e.g. app reinstall recovery, or a future per-user data export/import
  feature) can reuse without new tooling.

## 11. Future Cloud Synchronization Strategy

Every table already carries the three ingredients a future sync layer needs, unchanged from
Module 1 Revision 1's original design intent, now uniformly true across all 44 tables:

1. **Global identity** — every table has a `uuid` column (`GlobalId.new()`), stable across
   devices and independent of the local autoincrement `rowId`/`*Id` primary key.
2. **Audit/version metadata** — `AuditMetadata` (`createdAt`, `updatedAt`, `createdBy`,
   `updatedBy`, `version`) on `core`/`analytics` tables (and the column-compatible flat fields
   on `SignalEntity`) gives a sync layer both a last-write-wins timestamp and an optimistic-lock
   version counter for conflict detection.
3. **Soft delete** — `isDeleted`/`deletedAt` means a delete is itself a syncable event (a row
   flip), not a disappearance a remote peer would need to infer from absence.

A future sync engine's shape, consistent with everything above: push/pull batches keyed by
`updatedAt`/`version`, conflict resolution keyed by `version` (or a vector-clock upgrade if
multi-device concurrent writes become common), and deletes propagated as `isDeleted = true`
rows rather than physical deletes. None of this requires a schema change — v1.0 is sync-ready
as shipped.

## 12. What Changed vs. What Didn't (summary for reviewers)

**Changed:**
- Three Room databases → one (`TradingIntelligenceDatabase`).
- Three DI objects → one (`com.jarvis.tidb.di.TidbModule`).
- `TradeEntity.signalId`/`instrumentId` and `PortfolioPositionEntity.instrumentId`: logical-only
  → real `@ForeignKey`.
- New: `PortfolioSnapshotEntity` (+ DAO + repository methods).
- New: `LearningEvidenceLinkEntity` (+ DAO + repository methods).
- New: `LegacyDatabaseConsolidator` (one-time, pre-Room-open data migration path).
- Fixed: Module 3's originally-invented `GlobalId` embeddable (which never matched Module 1's
  actual `object GlobalId { fun new(): String }`) is now correctly the single shared
  implementation across all 44 entities.

**Not changed:**
- Every entity, field, enum, DAO query, and repository method from Modules 1, 2, and 3 —
  preserved exactly (SignalEntity's flat audit columns included, per "do not redesign the
  architecture").
- No destructive migrations anywhere, at any layer.
- No functionality removed.
