# TRADING-007A.2 — Market Context Intelligence Platform

Schema version: **v8** (migrated from v7 via `MIGRATION_7_8`, purely additive).

## 1. Reconciliation against existing modules — read this first

The approved blueprint listed a "do not create" list (evidence, signal, confidence, market
regime, trading session, holiday, performance tables) and one explicit naming prohibition
(`MarketContextEntity`). Before writing any entity, the existing schema was statically audited
— same discipline as TRADING-005 §0, TRADING-006 §1, and TRADING-007A.1 §1 — to confirm what
already exists and what genuinely doesn't.

| Blueprint concern | Resolution |
|---|---|
| `MarketContextEntity` name collision | **Confirmed pre-existing** — `intelligence.graph.entity.MarketContextEntity` (TRADING-006 Knowledge Graph) is a point-in-time regime/session/volatility snapshot attached to a single observation. Different grain, different owner, from the economic-calendar concept this module needed. **Not reused, not renamed, not touched.** Every table in this module uses `economic_event_*` naming instead, per the approved rule. |
| Market regime tracking | **Reused, not duplicated.** `intelligence.regime.entity.MarketRegimeEntity` / `RegimeObservationEntity` (Module 5) already own this. Nothing here redefines regime state. |
| Trading sessions / holidays | **Reused, not duplicated.** `core.entity.MarketSessionEntity` (Module 1) already owns this. Nothing here redefines session/holiday state. |
| Evidence / confidence / signal / performance concepts | **Reused, not duplicated.** This module produces raw calendar and monitoring data that the Evidence Engine (`historical.evidence`, `intelligence.evidence`), Confidence Modeling (`intelligence.confidence`), Signal Intelligence (`signals`), and Analytics (`analytics`) packages can consume — it does not re-implement any of them. Promoting an economic event to evidence, or feeding a drift measurement into a confidence recalibration, is a caller-side orchestration against those existing repositories, exactly how `NewsRepository` (TRADING-007A.1) documents the same boundary for news-to-evidence promotion. |
| A single polymorphic drift table, not one per subject type | **Reused pattern, one new table.** `DriftMetricEntity` reuses the `subjectType` (typed enum) + `subjectRowId` (logical-only reference) shape already established by `intelligence.confidence.entity.ConfidenceScoreEntity.scoredEntityType`/`scoredEntityRowId` and `intelligence.graph.entity.EntityRelationshipEntity`'s endpoint columns. No `ModelDriftEntity`/`IndicatorDriftEntity`/`StrategyDriftEntity`/`SourceDriftEntity` were created. |
| Economic calendar data, outcome tracking, drift/calibration monitoring | **All new** — nothing in Modules 1–7 stored macro calendar events or tracked model/indicator/strategy/source drift, so all 7 tables below are genuinely new. |

**Net result: 7 new tables**, none of them evidence, signal, confidence, market-regime,
trading-session, holiday, or performance tables — per the implementation brief's explicit
"do not create" list.

## 2. What this platform adds

**7 new tables**, one new top-level package `com.jarvis.tidb.context`:

```
context/
  entity/       EconomicEventEntity, EconomicEventCategoryEntity,
                EconomicEventCategoryLinkEntity, EconomicEventInstrumentLinkEntity,
                EconomicEventOutcomeEntity, DriftMetricEntity, CalibrationMetricEntity
  dao/          one @Dao interface per entity above
  repository/   MarketContextIntelligenceRepository
  repository/impl/  MarketContextIntelligenceRepositoryImpl
```

Two sub-concerns share the package because both are "market context" per the platform's own
name, but they are independent of each other at the schema level (no FK between them):

1. **Economic calendar** (5 tables, `economic_event_*` naming) — `EconomicEventEntity` is the
   calendar entry (FOMC, CPI, GDP, PPI, EIA Storage, OPEC Meeting, Employment Report, ...).
   `EconomicEventCategoryEntity` / `EconomicEventCategoryLinkEntity` give it a flat controlled
   vocabulary (not hierarchical — the approved blueprint didn't ask for a ROOT/SUBCATEGORY
   taxonomy here, unlike `news.entity.NewsCategoryEntity`, so one wasn't introduced).
   `EconomicEventInstrumentLinkEntity` maps an event to whatever it's relevant to — an
   instrument, sector, asset class, or index — via a `scope` enum rather than four separate
   link tables. `EconomicEventOutcomeEntity` stores expected/actual/previous/revision/surprise/
   market-reaction figures, insert-only with a self-referential `revisesOutcomeId` for data
   revisions (CPI/GDP/NFP routinely get revised after initial release).

2. **Drift & calibration monitoring** (2 tables, standalone naming) — `DriftMetricEntity`
   and `CalibrationMetricEntity`, both single polymorphic tables over
   `ContextMonitoringSubjectType` (MODEL / INDICATOR / STRATEGY / SOURCE). These do not carry
   the `economic_event_` prefix because they aren't economic-event-scoped; the naming rule in
   the approved blueprint applies to the calendar tables specifically.

## 3. Data flow

```
Future connectors (Trading Economics, FRED, EIA, MCX, NSE, CME, Fed, RBI, NOAA, IMD, ...)
        │  (not implemented this phase — providerCode/externalEventId are prepared for it)
        ▼
EconomicEventEntity  ──┬──▶ EconomicEventCategoryLinkEntity ──▶ EconomicEventCategoryEntity
                       ├──▶ EconomicEventInstrumentLinkEntity ──▶ InstrumentEntity / ContractEntity
                       └──▶ EconomicEventOutcomeEntity (insert-only, revision chain)

Confidence models, indicator computations, strategies, evidence sources
        │  (measured against, out-of-band — this platform only stores the measurement)
        ▼
DriftMetricEntity / CalibrationMetricEntity  (subjectType + subjectRowId, insert-only)
```

Downstream consumers (Evidence Engine, Learning Platform, future Decision Engine) read
`MarketContextIntelligenceRepository` directly; this module does not reach into theirs.

## 4. Entity relationships (ER summary)

```
economic_events 1──* economic_event_category_links *──1 economic_event_categories
economic_events 1──* economic_event_instrument_links *──0..1 instruments
                                                        *──0..1 contracts   (nullable, front-month narrowing)
economic_events 1──* economic_event_outcomes
economic_event_outcomes 0..1──* economic_event_outcomes   (self-referential revision chain)

drift_metrics (subjectType, subjectRowId)        — logical-only, no FK (resolves against
calibration_metrics (subjectType, subjectRowId)    confidence_models / indicator_definitions /
                                                    a strategy identifier / evidence_sources,
                                                    depending on subjectType)
```

## 5. Migration notes

`MIGRATION_7_8` is purely additive: 7 `CREATE TABLE IF NOT EXISTS` statements plus their
indices, no `ALTER TABLE` on any existing table, no destructive operations — matching the
project's standing "no destructive migrations" invariant already followed by
`MIGRATION_4_5`/`MIGRATION_5_6`/`MIGRATION_6_7`. Every column and index in the migration SQL was
cross-checked against the entity definitions before delivery (see the audit summary in §7) —
same discipline as TRADING-005 §5.

Two Room-adjacent registration points that are easy to miss and were verified:
- `core/Converters.kt` — 6 new `@TypeConverter` pairs for `EconomicEventStatus`,
  `EconomicEventImportance`, `EconomicEventLinkScope`, `ContextMonitoringSubjectType`,
  `DriftSeverity`, `CalibrationMetricType`, all persisted as their String `value` (not ordinal),
  matching the convention every prior module after Module 3 has used.
- `di/TidbModule.kt` — `MarketContextIntelligenceRepository` field, `initialize()` wiring, and
  a public getter, following the exact manual-DI shape already used for `NewsRepository` and
  every other module (this is the TIDB's own singleton DI object, not the app's separate Hilt
  layer).

## 6. What's deliberately out of scope

- **Connector implementations.** `EconomicEventEntity.providerCode` / `externalEventId` are
  logical-only fields sized for Trading Economics, FRED, EIA, MCX, NSE, CME, Federal Reserve,
  RBI, NOAA, and IMD identifiers — no HTTP client, no provider-specific parsing, no scheduling
  logic. Per the brief: "Prepare for future connectors. Do NOT implement them."
- **Drift/calibration computation.** This platform stores measurements; it does not compute
  PSI, KL-divergence, Brier scores, or any other statistic. That's an external/compute concern,
  same boundary `news.entity.SentimentScoreEntity` already draws for sentiment scoring.
- **Automatic recalibration actions.** `CalibrationMetricEntity.triggeredRecalibration` records
  that a threshold was crossed; it does not itself trigger anything — no Decision Engine logic
  lives in this database layer.
- **On-device retention/pruning policy** for economic events, analogous to the tiering
  `news.entity.NewsArticleEntity` documents — deferred to the same future retention pass, not
  re-litigated here. `EconomicEventEntity` carries `SoftDeleteMetadata` in preparation for it.

## 7. Quality audit (performed before delivery)

Static analysis (parsed entities, cross-referenced DAO SQL, resolved imports, checked DI graph
— the established pattern for audits when Gradle can't be run in this sandbox):

- **Duplicate Entity Audit** — 0 duplicate `tableName` values across all 108 tables (101
  pre-existing + 7 new).
- **Registration Audit** — all 101 pre-existing + 7 new entities: declared-in-`@Database` ↔
  defined-in-code parity confirmed both directions (no orphans, nothing declared-but-missing).
- **Duplicate DAO / Repository Audit** — every new `@Dao` interface has exactly one getter on
  `TradingIntelligenceDatabase` and exactly one wiring line in `TidbModule.initialize()`; no
  duplicates found.
- **Relationship / Migration Audit** — every `ForeignKey(entity = ...)` target resolves to a
  real, defined entity class; every one of the 7 new tables' migration `CREATE TABLE` columns
  matches its entity's `@ColumnInfo` fields exactly (including `@Embedded` audit/soft-delete
  expansion) — zero missing, zero extra, checked column-by-column and index-by-index.
- **Converter / Naming Collision Audit** — all 6 new enums have registered `TypeConverter`
  pairs; 0 naming collisions against any existing table, entity, DAO, or repository name in the
  schema (`EconomicEvent*`, `DriftMetric*`, `CalibrationMetric*` did not previously exist
  anywhere in the codebase).
- **Dependency Injection Audit** — `MarketContextIntelligenceRepositoryImpl`'s constructor
  parameters match the DAOs passed from `TidbModule.initialize()` one-to-one by name.

Not verified in this sandbox (no network access to run Gradle): an actual `kspDebugKotlin` /
compile pass. Brace/paren balance was checked on every new and modified file as a syntax
sanity check, but a local Gradle build is still the authoritative verification step before
this ships to GitHub Actions.
