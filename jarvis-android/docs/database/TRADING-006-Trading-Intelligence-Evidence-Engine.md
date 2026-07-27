# TRADING-006 — Module 5: Trading Intelligence & Evidence Engine

Schema version: **v6** (migrated from v5 via `MIGRATION_5_6`, purely additive).

## 1. Reconciliation against Modules 3 & 4 — read this first

TRADING-006's brief was written as if the Evidence Foundation didn't exist yet. It does — Module
4 (TRADING-005, schema v5) already built it, under `com.jarvis.tidb.historical.evidence`. Before
writing any Module 5 code, the existing schema was statically audited (parsed entities,
cross-referenced DAO SQL, resolved the DI graph) to avoid duplicating what's already there. The
outcome:

| TRADING-006 asked for | Resolution |
|---|---|
| `EvidenceEntity` | **Reused.** This is `historical.evidence.entity.EvidenceRecordEntity`. Not redefined. |
| `EvidenceSourceEntity` | **New** (`intelligence.evidence`). Complements, doesn't replace, `SourceReferenceEntity` — that table is a per-evidence-row citation; this one is a reusable named-source catalog with a trust weight. |
| `EvidenceCategoryEntity` | **New.** Nothing like it existed. |
| `EvidenceLinkEntity` | **New.** Generalizes `SupportingIndicatorEntity` (which only ever links evidence to an indicator reading) to link evidence to *any* row in the system, mirroring the polymorphic pattern already proven by `analytics.entity.LearningEvidenceLinkEntity`. |
| `EvidenceOutcomeEntity` | **New.** Generalizes `PatternOccurrenceEntity.outcome` (one mutable field) to any evidence record, with multiple evaluation checkpoints over time. |
| `PatternEntity` | **New** (`intelligence.pattern`) — a master catalog/definition table. `PatternOccurrenceEntity` (Module 4) only ever recorded free-text pattern *instances*; there was no definitions table. |
| `PatternOccurrenceEntity` | **Reused, extended.** Gained one additive nullable column, `patternId`, as a logical-only backlink to the new `patterns` table. `patternName` (existing) is untouched. |
| `ConfidenceModelEntity` / `ConfidenceScoreEntity` | **New** (`intelligence.confidence`). `ConfidenceComponentEntity` (Module 4) is a per-evidence-row breakdown and is untouched; these are a reusable scoring-methodology definition and a final composed score attachable to any scoreable entity. |
| `MarketRegimeEntity` / `RegimeObservationEntity` | **New** (`intelligence.regime`). Nothing previously modeled regime *state* — `analytics.entity.InsightCategory.MARKET_REGIME` is only a classification tag on a learning insight. |
| `HypothesisEntity` → `ExperimentEntity` → `ExperimentRunEntity` → `ExperimentResultEntity` | **New** (`intelligence.research`). Entire chain didn't exist. |
| `LearningInsightEntity` | **NOT redefined.** This exact class/table already exists at `analytics.entity.LearningInsightEntity` (`learning_insights`, Module 3), append-only by established convention. Redefining it here would either collide (same class name, second table) or fork "what an insight is" across two modules. Instead, `ExperimentResultEntity.producedInsightRowId` is a logical-only reference into the existing table: when a result is significant enough to promote, the caller writes through the existing `analytics.repository.LearningRepository` and records the resulting `rowId` back here. |
| `EntityRelationshipEntity`, `MarketContextEntity`, `CausalObservationEntity`, `CorrelationEntity` | **New** (`intelligence.graph`). No generic graph layer existed. |

## 2. What Module 5 actually adds

**17 new tables**, one new top-level package `com.jarvis.tidb.intelligence` with six
sub-packages, and **one additive column**:

```
intelligence/
  evidence/     EvidenceCategoryEntity, EvidenceSourceEntity, EvidenceLinkEntity, EvidenceOutcomeEntity
  pattern/      PatternEntity                              (+ pattern_occurrences.patternId, additive)
  regime/       MarketRegimeEntity, RegimeObservationEntity
  confidence/   ConfidenceModelEntity, ConfidenceScoreEntity
  research/     HypothesisEntity, ExperimentEntity, ExperimentRunEntity, ExperimentResultEntity
  graph/        EntityRelationshipEntity, MarketContextEntity, CausalObservationEntity, CorrelationEntity
```

Each sub-package follows the existing `historical.dna` / `historical.evidence` file layout
exactly: `entity/XEntities.kt`, `dao/XDaos.kt`, `repository/XRepository(ies).kt`,
`repository/impl/XRepositoryImpl.kt`.

## 3. Design decisions

- **DI stays manual, not Hilt.** The generic brief said "use Hilt." The actual TIDB layer
  (`com.jarvis.tidb.di.TidbModule`) is a hand-rolled singleton object with `@Volatile` fields and
  an `initialize(context)` entry point — Hilt is used elsewhere in `com.jarvis.os.app`, but never
  for TIDB repositories. Module 5 follows the real convention: six new repositories wired into
  the same `TidbModule.initialize()` call, with matching getter functions.
- **Polymorphic references, not Room `@ForeignKey`s, for cross-cutting links.** Every
  "any entity in the system" reference (`EvidenceLinkEntity.linkedEntityType/RowId`,
  `ConfidenceScoreEntity.scoredEntityType/RowId`, all four `GraphEntityType`-keyed columns) uses
  a `type + rowId` pair, exactly matching the precedent set by
  `analytics.entity.LearningEvidenceLinkEntity` and `historical.evidence.entity.SourceReferenceEntity`.
  SQLite has no polymorphic FK; referential integrity for the polymorphic side is a repository-layer
  responsibility, not a database-layer one, by established project convention.
- **`patternId` is additive, not a redesign.** Rather than editing `PatternOccurrenceEntity`'s
  existing `@Entity`/DAO (Module 4, frozen), the new nullable column is added via
  `ALTER TABLE ... ADD COLUMN` in `MIGRATION_5_6`, and `PatternDao.backfillOccurrenceLinks()`
  exists to link *existing* occurrence rows to a newly-defined pattern by matching
  `patternName == patternKey`.
- **No `SoftDeleteMetadata`.** Consistent with `historical.evidence`'s own entities (append-only,
  audit-only, no soft delete), Module 5's entities embed `AuditMetadata` only — nothing here
  needed a delete lifecycle at write time.
- **Enum persistence** follows the project's two established conventions exactly: enums with a
  `.value` field (all 17 new Module 5 enums) get an explicit `@TypeConverter` pair in
  `core/Converters.kt`; nothing here relies on Room's implicit ordinal/name handling, to keep the
  convention consistent with every other v5/v6 table.

## 4. Migration

`MIGRATION_5_6` (`database/migration/IntelligenceEvidenceEngineMigration.kt`) is purely additive:
17 `CREATE TABLE IF NOT EXISTS` blocks with their indices, plus one guarded
`ALTER TABLE pattern_occurrences ADD COLUMN patternId INTEGER`. No existing table's shape changes
otherwise. Registered in `TidbMigrations.ALL = arrayOf(MIGRATION_4_5, MIGRATION_5_6)`.

## 5. Known follow-ups (not yet implemented — flagged, not silently dropped)

- No decision/scoring *engine* exists anywhere in this module by design — every entity here is
  pure storage, matching the "no decisioning" principle already stated in Module 4's own
  `EvidenceEntities.kt` header. A future Strategy/Decision module reads these tables; it does not
  extend them.
- `GraphRepository` and `ConfidenceRepository` do not currently expose a bulk-backfill path
  analogous to `PatternRepository.backfillOccurrenceLinks` — not needed yet since both are brand
  new tables with no pre-existing free-text data to reconcile.
